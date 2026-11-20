#include <cstdint>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

#include "pathfinder/api.hpp"

using namespace pf;

// A tiny test helper: print the failing line and keep going.
static int failures = 0;
#define CHECK(...) \
    do { \
        if (!(__VA_ARGS__)) { \
            std::cerr << __FILE__ << ":" << __LINE__ << "  CHECK failed: " #__VA_ARGS__ "\n"; \
            ++failures; \
        } \
    } while (0)

// A small valid request that most error tests change one detail of.
static const std::string kBase =
    R"({"width":2,"height":1,"cells":["11"],"start":[0,0],"goal":[1,0],"budget":100})";

// Replaces the first occurrence of `from` in the base request.
static std::string with(const std::string& from, const std::string& to) {
    std::string body = kBase;
    const std::size_t at = body.find(from);
    if (at == std::string::npos) {
        std::cerr << "test setup error: '" << from << "' not found\n";
        std::exit(2);
    }
    return body.replace(at, from.size(), to);
}

static void expect_error(const std::string& body, int status, const std::string& code,
                         const std::string& pointer = "", bool reach = false) {
    Query q;
    const auto error = reach ? parse_reach_request(body, q) : parse_path_request(body, q);
    const bool ok = error.has_value() && error->status == status && error->code == code &&
                    error->pointer == pointer;
    if (!ok) {
        std::cerr << "  expected " << status << " " << code << " " << pointer << " for: "
                  << body.substr(0, 120) << "\n";
        if (error) std::cerr << "  got      " << error->status << " " << error->code << " " << error->pointer << "\n";
        else std::cerr << "  got      no error\n";
        ++failures;
    }
}

// ---- good requests ----------------------------------------------------------

static void test_path_request_from_the_spec() {  // spec 5.3, case C-02
    const std::string body =
        R"({ "geometry": "square", "metric": "equal_cost", "corners": "forbid_if_blocked",
             "width": 5, "height": 2, "cells": ["12111", "11111"], "noStop": [],
             "start": [0, 0], "goal": [4, 0], "budget": 600 })";
    Query q;
    CHECK(!parse_path_request(body, q).has_value());
    CHECK(q.grid.width == 5 && q.grid.height == 2);
    CHECK(q.budget == 600);
    CHECK(q.start == Cell({0, 0}) && q.goal == Cell({4, 0}));

    CHECK(path_response_json(find_path(q)) ==
          R"({"found":true,"path":[[0,0],[1,1],[2,0],[3,0],[4,0]],"cost":400,"expansions":5})");
}

static void test_defaults() {
    Query q;
    CHECK(!parse_path_request(kBase, q).has_value());
    CHECK(q.metric == Metric::EqualCost);
    CHECK(q.corners == Corners::ForbidIfBlocked);
    CHECK(q.parity == 0);
    CHECK(q.no_stop.empty());
}

static void test_every_option() {
    const std::string body =
        R"({"metric":"alternating","corners":"allow","parity":1,"noStop":[[1,0]],
            "width":2,"height":1,"cells":["11"],"start":[0,0],"goal":[1,0],"budget":100})";
    Query q;
    CHECK(!parse_path_request(body, q).has_value());
    CHECK(q.metric == Metric::Alternating);
    CHECK(q.corners == Corners::Allow);
    CHECK(q.parity == 1);
    CHECK(q.no_stop.size() == 1 && q.no_stop[0] == Cell({1, 0}));
}

static void test_reach_request_and_response() {  // spec 5.4, case C-15
    const std::string body =
        R"({"width":5,"height":2,"cells":["12111","11111"],"noStop":[],"start":[0,0],"budget":300})";
    Query q;
    CHECK(!parse_reach_request(body, q).has_value());
    CHECK(reach_response_json(q.grid, reach(q)) ==
          R"({"width":5,"height":2,"costs":[0,200,200,300,-1,100,100,200,300,-1],"stop":["11110","11110"]})");
}

static void test_not_found_responses() {
    Query q;
    CHECK(!parse_path_request(R"({"width":3,"height":1,"cells":["101"],"start":[0,0],"goal":[1,0],"budget":100})", q).has_value());
    CHECK(path_response_json(find_path(q)) == R"({"found":false,"reason":"GOAL_BLOCKED","expansions":0})");

    CHECK(!parse_path_request(with(R"("budget":100)", R"("budget":99)"), q).has_value());
    CHECK(path_response_json(find_path(q)) == R"({"found":false,"reason":"NO_PATH_WITHIN_BUDGET","expansions":1})");
}

