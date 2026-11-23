# The pathfinder service as a container image (spec section 11, M4/M5).
#
# Build (run from the repository root):
#   docker build -t rpg-pathfinder pathfinder
# Run:
#   docker run --rm -p 9000:9000 -e PATHFINDER_TOKEN=change-me rpg-pathfinder
# Settings (all optional): PORT, BIND, WORKERS, MAX_QUEUE, PATHFINDER_TOKEN, LOG_LEVEL.

# ---- build stage: compilers live here and never reach the final image --------
FROM gcc:15-bookworm@sha256:9ca91b05c7b07d2979f16413e8b2cd6ec8a7c80ffca4121ccab0aeba33f90460 AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends cmake python3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY CMakeLists.txt ./
COPY cmake cmake
COPY include include
COPY src src
COPY test test
COPY contract contract
COPY bench bench
COPY third_party third_party
ARG PATHFINDER_BUILD=dev
# The C++ runtime library is linked into the program, so the final image needs nothing but libc.
RUN cmake -S . -B build \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_CXX_COMPILER=/usr/local/bin/g++ \
        -DPATHFINDER_BUILD="${PATHFINDER_BUILD}" \
        -DCMAKE_EXE_LINKER_FLAGS="-static-libstdc++ -static-libgcc" \
    && cmake --build build --target pathfinder_server pathfinder_tests pathfinder_api_tests \
        pathfinder_server_tests pathfinder_contract_tests pathfinder_work_queue_tests -j"$(nproc)" \
    && ctest --test-dir build --output-on-failure \
    && python3 test/test_http.py build/pathfinder_server

# ---- runtime stage: the program and nothing else ------------------------------
FROM debian:bookworm-slim@sha256:734a72b3d6142a71993b4bc580ca01d8ca687f77a2c422d96a961d3376b179bc
RUN useradd --system --uid 10001 pathfinder
COPY --from=build /src/build/pathfinder_server /usr/local/bin/pathfinder_server
USER pathfinder
EXPOSE 9000
# --healthcheck asks the running service for /healthz (it reads PORT and PATHFINDER_TOKEN itself).
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD ["/usr/local/bin/pathfinder_server", "--healthcheck"]
# "docker stop" sends SIGTERM, which the service answers with a clean shutdown (5 s limit).
ENTRYPOINT ["/usr/local/bin/pathfinder_server"]
