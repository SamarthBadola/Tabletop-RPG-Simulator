// Benchmark for the worst requests the service accepts (spec section 11, M5).
//
// Every case is sent through handle(), so it measures the whole request: reading the JSON,
// the search and writing the answer. It prints the median and the slowest time of several runs.
//
//   pathfinder_bench              print the table
//   pathfinder_bench --max-ms 100 also exit with 1 if any median is above 100 ms
//
// The service gives up a path search after 200 ms, so a median anywhere near that is a problem.

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <string>
#include <vector>

#include "pathfinder/server.hpp"

using Clock = std::chrono::steady_clock;

struct Case {
    std::string name;
    std::string path;
    std::string body;
};

static std::string rows_json(const std::vector<std::string>& rows) {
    std::string out = "[";
    for (std::size_t i = 0; i < rows.size(); ++i) out += (i ? ",\"" : "\"") + rows[i] + "\"";
    return out + "]";
}

static std::string request(const std::vector<std::string>& rows, const std::string& metric, const std::string& tail) {
    return "{\"width\":" + std::to_string(rows[0].size()) + ",\"height\":" + std::to_string(rows.size()) +
           ",\"cells\":" + rows_json(rows) + ",\"metric\":\"" + metric + "\"," + tail + "}";
}

int main(int argc, char** argv) {
    double max_ms = -1;
    if (argc == 3 && std::string(argv[1]) == "--max-ms") {
        max_ms = std::atof(argv[2]);
    } else if (argc != 1) {
        std::cerr << "usage: pathfinder_bench [--max-ms N]\n";
        return 2;
    }

    const std::vector<std::string> open(200, std::string(200, '1'));
    std::vector<std::string> walled = open;  // the goal (199,199) is closed in
    walled[198][198] = walled[198][199] = walled[199][198] = '0';
    std::vector<std::string> maze = open;  // a wall with one gap at the far end forces a long detour
    for (int y = 10; y < 190; y += 20) {
        for (int x = 0; x < 200; ++x) maze[static_cast<std::size_t>(y)][static_cast<std::size_t>(x)] = '0';
        maze[static_cast<std::size_t>(y)][(y / 20) % 2 ? 0 : 199] = '1';
    }

    const std::string big = "\"start\":[0,0],\"goal\":[199,199],\"budget\":10000000";
    const std::string reach_tail = "\"start\":[100,100],\"budget\":10000000";
    const std::vector<Case> cases = {
        {"path 200x200 open, equal_cost", "/v1/path", request(open, "equal_cost", big)},
        {"path 200x200 open, alternating", "/v1/path", request(open, "alternating", big)},
        {"path 200x200 goal closed in (explores everything)", "/v1/path", request(walled, "alternating", big)},
        {"path 200x200 serpentine maze", "/v1/path", request(maze, "true_diagonal", big)},
        {"reach 200x200 open, alternating", "/v1/reach", request(open, "alternating", reach_tail)},
    };

    pf::ServerConfig config;
    config.deadline_ms = 60000;  // measure the work itself, not the deadline
    constexpr int kRuns = 15;

    std::cout << std::left << std::setw(52) << "case" << std::right << std::setw(12) << "median ms" << std::setw(12)
              << "worst ms" << '\n';
    bool too_slow = false;
    for (const Case& c : cases) {
        pf::HttpRequest http;
        http.method = "POST";
        http.path = c.path;
        http.body = c.body;
        http.headers["content-type"] = "application/json";

        std::vector<double> times;
        for (int run = 0; run < kRuns; ++run) {
            const auto start = Clock::now();
            const pf::HttpResponse response = pf::handle(config, http);
            const std::chrono::duration<double, std::milli> took = Clock::now() - start;
            if (response.status != 200) {
                std::cerr << "benchmark request failed with status " << response.status << ": " << c.name << "\n";
                return 1;
            }
            times.push_back(took.count());
        }
        std::sort(times.begin(), times.end());
        const double median = times[times.size() / 2];
        std::cout << std::left << std::setw(52) << c.name << std::right << std::fixed << std::setprecision(2)
                  << std::setw(12) << median << std::setw(12) << times.back() << '\n';
        if (max_ms >= 0 && median > max_ms) too_slow = true;
    }
    if (too_slow) {
        std::cerr << "a median is above the limit of " << max_ms << " ms\n";
        return 1;
    }
    return 0;
}
