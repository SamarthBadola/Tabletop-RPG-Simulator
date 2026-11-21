#include "pathfinder/api.hpp"

#include <cstddef>
#include <cstdint>
#include <initializer_list>
#include <limits>
#include <set>
#include <utility>
#include <vector>

#include <nlohmann/json.hpp>

namespace pf {
namespace {

using json = nlohmann::json;
using ordered_json = nlohmann::ordered_json;  // keeps keys in the order we add them

constexpr int kMaxNesting = 8;             // spec section 5.6
constexpr std::size_t kMaxNoStop = 10000;  // spec section 7
constexpr int kMaxBudget = 10000000;       // spec section 5.2

ApiError invalid_json(std::string detail, std::string pointer = "") {
    return ApiError{400, "INVALID_JSON", std::move(detail), std::move(pointer)};
}

ApiError invalid_request(std::string detail, std::string pointer = "") {
    return ApiError{422, "INVALID_REQUEST", std::move(detail), std::move(pointer)};
}

// Reads the text once, without building anything, and refuses what spec 5.6 lists as
// INVALID_JSON beyond plain syntax errors: duplicate keys, nesting deeper than 8,
// and numbers that are not a whole int32.
class StrictChecker : public json::json_sax_t {
public:
    std::string problem;  // empty while everything is fine

    bool null() override { return true; }
    bool boolean(bool) override { return true; }
    bool number_integer(json::number_integer_t value) override {
        if (value < std::numeric_limits<std::int32_t>::min() ||
            value > std::numeric_limits<std::int32_t>::max()) {
            return fail("a number is outside the int32 range");
        }
        return true;
    }
    bool number_unsigned(json::number_unsigned_t value) override {
        if (value > static_cast<json::number_unsigned_t>(std::numeric_limits<std::int32_t>::max())) {
            return fail("a number is outside the int32 range");
        }
        return true;
    }
    bool number_float(json::number_float_t, const json::string_t&) override {
        return fail("a number is not a whole number");
    }
    bool string(json::string_t&) override { return true; }
    bool binary(json::binary_t&) override { return true; }
    bool start_object(std::size_t) override {
        if (!enter()) return false;
        keys_.emplace_back();
        return true;
    }
    bool key(json::string_t& name) override {
        if (!keys_.back().insert(name).second) return fail("an object has a duplicate key");
        return true;
    }
    bool end_object() override {
        keys_.pop_back();
        --depth_;
        return true;
    }
    bool start_array(std::size_t) override { return enter(); }
    bool end_array() override {
        --depth_;
        return true;
    }
    bool parse_error(std::size_t, const std::string&, const json::exception&) override {
        return fail("the body is not valid JSON");
    }

private:
    bool enter() {
        ++depth_;
        if (depth_ > kMaxNesting) return fail("nesting is deeper than 8");
        return true;
    }
    bool fail(const char* what) {
        if (problem.empty()) problem = what;
        return false;
    }

