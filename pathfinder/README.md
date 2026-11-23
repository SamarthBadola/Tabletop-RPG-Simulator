# C++ pathfinder service

The pathfinder is a stateless C++20 service with CMake, vendored `cpp-httplib` and `nlohmann/json` headers, and unit and contract tests. It searches the grid supplied by the application. The application owns the game rules and the Java fallback.

Use CMake 3.25 or newer with GCC 13 or Clang 17. From this directory, configure and check a local build with:

```sh
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build
ctest --test-dir build --output-on-failure
python3 test/test_http.py build/pathfinder_server
```

From the repository root, `docker build -t rpg-pathfinder pathfinder` builds and tests the service with GCC 13. The final Debian runtime uses the `pathfinder` user and the binary's `--healthcheck`.
