#pragma once

#include <cstddef>
#include <optional>
#include <string>

#include "pathfinder/search.hpp"

namespace pf {

inline constexpr std::size_t kMaxBodyBytes = 1024 * 1024;  // spec section 7: request body, 1 MB

// What is wrong with a request, in the shape of spec section 5.6.
struct ApiError {
    int status = 0;       // the HTTP status to send back
    std::string code;     // a stable code such as INVALID_JSON
    std::string detail;   // a short fixed sentence; it never contains request content
    std::string pointer;  // a JSON pointer such as "/goal", or empty
};

// Reads and checks the body of POST /v1/path.
// Returns std::nullopt and fills `query` when the request is fine; otherwise returns the error.
std::optional<ApiError> parse_path_request(const std::string& body, Query& query);

// The same for POST /v1/reach (it has no "goal" field).
std::optional<ApiError> parse_reach_request(const std::string& body, Query& query);

// The JSON bodies we send back (spec sections 5.3, 5.4 and 5.6).
std::string path_response_json(const PathResult& result);
std::string reach_response_json(const Grid& grid, const ReachResult& result);
std::string error_json(const ApiError& error);

}  // namespace pf