    int depth_ = 0;
    std::vector<std::set<std::string>> keys_;  // the keys seen so far in each open object
};

bool known_field(const std::string& key, bool with_goal) {
    static const char* const names[] = {"geometry", "metric", "corners", "parity", "width",
                                        "height",   "cells",  "noStop",  "start",  "budget"};
    for (const char* name : names) {
        if (key == name) return true;
    }
    return with_goal && key == "goal";
}

bool is_int_pair(const json& value) {
    return value.is_array() && value.size() == 2 && value[0].is_number_integer() &&
           value[1].is_number_integer();
}

// Fields that are present must have the right JSON type. Spec 5.6: "wrong types" is INVALID_JSON.
std::optional<ApiError> check_types(const json& doc) {
    const auto wrong = [](const char* field) {
        return invalid_json(std::string(field) + " has the wrong type", std::string("/") + field);
    };

    for (const char* field : {"geometry", "metric", "corners"}) {
        if (doc.contains(field) && !doc[field].is_string()) return wrong(field);
    }
    for (const char* field : {"parity", "width", "height", "budget"}) {
        if (doc.contains(field) && !doc[field].is_number_integer()) return wrong(field);
    }
    if (doc.contains("cells")) {
        if (!doc["cells"].is_array()) return wrong("cells");
        for (const json& row : doc["cells"]) {
            if (!row.is_string()) return wrong("cells");
        }
    }
    if (doc.contains("noStop")) {
        if (!doc["noStop"].is_array()) return wrong("noStop");
        for (const json& cell : doc["noStop"]) {
            if (!is_int_pair(cell)) return wrong("noStop");
        }
    }
    for (const char* field : {"start", "goal"}) {
        if (doc.contains(field) && !is_int_pair(doc[field])) return wrong(field);
    }
    return std::nullopt;
}

std::optional<ApiError> parse_request(const std::string& body, bool with_goal, Query& query) {
    if (body.size() > kMaxBodyBytes) {
        return ApiError{413, "BODY_TOO_LARGE", "the body is larger than 1 MB", ""};
    }

    StrictChecker checker;
    if (!json::sax_parse(body, &checker)) {
        return invalid_json(checker.problem.empty() ? "the body is not valid JSON" : checker.problem);
    }
    const json doc = json::parse(body, nullptr, false);
    if (doc.is_discarded()) return invalid_json("the body is not valid JSON");
    if (!doc.is_object()) return invalid_json("the body must be a JSON object");

    // 1. Unknown fields. The name is not echoed back, so no pointer.
    for (auto it = doc.begin(); it != doc.end(); ++it) {
        if (!known_field(it.key(), with_goal)) return invalid_json("the body has an unknown field");
    }

    // 2. Types.
    if (auto error = check_types(doc)) return error;

    // 3. Required fields.
    for (const char* field : {"width", "height", "cells", "start", "budget"}) {
        if (!doc.contains(field)) {
            return invalid_request(std::string(field) + " is required", std::string("/") + field);
        }
    }
    if (with_goal && !doc.contains("goal")) return invalid_request("goal is required", "/goal");

    // 4. Rules, always checked in this order.
    if (doc.value("geometry", std::string("square")) != "square") {
        return invalid_request("geometry must be square", "/geometry");
    }

    const std::string metric_name = doc.value("metric", std::string("equal_cost"));
    Metric metric = Metric::EqualCost;
    if (metric_name == "true_diagonal") {
        metric = Metric::TrueDiagonal;
    } else if (metric_name == "alternating") {
        metric = Metric::Alternating;
    } else if (metric_name != "equal_cost") {
        return invalid_request("metric is not supported", "/metric");
    }

    const std::string corners_name = doc.value("corners", std::string("forbid_if_blocked"));
    Corners corners = Corners::ForbidIfBlocked;
    if (corners_name == "allow") {
        corners = Corners::Allow;
    } else if (corners_name != "forbid_if_blocked") {
        return invalid_request("corners is not supported", "/corners");
    }

    const int parity = doc.value("parity", 0);
    if (parity != 0 && parity != 1) return invalid_request("parity must be 0 or 1", "/parity");

    const int width = doc["width"].get<int>();
    const int height = doc["height"].get<int>();
    if (width < 1 || width > kMaxSide) return invalid_request("width must be 1 to 200", "/width");
    if (height < 1 || height > kMaxSide) return invalid_request("height must be 1 to 200", "/height");

    const json& cells = doc["cells"];
    if (cells.size() != static_cast<std::size_t>(height)) {
        return invalid_request("cells must have exactly height rows", "/cells");
    }
    std::vector<std::string> rows;
    rows.reserve(cells.size());
    for (std::size_t i = 0; i < cells.size(); ++i) {
        const std::string row = cells[i].get<std::string>();
        const std::string pointer = "/cells/" + std::to_string(i);
        if (row.size() != static_cast<std::size_t>(width)) {
            return invalid_request("a row of cells has the wrong length", pointer);
        }
        for (const char c : row) {
            if (c < '0' || c > '9') {
                return invalid_request("a row of cells has a character that is not 0 to 9", pointer);
            }
        }
        rows.push_back(row);
    }
    std::string grid_error;
    std::optional<Grid> grid = parse_grid(rows, grid_error);
    if (!grid) return invalid_request(grid_error, "/cells");

    std::vector<Cell> no_stop;
    if (doc.contains("noStop")) {
        const json& list = doc["noStop"];
        if (list.size() > kMaxNoStop) return invalid_request("noStop has more than 10000 entries", "/noStop");
        for (std::size_t i = 0; i < list.size(); ++i) {
            const Cell cell{list[i][0].get<int>(), list[i][1].get<int>()};
            if (!grid->inside(cell.x, cell.y)) {
                return invalid_request("a noStop cell is outside the grid", "/noStop/" + std::to_string(i));
            }
            no_stop.push_back(cell);
        }
    }

    const Cell start{doc["start"][0].get<int>(), doc["start"][1].get<int>()};
    if (!grid->inside(start.x, start.y)) return invalid_request("start is outside the grid", "/start");

    Cell goal;
    if (with_goal) {
        goal = Cell{doc["goal"][0].get<int>(), doc["goal"][1].get<int>()};
        if (!grid->inside(goal.x, goal.y)) return invalid_request("goal is outside the grid", "/goal");
    }

    const int budget = doc["budget"].get<int>();
    if (budget < 0 || budget > kMaxBudget) return invalid_request("budget must be 0 to 10000000", "/budget");

    query.grid = std::move(*grid);
    query.no_stop = std::move(no_stop);
    query.metric = metric;
    query.corners = corners;
    query.parity = parity;
    query.start = start;
    query.goal = goal;
    query.budget = budget;
    return std::nullopt;
}


const char* title_for(const std::string& code) {
    if (code == "INVALID_JSON") return "Invalid JSON";
    if (code == "UNAUTHORIZED") return "Unauthorized";
    if (code == "NOT_FOUND") return "Not found";
    if (code == "METHOD_NOT_ALLOWED") return "Method not allowed";
    if (code == "BODY_TOO_LARGE") return "Body too large";
    if (code == "UNSUPPORTED_MEDIA_TYPE") return "Unsupported media type";
    if (code == "INVALID_REQUEST") return "Invalid request";
    if (code == "BUSY") return "Busy";
    if (code == "INTERNAL") return "Internal error";
    return "Error";
}

}  // namespace

const char* reason_name(Reason reason) {
    switch (reason) {
        case Reason::NoPathWithinBudget: return "NO_PATH_WITHIN_BUDGET";
        case Reason::GoalBlocked: return "GOAL_BLOCKED";
        case Reason::GoalNoStop: return "GOAL_NO_STOP";
        case Reason::ExpansionCap: return "EXPANSION_CAP";
        case Reason::None: break;
    }
    return "NO_PATH_WITHIN_BUDGET";
}


std::optional<ApiError> parse_path_request(const std::string& body, Query& query) {
    return parse_request(body, true, query);
}

std::optional<ApiError> parse_reach_request(const std::string& body, Query& query) {
    return parse_request(body, false, query);
}

std::string path_response_json(const PathResult& result) {
    ordered_json out;
    out["found"] = result.found;
    if (result.found) {
        ordered_json path = ordered_json::array();
        for (const Cell& cell : result.path) path.push_back(ordered_json::array({cell.x, cell.y}));
        out["path"] = path;
        out["cost"] = result.cost;
    } else {
        out["reason"] = reason_name(result.reason);
    }
    out["expansions"] = result.expansions;
    return out.dump();
}

std::string reach_response_json(const Grid& grid, const ReachResult& result) {
    ordered_json out;
    out["width"] = grid.width;
    out["height"] = grid.height;
    out["costs"] = result.costs;

    ordered_json stop = ordered_json::array();
    for (int y = 0; y < grid.height; ++y) {
        std::string row;
        for (int x = 0; x < grid.width; ++x) {
            row.push_back(result.stop[static_cast<std::size_t>(grid.index(x, y))] ? '1' : '0');
        }
        stop.push_back(row);
    }
    out["stop"] = stop;
    return out.dump();
}

std::string error_json(const ApiError& error) {
    ordered_json out;
    out["type"] = "about:blank";
    out["title"] = title_for(error.code);
    out["status"] = error.status;
    out["code"] = error.code;
    out["detail"] = error.detail;
    if (!error.pointer.empty()) out["pointer"] = error.pointer;
    return out.dump();
}

}  // namespace pf