// ---- the error table of spec 5.6, case C-18 ---------------------------------

static void test_invalid_json() {
    expect_error("", 400, "INVALID_JSON");
    expect_error(R"({"width":)", 400, "INVALID_JSON");
    expect_error("[1,2]", 400, "INVALID_JSON");               // valid JSON, but not an object
    expect_error(kBase + " trailing", 400, "INVALID_JSON");
    expect_error(R"({"width":2,"width":3})", 400, "INVALID_JSON");  // duplicate key
    expect_error(R"({"noStop":[[[[[[[[1]]]]]]]]})", 400, "INVALID_JSON");  // nesting of 9
    expect_error(with(R"("budget":100)", R"("budget":3000000000)"), 400, "INVALID_JSON");  // not an int32
    expect_error(with(R"("budget":100)", R"("budget":1.5)"), 400, "INVALID_JSON");
    expect_error(with(R"("budget":100)", R"("budget":100.0)"), 400, "INVALID_JSON");
}

static void test_unknown_field_and_wrong_types() {
    expect_error(with(R"("budget")", R"("colour":1,"budget")"), 400, "INVALID_JSON");
    expect_error(with(R"("width":2)", R"("width":"2")"), 400, "INVALID_JSON", "/width");
    expect_error(with(R"("cells":["11"])", R"("cells":[1])"), 400, "INVALID_JSON", "/cells");
    expect_error(with(R"("cells":["11"])", R"("cells":"11")"), 400, "INVALID_JSON", "/cells");
    expect_error(with(R"("start":[0,0])", R"("start":[0])"), 400, "INVALID_JSON", "/start");
    expect_error(with(R"("start":[0,0])", R"("start":[0,"0"])"), 400, "INVALID_JSON", "/start");
    expect_error(with(R"("budget":100)", R"("budget":100,"noStop":[[0]])"), 400, "INVALID_JSON", "/noStop");
    expect_error(with(R"("budget":100)", R"("budget":100,"metric":3)"), 400, "INVALID_JSON", "/metric");
    expect_error(kBase, 400, "INVALID_JSON", "", true);  // "goal" is not allowed in a reach request
}

static void test_rule_violations() {
    expect_error(with(R"("width":2)", R"("width":0)"), 422, "INVALID_REQUEST", "/width");
    expect_error(with(R"("width":2)", R"("width":201)"), 422, "INVALID_REQUEST", "/width");
    expect_error(with(R"("height":1)", R"("height":0)"), 422, "INVALID_REQUEST", "/height");
    expect_error(with(R"("height":1)", R"("height":201)"), 422, "INVALID_REQUEST", "/height");
    expect_error(with(R"("height":1)", R"("height":2)"), 422, "INVALID_REQUEST", "/cells");
    expect_error(with(R"(["11"])", R"(["1"])"), 422, "INVALID_REQUEST", "/cells/0");
    expect_error(with(R"(["11"])", R"(["1a"])"), 422, "INVALID_REQUEST", "/cells/0");
    expect_error(with(R"("start":[0,0])", R"("start":[2,0])"), 422, "INVALID_REQUEST", "/start");
    expect_error(with(R"("start":[0,0])", R"("start":[-1,0])"), 422, "INVALID_REQUEST", "/start");
    expect_error(with(R"("goal":[1,0])", R"("goal":[0,5])"), 422, "INVALID_REQUEST", "/goal");
    expect_error(with(R"("budget":100)", R"("budget":100,"geometry":"hex")"), 422, "INVALID_REQUEST", "/geometry");
    expect_error(with(R"("budget":100)", R"("budget":100,"metric":"manhattan")"), 422, "INVALID_REQUEST", "/metric");
    expect_error(with(R"("budget":100)", R"("budget":100,"corners":"maybe")"), 422, "INVALID_REQUEST", "/corners");
    expect_error(with(R"("budget":100)", R"("budget":100,"parity":2)"), 422, "INVALID_REQUEST", "/parity");
    expect_error(with(R"("budget":100)", R"("budget":-1)"), 422, "INVALID_REQUEST", "/budget");
    expect_error(with(R"("budget":100)", R"("budget":10000001)"), 422, "INVALID_REQUEST", "/budget");
    expect_error(with(R"("budget":100)", R"("budget":100,"noStop":[[5,5]])"), 422, "INVALID_REQUEST", "/noStop/0");
}

