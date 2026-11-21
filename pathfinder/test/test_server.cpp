#include <cstdlib>
#include <iostream>
#include <string>

#include "pathfinder/server.hpp"

using namespace pf;

static int failures = 0;
#define CHECK(...) \
    do { \
        if (!(__VA_ARGS__)) { \
            std::cerr << __FILE__ << ":" << __LINE__ << "  CHECK failed: " #__VA_ARGS__ "\n"; \
            ++failures; \
        } \
    } while (0)

static const std::string kPathBody =
    R"({"width":5,"height":2,"cells":["12111","11111"],"start":[0,0],"goal":[4,0],"budget":600})";
static const std::string kReachBody =
    R"({"width":5,"height":2,"cells":["12111","11111"],"start":[0,0],"budget":300})";

static HttpRequest get(const std::string& path) {
    HttpRequest request;
    request.method = "GET";
    request.path = path;
    return request;
}

static HttpRequest post(const std::string& path, const std::string& body,
                        const std::string& content_type = "application/json") {
    HttpRequest request;
    request.method = "POST";
    request.path = path;
    request.body = body;
    request.headers["content-type"] = content_type;
    return request;
}

static std::string header(const HttpResponse& response, const std::string& name) {
    const auto it = response.headers.find(name);
    return it == response.headers.end() ? "" : it->second;
}

static bool is_problem(const HttpResponse& response, int status, const std::string& code) {
    const std::string expected = "\"code\":\"" + code + "\"";
    const bool ok = response.status == status && response.content_type == "application/problem+json" &&
                    response.body.find(expected) != std::string::npos;
    if (!ok) {
        std::cerr << "  expected problem " << status << " " << code << ", got " << response.status << " "
                  << response.content_type << " " << response.body << "\n";
    }
    return ok;
}

// ---- the routes that work ---------------------------------------------------

static void test_health_and_info() {
    const ServerConfig config{"", "abc123"};
    const HttpResponse health = handle(config, get("/healthz"));
    CHECK(health.status == 200 && health.content_type == "application/json");
    CHECK(health.body == R"({"status":"ok"})");

    const HttpResponse info = handle(config, get("/v1/info"));
    CHECK(info.status == 200);
    CHECK(info.body == R"({"service":"rpg-pathfinder","apiVersion":"1","build":"abc123"})");
}

static void test_path_and_reach() {
    const ServerConfig config;
    const HttpResponse path = handle(config, post("/v1/path", kPathBody));
    CHECK(path.status == 200 && path.content_type == "application/json");
    CHECK(path.body == R"({"found":true,"path":[[0,0],[1,1],[2,0],[3,0],[4,0]],"cost":400,"expansions":5})");

    const HttpResponse reach = handle(config, post("/v1/reach", kReachBody));
    CHECK(reach.status == 200);
    CHECK(reach.body == R"({"width":5,"height":2,"costs":[0,200,200,300,-1,100,100,200,300,-1],"stop":["11110","11110"]})");
}

static void test_content_type_may_have_parameters() {
    const ServerConfig config;
    CHECK(handle(config, post("/v1/path", kPathBody, "application/json; charset=utf-8")).status == 200);
    CHECK(handle(config, post("/v1/path", kPathBody, "Application/JSON")).status == 200);
}

// ---- the errors of spec 5.6 that belong to the HTTP layer -------------------

static void test_unknown_path_and_wrong_method() {
    const ServerConfig config;
    CHECK(is_problem(handle(config, get("/nope")), 404, "NOT_FOUND"));
    CHECK(is_problem(handle(config, get("/v1/path/extra")), 404, "NOT_FOUND"));

    const HttpResponse wrong = handle(config, get("/v1/path"));
    CHECK(is_problem(wrong, 405, "METHOD_NOT_ALLOWED"));
    CHECK(header(wrong, "Allow") == "POST");

    const HttpResponse also_wrong = handle(config, post("/healthz", "{}"));
    CHECK(is_problem(also_wrong, 405, "METHOD_NOT_ALLOWED"));
    CHECK(header(also_wrong, "Allow") == "GET");
}

static void test_wrong_content_type() {
    const ServerConfig config;
    CHECK(is_problem(handle(config, post("/v1/path", kPathBody, "text/plain")), 415, "UNSUPPORTED_MEDIA_TYPE"));
    HttpRequest none = post("/v1/path", kPathBody);
    none.headers.erase("content-type");
    CHECK(is_problem(handle(config, none), 415, "UNSUPPORTED_MEDIA_TYPE"));
}

static void test_errors_from_the_json_layer_pass_through() {
    const ServerConfig config;
    CHECK(is_problem(handle(config, post("/v1/path", "not json")), 400, "INVALID_JSON"));
    CHECK(is_problem(handle(config, post("/v1/path", R"({"width":0,"height":1,"cells":["1"],"start":[0,0],"goal":[0,0],"budget":1})")),
                     422, "INVALID_REQUEST"));
    CHECK(is_problem(handle(config, post("/v1/path", std::string(kMaxBodyBytes + 1, ' '))), 413, "BODY_TOO_LARGE"));
}

