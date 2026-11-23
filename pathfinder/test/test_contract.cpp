// Contract corpus runner (spec section 12).
// Reads every contract/cases/*.json file, sends its request through handle(),
// and compares the answer with the expected one. No network is involved.

#include <algorithm>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "pathfinder/server.hpp"

#ifndef CONTRACT_DIR
#error "CONTRACT_DIR must point at the contract/cases folder"
#endif

namespace fs = std::filesystem;
using json = nlohmann::json;

static std::string lower(std::string text) {
    std::transform(text.begin(), text.end(), text.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return text;
}

// Returns an empty string when the case passes, or a description of the first difference.
static std::string run_case(const json& test) {
    pf::ServerConfig config;
    if (test.contains("config")) {
        config.token = test["config"].value("token", "");
    }

    pf::HttpRequest request;
    request.method = test.value("method", "POST");
    request.path = test.value("endpoint", "");
    request.headers["content-type"] = "application/json";
    if (test.contains("headers")) {
        for (const auto& [name, value] : test["headers"].items()) {
            request.headers[lower(name)] = value.get<std::string>();
        }
    }

    // A string request is sent as it is (so a case can hold broken JSON); anything else is serialised.
    const json& body = test["request"];
    if (test.contains("request_generator")) {
        const json& gen = test["request_generator"];
        const std::string piece = gen.at("repeat").get<std::string>();
        const std::size_t times = gen.at("times").get<std::size_t>();
        request.body.reserve(piece.size() * times);
        for (std::size_t i = 0; i < times; ++i) request.body += piece;
    } else if (body.is_string()) {
        request.body = body.get<std::string>();
    } else {
        request.body = body.dump();
    }

    const pf::HttpResponse response = pf::handle(config, request);

    const json& expect = test.at("expect");
    const int want_status = expect.at("status").get<int>();
    if (response.status != want_status) {
        return "status " + std::to_string(response.status) + ", expected " + std::to_string(want_status) +
               "  body: " + response.body.substr(0, 200);
    }

    json got;
    try {
        got = json::parse(response.body);
    } catch (const std::exception&) {
        return "response body is not JSON: " + response.body.substr(0, 200);
    }
    const json& want = expect.at("body");

    if (want_status == 200) {
        if (got != want) return "body differs\n    got:      " + got.dump() + "\n    expected: " + want.dump();
    } else {
        // For errors the contract is the code and, when given, the pointer.
        for (const char* key : {"code", "pointer"}) {
            if (want.contains(key) && (!got.contains(key) || got[key] != want[key])) {
                return std::string("field '") + key + "' differs\n    got:      " + got.dump().substr(0, 300) +
                       "\n    expected: " + want.dump();
            }
        }
        if (!want.contains("pointer") && got.contains("pointer")) {
            return "unexpected pointer in answer: " + got.dump().substr(0, 300);
        }
    }
    return "";
}

int main() {
    std::vector<fs::path> files;
    for (const auto& entry : fs::directory_iterator(CONTRACT_DIR)) {
        if (entry.path().extension() == ".json") files.push_back(entry.path());
    }
    std::sort(files.begin(), files.end());
    if (files.empty()) {
        std::cerr << "no case files found in " << CONTRACT_DIR << "\n";
        return 1;
    }

    int failures = 0;
    for (const auto& file : files) {
        std::ifstream in(file, std::ios::binary);
        std::stringstream text;
        text << in.rdbuf();
        std::string problem;
        try {
            problem = run_case(json::parse(text.str()));
        } catch (const std::exception& e) {
            problem = std::string("could not run case: ") + e.what();
        }
        if (!problem.empty()) {
            std::cerr << "FAIL " << file.filename().string() << ": " << problem << "\n";
            ++failures;
        }
    }
    std::cout << (files.size() - failures) << " of " << files.size() << " contract cases passed\n";
    return failures == 0 ? 0 : 1;
}