static void test_missing_fields_and_limits() {
    expect_error(with(R"(,"goal":[1,0])", ""), 422, "INVALID_REQUEST", "/goal");
    expect_error(with(R"(,"budget":100)", ""), 422, "INVALID_REQUEST", "/budget");
    expect_error(with(R"("width":2,)", ""), 422, "INVALID_REQUEST", "/width");
    Query reach_query;
    CHECK(!parse_reach_request(with(R"(,"goal":[1,0])", ""), reach_query).has_value());  // reach needs no goal
    expect_error(std::string(kMaxBodyBytes + 1, ' '), 413, "BODY_TOO_LARGE");

    std::string many = R"({"width":2,"height":1,"cells":["11"],"start":[0,0],"goal":[1,0],"budget":100,"noStop":[)";
    for (int i = 0; i < 10001; ++i) many += (i == 0 ? "[0,0]" : ",[0,0]");
    many += "]}";
    expect_error(many, 422, "INVALID_REQUEST", "/noStop");
}

// ---- error bodies -----------------------------------------------------------

static void test_error_body_from_the_spec() {  // spec 5.6
    const ApiError error{422, "INVALID_REQUEST", "goal is outside the grid", "/goal"};
    CHECK(error_json(error) ==
          R"({"type":"about:blank","title":"Invalid request","status":422,"code":"INVALID_REQUEST","detail":"goal is outside the grid","pointer":"/goal"})");
    CHECK(error_json(ApiError{400, "INVALID_JSON", "the body is not valid JSON", ""}) ==
          R"({"type":"about:blank","title":"Invalid JSON","status":400,"code":"INVALID_JSON","detail":"the body is not valid JSON"})");
}

static void test_errors_never_echo_the_request() {  // spec 9: "the request content is never echoed back"
    const std::vector<std::string> bodies = {
        with(R"("budget")", R"("SECRET-FIELD":1,"budget")"),
        with(R"("width":2)", R"("width":"SECRET-VALUE")"),
        with(R"(["11"])", R"(["SECRET-ROW"])"),
        R"({"SECRET-KEY":)",
    };
    for (const std::string& body : bodies) {
        Query q;
        const auto error = parse_path_request(body, q);
        CHECK(error.has_value());
        if (error) CHECK(error_json(*error).find("SECRET") == std::string::npos);
    }
}

// ---- hostile input ----------------------------------------------------------

static void test_garbage_never_crashes() {
    std::uint32_t state = 12345;  // a fixed seed, so every run does the same thing
    const auto next = [&state]() {
        state = state * 1664525u + 1013904223u;
        return state >> 8;
    };
    for (int i = 0; i < 3000; ++i) {
        std::string body = kBase;
        const int changes = 1 + static_cast<int>(next() % 4);
        for (int c = 0; c < changes; ++c) {
            const std::size_t at = next() % body.size();
            switch (next() % 3) {
                case 0: body[at] = static_cast<char>(next() % 256); break;
                case 1: body.erase(at, 1); break;
                default: body.insert(at, 1, "{}[]\":,-0123456789."[next() % 19]); break;
            }
        }
        Query q;
        const auto error = parse_path_request(body, q);  // must return, never crash or hang
        if (error) CHECK(error->status == 400 || error->status == 422);
    }
    std::string deep(100000, '[');  // a body that is only nesting must be refused quickly
    expect_error(deep, 400, "INVALID_JSON");
}

int main() {
    test_path_request_from_the_spec();
    test_defaults();
    test_every_option();
    test_reach_request_and_response();
    test_not_found_responses();
    test_invalid_json();
    test_unknown_field_and_wrong_types();
    test_rule_violations();
    test_missing_fields_and_limits();
    test_error_body_from_the_spec();
    test_errors_never_echo_the_request();
    test_garbage_never_crashes();

    if (failures == 0) {
        std::cout << "All API tests passed\n";
        return 0;
    }
    std::cerr << failures << " check(s) failed\n";
    return 1;
}
