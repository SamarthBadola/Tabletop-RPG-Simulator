#pragma once

#include <chrono>
#include <functional>
#include <map>
#include <string>

#include "pathfinder/api.hpp"

namespace pf {

// What the HTTP library hands us, reduced to what the service needs.
struct HttpRequest {
    std::string method;                          // "GET", "POST", ...
    std::string path;                            // "/v1/path"
    std::string body;
    std::map<std::string, std::string> headers;  // header names in lower case
};

// What we hand back, plus a few facts for the log line (spec section 10).
struct HttpResponse {
    int status = 200;
    std::string content_type = "application/json";
    std::string body;
    std::map<std::string, std::string> headers;  // extra headers, such as Allow

    std::string trace_id;
    std::string route = "unmatched";  // the known route, never the raw path
    bool path_search = false;         // true when /v1/path ran a search
    bool found = false;
    std::string reason;
    int expansions = 0;
    int width = 0;
    int height = 0;
    std::string metric;
};

struct ServerConfig {
    std::string token;         // empty means no token is required
    std::string build = "dev";
    int deadline_ms = 200;     // spec section 7: the longest a path search may run
    // The clock the deadline uses. Tests replace it with a fake one.
    std::function<std::chrono::steady_clock::time_point()> now = [] { return std::chrono::steady_clock::now(); };
};

// All the routing, checking and answering, with no network in it (so it can be tested directly).
HttpResponse handle(const ServerConfig& config, const HttpRequest& request);

// A problem+json answer for an error found outside handle(), such as by the HTTP library.
HttpResponse error_response(const HttpRequest& request, const ApiError& error);

// The answer for a request the service has no room for: 503 BUSY with Retry-After: 1 (spec section 7).
HttpResponse busy_response(const HttpRequest& request);

// One JSON line for the log (spec section 10). It never contains cells or noStop.
std::string log_line(const HttpResponse& response, double duration_ms, const std::string& timestamp);

// Whether a log line of `line_level` is written when LOG_LEVEL is `configured`
// (order: debug, info, warn, error). The request.completed line is exempt: spec section 10
// requires one for every request at every level, so it is always written.
bool log_level_allows(const std::string& configured, const std::string& line_level);

}  // namespace pf