static void test_token() {
    const ServerConfig config{"s3cret", "dev"};
    CHECK(is_problem(handle(config, get("/healthz")), 401, "UNAUTHORIZED"));
    CHECK(header(handle(config, get("/healthz")), "WWW-Authenticate") == "Bearer");

    HttpRequest wrong = get("/healthz");
    wrong.headers["authorization"] = "Bearer wrong";
    CHECK(is_problem(handle(config, wrong), 401, "UNAUTHORIZED"));

    HttpRequest prefix = get("/healthz");
    prefix.headers["authorization"] = "Bearer s3cre";
    CHECK(is_problem(handle(config, prefix), 401, "UNAUTHORIZED"));

    HttpRequest longer = get("/healthz");
    longer.headers["authorization"] = "Bearer s3cretX";
    CHECK(is_problem(handle(config, longer), 401, "UNAUTHORIZED"));

    HttpRequest right = get("/healthz");
    right.headers["authorization"] = "Bearer s3cret";
    CHECK(handle(config, right).status == 200);

    // The token is checked before the path, so an unauthenticated caller cannot probe routes.
    CHECK(is_problem(handle(config, get("/nope")), 401, "UNAUTHORIZED"));

    // Without a configured token, an Authorization header means nothing.
    const ServerConfig open;
    HttpRequest stray = get("/healthz");
    stray.headers["authorization"] = "Bearer anything";
    CHECK(handle(open, stray).status == 200);
}

// ---- trace IDs and the log --------------------------------------------------

static void test_trace_ids() {
    const ServerConfig config;

    HttpRequest given = get("/healthz");
    given.headers["x-request-id"] = "req-42";
    const HttpResponse echoed = handle(config, given);
    CHECK(echoed.trace_id == "req-42" && header(echoed, "X-Request-Id") == "req-42");

    HttpRequest parent = get("/healthz");
    parent.headers["traceparent"] = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    CHECK(handle(config, parent).trace_id == "0af7651916cd43dd8448eb211c80319c");

    const HttpResponse first = handle(config, get("/healthz"));
    const HttpResponse second = handle(config, get("/healthz"));
    CHECK(first.trace_id.size() == 32 && second.trace_id.size() == 32);
    CHECK(first.trace_id != second.trace_id);
    CHECK(header(first, "X-Request-Id") == first.trace_id);

    // A trace ID is written to the log, so anything that is not a short plain value is replaced.
    HttpRequest hostile = get("/healthz");
    hostile.headers["x-request-id"] = "bad\nid\"with{stuff}";
    CHECK(handle(config, hostile).trace_id.size() == 32);
    HttpRequest too_long = get("/healthz");
    too_long.headers["x-request-id"] = std::string(65, 'a');
    CHECK(handle(config, too_long).trace_id.size() == 32);

    // Errors carry the trace ID too.
    HttpRequest bad = get("/nope");
    bad.headers["x-request-id"] = "req-43";
    CHECK(header(handle(config, bad), "X-Request-Id") == "req-43");
}

static void test_log_lines() {
    const ServerConfig config;
    HttpRequest request = post("/v1/path", kPathBody);
    request.headers["x-request-id"] = "t1";
    const HttpResponse answer = handle(config, request);
    CHECK(log_line(answer, 1.23456, "2026-01-02T03:04:05.678Z") ==
          R"({"ts":"2026-01-02T03:04:05.678Z","level":"info","event":"request.completed","trace_id":"t1",)"
          R"("route":"/v1/path","status":200,"duration_ms":1.235,"found":true,"expansions":5,)"
          R"("width":5,"height":2,"metric":"equal_cost"})");

    HttpRequest missing = post("/v1/path", R"({"width":3,"height":1,"cells":["101"],"start":[0,0],"goal":[1,0],"budget":100})");
    missing.headers["x-request-id"] = "t2";
    CHECK(log_line(handle(config, missing), 0.0, "T") ==
          R"({"ts":"T","level":"info","event":"request.completed","trace_id":"t2","route":"/v1/path",)"
          R"("status":200,"duration_ms":0.0,"found":false,"reason":"GOAL_BLOCKED","expansions":0,)"
          R"("width":3,"height":1,"metric":"equal_cost"})");

    HttpRequest unknown = get("/some/secret/path");
    unknown.headers["x-request-id"] = "t3";
    CHECK(log_line(handle(config, unknown), 0.0, "T") ==
          R"({"ts":"T","level":"info","event":"request.completed","trace_id":"t3","route":"unmatched",)"
          R"("status":404,"duration_ms":0.0})");
}

static void test_logs_and_errors_never_contain_the_request() {
    const ServerConfig config{"s3cret", "dev"};
    HttpRequest request = post("/v1/path", R"({"width":2,"height":1,"cells":["SECRET-ROW"],"start":[0,0],"goal":[1,0],"budget":1})");
    request.headers["authorization"] = "Bearer s3cret";
    const HttpResponse answer = handle(config, request);
    CHECK(answer.status == 422);
    CHECK(answer.body.find("SECRET") == std::string::npos);
    const std::string line = log_line(answer, 0.5, "T");
    CHECK(line.find("SECRET") == std::string::npos && line.find("cells") == std::string::npos);
    CHECK(line.find("s3cret") == std::string::npos);
}

int main() {
    test_health_and_info();
    test_path_and_reach();
    test_content_type_may_have_parameters();
    test_unknown_path_and_wrong_method();
    test_wrong_content_type();
    test_errors_from_the_json_layer_pass_through();
    test_token();
    test_trace_ids();
    test_log_lines();
    test_logs_and_errors_never_contain_the_request();

    if (failures == 0) {
        std::cout << "All server tests passed\n";
        return 0;
    }
    std::cerr << failures << " check(s) failed\n";
    return 1;
}
