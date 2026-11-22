#include <algorithm>
#include <cctype>
#include <charconv>
#include <chrono>
#include <cstddef>
#include <cstdio>
#include <csignal>
#include <cstdlib>
#include <ctime>
#include <iostream>
#include <mutex>
#include <optional>
#include <string>
#include <atomic>
#include <system_error>
#include <thread>

#include <httplib/httplib.h>
#include <nlohmann/json.hpp>

#include "pathfinder/server.hpp"
#include "pathfinder/work_queue.hpp"

#ifndef PATHFINDER_BUILD
#define PATHFINDER_BUILD "dev"
#endif

namespace {

// ---- settings from environment variables (spec section 7) -------------------

struct Settings {
    std::string bind = "0.0.0.0";
    int port = 9000;
    int workers = 1;
    int max_queue = 32;
    std::string token;
    std::string log_level = "info";
};

std::optional<std::string> env(const char* name) {
    const char* value = std::getenv(name);
    if (value == nullptr || *value == '\0') return std::nullopt;
    return std::string(value);
}

bool parse_whole_number(const std::string& text, int low, int high, int& out) {
    int value = 0;
    const char* end = text.data() + text.size();
    const auto result = std::from_chars(text.data(), end, value);
    if (result.ec != std::errc() || result.ptr != end || value < low || value > high) return false;
    out = value;
    return true;
}

// Returns an error message, or std::nullopt when every setting is fine.
std::optional<std::string> load_settings(Settings& settings) {
    const int cores = static_cast<int>(std::thread::hardware_concurrency());
    settings.workers = std::min(8, std::max(1, cores));

    if (const auto value = env("PORT")) {
        if (!parse_whole_number(*value, 1, 65535, settings.port)) return "PORT must be a number from 1 to 65535";
    }
    if (const auto value = env("BIND")) settings.bind = *value;
    if (const auto value = env("WORKERS")) {
        if (!parse_whole_number(*value, 1, 64, settings.workers)) return "WORKERS must be a number from 1 to 64";
    }
    if (const auto value = env("MAX_QUEUE")) {
        if (!parse_whole_number(*value, 0, 10000, settings.max_queue)) return "MAX_QUEUE must be a number from 0 to 10000";
    }
    if (const auto value = env("PATHFINDER_TOKEN")) settings.token = *value;
    if (const auto value = env("LOG_LEVEL")) {
        if (*value != "debug" && *value != "info" && *value != "warn" && *value != "error") {
            return "LOG_LEVEL must be debug, info, warn or error";
        }
        settings.log_level = *value;
    }
    return std::nullopt;
}

// ---- logging (spec section 10) ----------------------------------------------

std::string timestamp_now() {
    const auto now = std::chrono::system_clock::now();
    const auto seconds = std::chrono::time_point_cast<std::chrono::seconds>(now);
    const auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(now - seconds).count();
    const std::time_t time = std::chrono::system_clock::to_time_t(seconds);
    std::tm parts{};
#ifdef _WIN32
    gmtime_s(&parts, &time);
#else
    gmtime_r(&time, &parts);
#endif
    char text[96];  // roomy on purpose: the compiler cannot know the numbers are small
    std::snprintf(text, sizeof text, "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ", parts.tm_year + 1900,
                  parts.tm_mon + 1, parts.tm_mday, parts.tm_hour, parts.tm_min, parts.tm_sec,
                  static_cast<int>(millis));
    return text;
}

std::mutex log_mutex;  // several worker threads write to the same output

void write_log(const std::string& line) {
    const std::lock_guard<std::mutex> lock(log_mutex);
    std::cout << line << std::endl;  // endl flushes, so a crash loses no line
}

// ---- the glue between the HTTP library and pf::handle -----------------------

pf::HttpRequest convert(const httplib::Request& request) {
    pf::HttpRequest out;
    out.method = request.method;
    out.path = request.path;
    out.body = request.body;
    for (const auto& header : request.headers) {
        std::string name = header.first;
        std::transform(name.begin(), name.end(), name.begin(),
                       [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
        out.headers[name] = header.second;
    }
    return out;
}

void send(const pf::HttpResponse& answer, httplib::Response& response) {
    response.status = answer.status;
    response.set_content(answer.body, answer.content_type);
    for (const auto& header : answer.headers) response.set_header(header.first, header.second);
}

// ---- load shedding and shutdown (spec section 7) -----------------------------

constexpr int kDrainSeconds = 5;

// Set on a worker thread while it is only allowed to answer 503 BUSY.
thread_local bool shedding = false;

// httplib hands every accepted connection to a "task queue". This one is our bounded WorkQueue.
class BoundedTaskQueue final : public httplib::TaskQueue {
public:
    BoundedTaskQueue(std::size_t workers, std::size_t max_waiting) : queue_(workers, max_waiting) {}

    bool enqueue(std::function<void()> fn) override {
        return queue_.submit([fn = std::move(fn)](bool shed) {
            shedding = shed;
            fn();
            shedding = false;
        });
    }
    void shutdown() override { queue_.shutdown(); }

private:
    pf::WorkQueue queue_;
};

std::atomic<bool> stop_requested{false};

extern "C" void on_stop_signal(int) { stop_requested = true; }  // only sets a flag: nothing else is safe here

std::string shutdown_line(const char* event, const char* level) {
    nlohmann::ordered_json line;
    line["ts"] = timestamp_now();
    line["level"] = level;
    line["event"] = event;
    return line.dump();
}

int run_healthcheck(const Settings& settings) {
    httplib::Client client("127.0.0.1", settings.port);
    client.set_connection_timeout(2);
    client.set_read_timeout(2);
    httplib::Headers headers;
    if (!settings.token.empty()) headers.emplace("Authorization", "Bearer " + settings.token);
    const auto result = client.Get("/healthz", headers);
    return result && result->status == 200 ? 0 : 1;
}

}  // namespace

int main(int argc, char** argv) {
    Settings settings;
    if (const auto problem = load_settings(settings)) {
        std::cerr << "pathfinder: " << *problem << "\n";
        return 2;
    }
    if (argc == 2 && std::string(argv[1]) == "--healthcheck") return run_healthcheck(settings);
    if (argc != 1) {
        std::cerr << "pathfinder: the only option is --healthcheck\n";
        return 2;
    }

    pf::ServerConfig config;
    config.token = settings.token;
    config.build = PATHFINDER_BUILD;

    httplib::Server server;
    server.set_payload_max_length(pf::kMaxBodyBytes + 1);  // one byte over reaches pf::handle, which answers 413
    server.set_read_timeout(2);
    // The library's default (SO_REUSEPORT on Linux) lets a second copy of the service bind the same
    // port silently and share the traffic. We want "port already in use" to be an error instead.
    server.set_socket_options([](auto socket) {
#ifndef _WIN32
        httplib::set_socket_opt(socket, SOL_SOCKET, SO_REUSEADDR, 1);  // allows a quick restart only
#else
        (void)socket;  // on Windows, no option means the port is held exclusively
#endif
    });
    const auto workers = static_cast<std::size_t>(settings.workers);
    const auto max_queue = static_cast<std::size_t>(settings.max_queue);
    server.new_task_queue = [workers, max_queue] { return new BoundedTaskQueue(workers, max_queue); };

    const auto serve = [&](const httplib::Request& request, httplib::Response& response) {
        const auto started = std::chrono::steady_clock::now();
        const pf::HttpResponse answer = shedding ? pf::busy_response(convert(request))
                                                 : pf::handle(config, convert(request));
        send(answer, response);
        const std::chrono::duration<double, std::milli> took = std::chrono::steady_clock::now() - started;
        write_log(pf::log_line(answer, took.count(), timestamp_now()));  // one line per request at every LOG_LEVEL (spec section 10)
    };
    server.Get(".*", serve);
    server.Post(".*", serve);
    server.Put(".*", serve);
    server.Patch(".*", serve);
    server.Delete(".*", serve);
    server.Options(".*", serve);

    // The library answers by itself when it refuses a request before any handler runs
    // (a body over its limit, a header block that is too long). Give those the same body shape.
    server.set_error_handler([&](const httplib::Request& request, httplib::Response& response) {
        if (!response.body.empty()) return;  // one of our own answers
        std::optional<pf::ApiError> error;
        if (response.status == 413) {
            error = pf::ApiError{413, "BODY_TOO_LARGE", "the body is larger than 1 MB", ""};
        } else if (response.status == 400) {
            error = pf::ApiError{400, "INVALID_JSON", "the request could not be read", ""};
        }
        if (!error) return;
        const pf::HttpResponse answer = pf::error_response(convert(request), *error);
        send(answer, response);
        write_log(pf::log_line(answer, 0.0, timestamp_now()));
    });

    if (!server.bind_to_port(settings.bind, settings.port)) {
        std::cerr << "pathfinder: cannot listen on " << settings.bind << ":" << settings.port << "\n";
        return 1;
    }

    nlohmann::ordered_json started;
    started["ts"] = timestamp_now();
    started["level"] = "info";
    started["event"] = "service.started";
    started["version"] = PATHFINDER_BUILD;
    started["bind"] = settings.bind;
    started["port"] = settings.port;
    started["workers"] = settings.workers;
    started["max_queue"] = settings.max_queue;
    started["token_required"] = !settings.token.empty();  // whether, never the token itself
    started["log_level"] = settings.log_level;
    if (pf::log_level_allows(settings.log_level, "info")) write_log(started.dump());

    // SIGTERM (what "docker stop" sends) and Ctrl+C start a clean shutdown: stop listening, let the
    // requests already accepted finish, and give up after kDrainSeconds.
    std::signal(SIGTERM, on_stop_signal);
    std::signal(SIGINT, on_stop_signal);
    std::atomic<bool> finished{false};
    std::thread watcher([&] {
        while (!finished) {
            if (stop_requested) {
                write_log(shutdown_line("service.stopping", "info"));
                server.stop();
                for (int waited_ms = 0; !finished; waited_ms += 50) {
                    if (waited_ms >= kDrainSeconds * 1000) {
                        write_log(shutdown_line("service.drain_timeout", "error"));
                        std::_Exit(1);
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(50));
                }
                return;
            }
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
    });

    server.listen_after_bind();  // returns after server.stop(), once the accepted requests are done
    finished = true;
    watcher.join();
    write_log(shutdown_line("service.stopped", "info"));
    return 0;
}
