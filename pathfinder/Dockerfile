# The pathfinder service as a container image (spec section 11, M4/M5).
#
# Build (run from the repository root):
#   docker build -t rpg-pathfinder pathfinder
# Run:
#   docker run --rm -p 9000:9000 -e PATHFINDER_TOKEN=change-me rpg-pathfinder
# Settings (all optional): PORT, BIND, WORKERS, MAX_QUEUE, PATHFINDER_TOKEN, LOG_LEVEL.

# ---- build stage: compilers live here and never reach the final image --------
FROM debian:bookworm-slim AS build
RUN apt-get update \
    && apt-get install -y --no-install-recommends g++ cmake make \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY CMakeLists.txt ./
COPY include include
COPY src src
COPY test test
COPY contract contract
COPY third_party third_party
ARG PATHFINDER_BUILD=dev
# The C++ runtime library is linked into the program, so the final image needs nothing but libc.
RUN cmake -S . -B build \
        -DCMAKE_BUILD_TYPE=Release \
        -DPATHFINDER_BUILD="${PATHFINDER_BUILD}" \
        -DCMAKE_EXE_LINKER_FLAGS="-static-libstdc++ -static-libgcc" \
    && cmake --build build --target pathfinder_server pathfinder_tests pathfinder_api_tests \
        pathfinder_server_tests pathfinder_contract_tests pathfinder_work_queue_tests -j"$(nproc)" \
    && ctest --test-dir build --output-on-failure

# ---- runtime stage: the program and nothing else ------------------------------
FROM debian:bookworm-slim
RUN useradd --system --uid 10001 pathfinder
COPY --from=build /src/build/pathfinder_server /usr/local/bin/pathfinder_server
USER pathfinder
EXPOSE 9000
# --healthcheck asks the running service for /healthz (it reads PORT and PATHFINDER_TOKEN itself).
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD ["/usr/local/bin/pathfinder_server", "--healthcheck"]
# "docker stop" sends SIGTERM, which the service answers with a clean shutdown (5 s limit).
ENTRYPOINT ["/usr/local/bin/pathfinder_server"]
