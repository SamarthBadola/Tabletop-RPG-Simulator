#include "pathfinder/server.hpp"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <exception>
#include <random>
#include <string_view>

#include <nlohmann/json.hpp>

#include "pathfinder/search.hpp"

namespace pf {
namespace {

using ordered_json = nlohmann::ordered_json;

struct Route {
    std::string_view path;
    std::string_view method;
};

constexpr Route kRoutes[] = {
    {"/healthz", "GET"},
    {"/v1/info", "GET"},
    {"/v1/path", "POST"},
    {"/v1/reach", "POST"},
};

std::string to_lower(std::string text) {
    std::transform(text.begin(), text.end(), text.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return text;
}

const std::string* find_header(const HttpRequest& request, const char* lower_name) {
    const auto it = request.headers.find(lower_name);
    return it == request.headers.end() ? nullptr : &it->second;
}

bool is_hex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

// A trace ID goes into the log, so only a short, plain value is accepted from the client.
bool valid_trace_id(const std::string& id) {
    if (id.empty() || id.size() > 64) return false;
    return std::all_of(id.begin(), id.end(), [](unsigned char c) {
        return std::isalnum(c) != 0 || c == '.' || c == '_' || c == '-';
    });
}

std::string random_hex(int digits) {
    thread_local std::mt19937_64 engine{std::random_device{}()};
    static constexpr char kDigits[] = "0123456789abcdef";
    std::string out;
    for (int i = 0; i < digits; ++i) out.push_back(kDigits[engine() % 16]);
    return out;
}

std::string trace_id_for(const HttpRequest& request) {
    if (const std::string* id = find_header(request, "x-request-id")) {
        if (valid_trace_id(*id)) return *id;
    }
    // W3C traceparent: "00-<32 hex trace id>-<16 hex parent id>-<2 hex flags>"
    if (const std::string* value = find_header(request, "traceparent")) {
        if (value->size() >= 55 && (*value)[2] == '-' && (*value)[35] == '-') {
            const std::string trace = value->substr(3, 32);
            if (std::all_of(trace.begin(), trace.end(), is_hex)) return trace;
        }
    }
    return random_hex(32);
}

// Compares two secrets without stopping at the first difference, so timing reveals nothing.
bool constant_time_equal(const std::string& a, const std::string& b) {
    const std::size_t length = std::max(a.size(), b.size());
    unsigned char difference = a.size() == b.size() ? 0 : 1;
    for (std::size_t i = 0; i < length; ++i) {
        const unsigned char left = i < a.size() ? static_cast<unsigned char>(a[i]) : 0;
        const unsigned char right = i < b.size() ? static_cast<unsigned char>(b[i]) : 0;
        difference = static_cast<unsigned char>(difference | (left ^ right));
    }
    return difference == 0;
}

bool is_json_content_type(const std::string& content_type) {
    std::string media = to_lower(content_type.substr(0, content_type.find(';')));
    const auto not_space = [](unsigned char c) { return std::isspace(c) == 0; };
    media.erase(media.begin(), std::find_if(media.begin(), media.end(), not_space));
    media.erase(std::find_if(media.rbegin(), media.rend(), not_space).base(), media.end());
    return media == "application/json";
}

const char* metric_name(Metric metric) {
    switch (metric) {
        case Metric::EqualCost: return "equal_cost";
        case Metric::TrueDiagonal: return "true_diagonal";
        case Metric::Alternating: return "alternating";
    }
    return "equal_cost";
}

void fail(HttpResponse& response, const ApiError& error) {
    response.status = error.status;
    response.content_type = "application/problem+json";
    response.body = error_json(error);
}

void dispatch(const ServerConfig& config, const HttpRequest& request, HttpResponse& response,
              std::chrono::steady_clock::time_point started) {
    // 1. The token comes first, so a caller without it learns nothing about the service.
    if (!config.token.empty()) {
        const std::string* given = find_header(request, "authorization");
        if (given == nullptr || !constant_time_equal(*given, "Bearer " + config.token)) {
            fail(response, ApiError{401, "UNAUTHORIZED", "a valid bearer token is required", ""});
            response.headers["WWW-Authenticate"] = "Bearer";
            return;
        }
    }

    // 2. Which route, and is the method right for it?
    const Route* route = nullptr;
    for (const Route& candidate : kRoutes) {
        if (candidate.path == request.path) route = &candidate;
    }
    if (route == nullptr) {
        fail(response, ApiError{404, "NOT_FOUND", "there is no such path", ""});
        return;
    }
    response.route = std::string(route->path);
    if (request.method != route->method) {
        fail(response, ApiError{405, "METHOD_NOT_ALLOWED", "the method is not allowed for this path", ""});
        response.headers["Allow"] = std::string(route->method);
        return;
    }

    // 3. The two GET routes.
    if (route->path == "/healthz") {
        response.body = R"({"status":"ok"})";
        return;
    }
    if (route->path == "/v1/info") {
        ordered_json info;
        info["service"] = "rpg-pathfinder";
        info["apiVersion"] = "1";
        info["build"] = config.build;
        response.body = info.dump();
        return;
    }

    // 4. The two POST routes.
    const std::string* content_type = find_header(request, "content-type");
    if (content_type == nullptr || !is_json_content_type(*content_type)) {
        fail(response, ApiError{415, "UNSUPPORTED_MEDIA_TYPE", "the content type must be application/json", ""});
        return;
    }

    const bool is_path = route->path == "/v1/path";
    Query query;
    const std::optional<ApiError> error =
        is_path ? parse_path_request(request.body, query) : parse_reach_request(request.body, query);
    if (error) {
        fail(response, *error);
        return;
    }

    response.width = query.grid.width;
    response.height = query.grid.height;
    response.metric = metric_name(query.metric);
    if (is_path) {
        const auto limit = std::chrono::milliseconds(config.deadline_ms);
        const StopCheck out_of_time = [&] { return config.now() - started >= limit; };
        const PathResult result = find_path(query, kExpansionCap, out_of_time);
        response.body = path_response_json(result);
        response.path_search = true;
        response.found = result.found;
        if (!result.found) response.reason = reason_name(result.reason);
        response.expansions = result.expansions;
    } else {
        response.body = reach_response_json(query.grid, reach(query));
    }
}

}  // namespace

HttpResponse handle(const ServerConfig& config, const HttpRequest& request) {
    HttpResponse response;
    // Spec section 7: the 200 ms deadline counts from the moment a worker starts the request,
    // so the clock is read first, before the JSON is parsed.
    const auto started = config.now();
    try {
        dispatch(config, request, response, started);
    } catch (...) {
        // A bug. The caller gets a generic message; nothing about the request or the bug is sent.
        HttpResponse failed;
        failed.route = response.route;
        fail(failed, ApiError{500, "INTERNAL", "an internal error occurred", ""});
        response = failed;
    }
    response.trace_id = trace_id_for(request);
    response.headers["X-Request-Id"] = response.trace_id;
    return response;
}

HttpResponse error_response(const HttpRequest& request, const ApiError& error) {
    HttpResponse response;
    fail(response, error);
    response.trace_id = trace_id_for(request);
    response.headers["X-Request-Id"] = response.trace_id;
    return response;
}

HttpResponse busy_response(const HttpRequest& request) {
    HttpResponse response;
    fail(response, ApiError{503, "BUSY", "the service is busy, try again shortly", ""});
    response.headers["Retry-After"] = "1";
    response.trace_id = trace_id_for(request);
    response.headers["X-Request-Id"] = response.trace_id;
    return response;
}

std::string log_line(const HttpResponse& response, double duration_ms, const std::string& timestamp) {
    ordered_json line;
    line["ts"] = timestamp;
    // The level is the severity of the outcome; it does not decide whether the line is written.
    line["level"] = response.status >= 500 ? "error" : response.status >= 400 ? "warn" : "info";
    line["event"] = "request.completed";
    line["trace_id"] = response.trace_id;
    line["route"] = response.route;
    line["status"] = response.status;
    line["duration_ms"] = std::round(duration_ms * 1000.0) / 1000.0;
    if (response.path_search) {
        line["found"] = response.found;
        if (!response.found) line["reason"] = response.reason;
        line["expansions"] = response.expansions;
    }
    if (response.width > 0) {
        line["width"] = response.width;
        line["height"] = response.height;
        line["metric"] = response.metric;
    }
    return line.dump();
}

bool log_level_allows(const std::string& configured, const std::string& line_level) {
    const auto rank = [](const std::string& level) {
        if (level == "debug") return 0;
        if (level == "info") return 1;
        if (level == "warn") return 2;
        if (level == "error") return 3;
        return 1;  // an unknown level is treated as info
    };
    return rank(line_level) >= rank(configured);
}

}  // namespace pf
