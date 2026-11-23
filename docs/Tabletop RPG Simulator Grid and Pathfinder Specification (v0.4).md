# Tabletop RPG Simulator: Grid and Pathfinder Specification (v0.4, draft)

Nov 15, 2026 · @Samarth

The contract for the grid, the movement cost model and the C++ pathfinder service. It is written for two builders: the teammate building the C++ service, and the coding AI building the Java side (the cost-map builder, `JavaPathfinder` and `CppPathfinderClient`). Both sides implement this document, and both must pass the same contract test corpus (section 12). Companion documents: Architecture (sections 4.11, 5 and 7), Ruleset Specification (Part B movement rules), Requirements Specification, AI Build Brief, and the Decision Log.

Status: **draft**. Decisions G-1 to G-15 (section 13) are decided (accepted 2026-11-15), and so are G-16 and G-18 (accepted 2026-11-21) and G-17, the detection method and its accuracy target (accepted 2026-11-22). The document moves to 1.0 when the contract corpus exists.

## 1. Purpose and scope

The pathfinder answers two questions about a weighted square grid: what is the cheapest path from A to B within a movement budget, and which cells can be reached within a budget. It knows nothing about D&D. The Java engine decides what each cell costs for the creature that is moving and sends that as a plain grid.

**In scope:** the grid model, saved maps and obstacle detection, creature placement, distance metrics, cost model, the HTTP contract (v1), determinism, limits, failure behavior, the Java client policy, security, observability, build, test and delivery.

**Out of scope:** area shapes for spells (they stay in the Java engine), line of sight and vision (v2), hex grids, gRPC, a job queue and result caching.

## 2. Design summary

| Question | Answer |
| --- | --- |
| What kind of component? | A long-running, stateless HTTP service on the internal compose network. It stores nothing. |
| Load | Human speed: a reach query when a turn starts and a path query for each move; peak about 5 requests per second. Typical grid 30 by 20 cells; hard cap 200 by 200. |
| Latency budget | The whole move must fit under about 300 ms including the network. The service deadline is 200 ms. |
| Bottleneck | One request's CPU time (the search), then the HTTP round trip. Memory per request is a few megabytes. |
| Targets (service time, excluding network, on a 4-core laptop) | 30x20: p95 under 5 ms. 100x100: p95 under 25 ms. 200x200 worst case (unreachable goal): p95 under 80 ms. |
| Failure modes | Bad input, oversize input, too many concurrent requests, a slow search, the service down. None may hurt the game: the Java fallback answers. |
| Authorization | Internal network only. An optional shared bearer token (section 9). |
| Idempotence and concurrency | Every request is a pure function of its body, so repeating one is harmless and requests can run in parallel. |
| Observability | One JSON log line per request with the trace ID, a health endpoint, and optional metrics (section 10). |

## 3. Grid model

- **Square grid, 8 neighbors.** One cell is 5 feet. `geometry` is always `"square"` in v1; any other value is rejected (the field is the extension point for hex).
- **Coordinates:** `x` is the column and `y` is the row. The origin `[0,0]` is the top-left cell. `cells[y][x]` addresses a cell.
- **Neighbor order (fixed, used for determinism):** N `(0,-1)`, E `(1,0)`, S `(0,1)`, W `(-1,0)`, NE `(1,-1)`, SE `(1,1)`, SW `(-1,1)`, NW `(-1,-1)`.
- **Cell encoding:** each row is a string with one digit per cell. `0` means blocked (a wall or an impassable creature space). `1` to `9` is the cost multiplier for entering the cell: `1` is normal terrain, `2` is difficult terrain, and the Java engine builds `3` and `4` when extra costs add (difficult terrain plus crawling, plus squeezing, decision E-16).
- **`noStop` cells:** a list of `[x,y]` cells that can be passed through but not ended on (for example a space occupied by an ally). A blocked cell is never listed.
- **Creature size:** the service is size-blind. For a creature larger than Medium the Java engine sends a map prepared for its anchor cell: a cell is blocked if any cell of the footprint would be blocked, and its multiplier is the highest among the footprint cells (decision G-3). Tiny creatures occupy one whole cell (decision E-20).

  Saved maps. A saved map is a grid plus a background image (decision G-16). The grid is the model: movement, distance and the pathfinder read only the grid, and the image never reaches the pathfinder. Each obstacle or difficult square also records whether it was detected or confirmed by the DM. The Java engine sends the pathfinder a square as blocked (0) only when the DM has confirmed it, so a suggested square changes no path until the DM saves. A map has a square size and an offset in pixels, and its width and height in squares stay within the 200 by 200 cap of section 7.

  Eligible squares. For a creature of a given footprint, a square is eligible when the whole footprint lies on the map, none of its squares is an obstacle (detected or confirmed) and none is occupied. Difficult terrain is eligible. This query lives in the Java engine (engine.grid), because the pathfinder is size-blind (G-3) and the query is not a path question (decision G-18). Placement and the move preview use it.

  Random choice inside a rectangle. To place k creatures inside a rectangle, the engine takes the eligible squares whose footprint lies inside the rectangle, and draws k of them without replacement, one creature at a time, removing the footprint of each drawn square before the next draw. The draw uses the engine's random source and the layout is logged when the encounter starts, so a replay places the same squares. If the rectangle holds fewer eligible squares than creatures, no creature is placed and the answer says how many squares there are. For creatures larger than Medium a draw can fail although the count suffices; the engine then tries up to 20 other orders before it answers the same way.

  Obstacle detection (decision G-17, decided). Detection runs in Java on the server. For each square it samples the pixels inside the square, converts them to luminance, and compares the share of dark pixels with a threshold found for the whole image by Otsu's method. A square whose dark share is above 60% is suggested as an obstacle. Only the pixels inside squares are read, and the image is decoded once, so the work grows with the number of squares and not with the image size. It runs inside the request that asks for it, with no queue or background task, and it ends with a failure reason (timeout or unreadable) rather than an error. It never calls the pathfinder. The accuracy target is a starting point that saves the DM time: on a set of at least 10 maps whose squares were marked by hand, at least 90% of squares agree with the hand marking and at least 80% of the hand-marked obstacle squares are suggested. The set and the check belong to the contract corpus (section 12).

## 4. Cost model

All costs are integers in scaled units, 100 per 5 feet, so budgets are deterministic and no floating point is used.

### 4.1 Metrics

| `metric` | Orthogonal step | Diagonal step |
| --- | --- | --- |
| `equal_cost` (default) | 100 | 100 |
| `true_diagonal` | 100 | 141 |
| `alternating` (the 5-10-5 rule) | 100 | 100 when the diagonal parity is even, 200 when it is odd |

For `alternating`, the **parity** is the number of diagonal steps already taken, modulo 2. Each diagonal step flips it; an orthogonal step leaves it unchanged. Because the diagonals in a turn count across several moves, the request carries the starting parity (`parity`, 0 or 1, default 0), and the search state is the cell plus the parity (decision G-5).

### 4.2 Step cost

The cost of one step is the metric's base step cost multiplied by the multiplier of the cell **entered** (decision G-2). The cell the creature starts in costs nothing.

### 4.3 Corner cutting

A diagonal step from `(x,y)` to `(x+dx,y+dy)` is forbidden when `corners` is `"forbid_if_blocked"` (the default) and either `(x+dx,y)` or `(x,y+dy)` is blocked (multiplier 0). With `corners` set to `"allow"` it is permitted. Only blocked cells count: a cell occupied by a creature does not block a corner, because creature spaces are handled through multipliers and `noStop` (decision G-4).

### 4.3.1 Worked examples

| Case | Budget | Result |
| --- | --- | --- |
| `true_diagonal`, open grid, a creature with speed 30 ft (budget 600) moving only diagonally | 600 | 4 diagonal steps cost 564 and fit; a fifth would cost 705 and does not |
| `alternating`, parity 0, diagonal moves only | 600 | costs 100, 200, 100, 200 = 600, so 4 diagonal steps fit exactly |
| `alternating`, parity 1, diagonal moves only | 600 | costs 200, 100, 200, 100 = 600, so 4 diagonal steps fit exactly |
| Entering a difficult cell (multiplier 2) with an orthogonal step | any | costs 200 |
| A diagonal step into a multiplier 3 cell under `true_diagonal` | any | costs 141 x 3 = 423 |

### 4.4 Heuristic

The search heuristic is `h = 100 x max(|dx|, |dy|)` for every metric. It never overestimates, because every step costs at least 100, and it is consistent, so each search state is expanded at most once. The exact heuristic is part of the contract because it affects which of several equal-cost paths is found.

### 4.5 Ranges

The largest single step costs 200 x 9 = 1800 and a path has at most 40,000 steps, so a path cost is at most 72 million, well inside a 32-bit signed integer. All costs, budgets and counts are `int32`.

## 5. The contract (v1)

### 5.1 Transport and versioning

- HTTP/1.1, JSON bodies (`Content-Type: application/json`), UTF-8, on port 9000 by default.
- The API version is in the URL (`/v1/...`). Adding an optional response field is not a breaking change, so clients must ignore response fields they do not know. Request bodies are strict: an unknown request field is an error. Any breaking change means `/v2`.
- Endpoints: `POST /v1/path`, `POST /v1/reach`, `GET /healthz`, `GET /v1/info`, and optionally `GET /metrics`. Later: `/v1/los` and `/v1/vision` (v2). There is no `/v1/area`: area shapes are computed in the Java engine.

### 5.2 Request fields shared by `/v1/path` and `/v1/reach`

| Field | Type | Required | Rule |
| --- | --- | --- | --- |
| `geometry` | string | no, default `square` | Only `square` is supported |
| `metric` | string | no, default `equal_cost` | `equal_cost`, `true_diagonal` or `alternating` |
| `corners` | string | no, default `forbid_if_blocked` | `forbid_if_blocked` or `allow` |
| `parity` | integer | no, default 0 | 0 or 1; only meaningful for `alternating` (ignored otherwise) |
| `width`, `height` | integer | yes | 1 to 200 each |
| `cells` | array of strings | yes | exactly `height` strings of exactly `width` characters, each `0` to `9` |
| `noStop` | array of `[x,y]` | no | cells inside the grid, at most 10,000 entries |
| `start` | `[x,y]` | yes | inside the grid |
| `budget` | integer | yes | 0 to 10,000,000 |

### 5.3 `POST /v1/path`

Additional field: `goal` (`[x,y]`, required, inside the grid).

Example request:

```json
{ "geometry": "square", "metric": "equal_cost", "corners": "forbid_if_blocked",
  "width": 5, "height": 2, "cells": ["12111", "11111"], "noStop": [],
  "start": [0, 0], "goal": [4, 0], "budget": 600 }
```

Example response (HTTP 200):

```json
{ "found": true, "path": [[0,0],[1,1],[2,0],[3,0],[4,0]], "cost": 400, "expansions": 5 }
```

The path avoids the difficult cell at `[1,0]` by zig-zagging, and costs 400 instead of 500.

Rules:

- The path starts with `start` and ends with `goal`, with one entry per step. `cost` is the sum of the step costs.
- If `start` equals `goal`, the response is `found: true`, `path: [start]` and `cost: 0`, whatever the cell is.
- The goal must not be blocked and must not be in `noStop` (unless it is the start). Intermediate cells may be in `noStop`.
- Only paths with cost at most `budget` are returned.
- `expansions` is the number of search states removed from the open set, including the goal, ignoring stale entries. It is deterministic and is used by benchmarks and tests.

When no path is returned: HTTP 200 with `{ "found": false, "reason": "..." }` using a reason from section 5.5.

### 5.4 `POST /v1/reach`

Returns the cheapest cost of reaching every cell within the budget. It takes the shared fields only (no `goal`).

Example request (same map, budget 300):

```json
{ "width": 5, "height": 2, "cells": ["12111", "11111"], "noStop": [],
  "start": [0, 0], "budget": 300 }
```

Example response (HTTP 200):

```json
{ "width": 5, "height": 2,
  "costs": [0, 200, 200, 300, -1,   100, 100, 200, 300, -1],
  "stop":  ["11110", "11110"] }
```

- `costs` is a flat row-major array of `width x height` integers: the cheapest cost to reach the cell, or `-1` if it cannot be reached within the budget. For `alternating` it is the minimum over both parities. The start cell is `0`.
- `stop` has `height` strings of `width` characters: `1` if the cell is reachable and can be ended on (not blocked, not in `noStop`), otherwise `0`. The start cell counts as stoppable.
- A reach query never fails because of the budget; unreachable cells simply show `-1`.

### 5.5 Reasons for `found: false`

| Reason | Meaning | Client action |
| --- | --- | --- |
| `NO_PATH_WITHIN_BUDGET` | No path of cost at most the budget exists | Authoritative: the move is illegal |
| `GOAL_BLOCKED` | The goal cell is blocked | Authoritative |
| `GOAL_NO_STOP` | The goal cell is in `noStop` | Authoritative |
| `EXPANSION_CAP` | The search reached 100,000 expansions (a safety net that valid inputs cannot reach, because there are at most 80,000 states) | Treated like `NO_PATH_WITHIN_BUDGET`; the Java fallback has the same cap |
| `DEADLINE` | The 200 ms deadline passed | Java fallback computes the answer |

### 5.6 Errors

Errors use `application/problem+json` with this body (the request content is never echoed back):

```json
{ "type": "about:blank", "title": "Invalid request", "status": 422,
  "code": "INVALID_REQUEST", "detail": "goal is outside the grid", "pointer": "/goal" }
```

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `INVALID_JSON` | The body is not valid JSON, has the wrong types, an unknown field, a duplicate key, nesting deeper than 8, or a number that is not an `int32` |
| 401 | `UNAUTHORIZED` | A token is configured and the request has none or a wrong one |
| 404 | `NOT_FOUND` | Unknown path |
| 405 | `METHOD_NOT_ALLOWED` | Wrong method |
| 413 | `BODY_TOO_LARGE` | The body is over 1 MB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The content type is not `application/json` |
| 422 | `INVALID_REQUEST` | Well-formed but breaks a rule: a dimension outside 1 to 200, a `cells` row of the wrong length or with a bad character, a coordinate outside the grid, an unsupported `geometry`, `metric` or `corners` value, `budget` out of range |
| 503 | `BUSY` | The request queue is full; the response has `Retry-After: 1` |
| 500 | `INTERNAL` | A bug; a generic message, details only in the log |

### 5.7 Health and info

- `GET /healthz` returns HTTP 200 with `{ "status": "ok" }` whenever the process can serve requests. The binary also supports `--healthcheck`, which performs that request locally and exits with 0 or 1, so the container needs no `curl`.
- `GET /v1/info` returns `{ "service": "rpg-pathfinder", "apiVersion": "1", "build": "<git sha>" }`.

### 5.8 Headers

- **Trace ID:** the service reads `traceparent` or `X-Request-Id`, generates one if neither is present, writes it in every log line, and returns it in `X-Request-Id`.
- **Authentication:** `Authorization: Bearer <token>`, required only when `PATHFINDER_TOKEN` is set.

## 6. Determinism and the reference algorithm

The path decides which cells a creature crosses, and that decides opportunity attacks and zone triggers; replays of a session must also follow the same path. So the Java fallback and the C++ service must return **identical paths, costs and expansion counts** for the same request, not merely equal costs (decision G-6). The algorithm below is therefore part of the contract.

### 6.1 `/v1/path`: A\*

- **State:** `(x, y, parity)`. For metrics other than `alternating` the parity is always 0.
- **Heuristic:** `h = 100 x max(|dx|, |dy|)` to the goal. `f = g + h`.
- **Open-set order (a total order, so there are no ties):** smallest `f`, then largest `g`, then smallest `y`, then smallest `x`, then smallest `parity`.
- **Loop:**
  1. Remove the first state from the open set. If its `g` is greater than the best known `g` for that state, it is stale: skip it without counting it.
  2. Count one expansion. If the state's cell is the goal, stop and return the path (the goal is tested when removed from the open set, not when it is added).
  3. If the expansion count has reached 100,000, return `EXPANSION_CAP`. Check the clock every 1,024 expansions and return `DEADLINE` if 200 ms have passed.
  4. Visit the 8 neighbors in the fixed order of section 3. Skip a neighbor that is outside the grid, blocked, or forbidden by the corner rule. Compute `g' = g + step cost` (section 4.2) and skip it if `g'` is greater than `budget`.
  5. Update a neighbor state only if `g'` is **strictly lower** than its best known `g`; record the current state as its parent and add it to the open set.
- **Result:** follow the parents back from the goal state to the start. If the open set empties, return `NO_PATH_WITHIN_BUDGET`.

Worked trace for the example in section 5.3 (the order shows the tie-breaks at work):

| Removed (expansion) | g | f | What happens |
| --- | --- | --- | --- |
| (0,0) (1) | 0 | 400 | Adds (1,0) at 200, (0,1) at 100, (1,1) at 100 |
| (1,1) (2) | 100 | 400 | Adds (2,1) at 200 and (2,0) at 200; no improvement for (1,0) or (0,1) |
| (2,0) (3) | 200 | 400 | Chosen over (2,1) because it has the smaller `y`; adds (3,0) and (3,1) at 300 |
| (3,0) (4) | 300 | 400 | Chosen over (3,1) because it has the smaller `y`; adds (4,0) at 400 |
| (4,0) (5) | 400 | 400 | The goal; return `[[0,0],[1,1],[2,0],[3,0],[4,0]]` |

### 6.2 `/v1/reach`: Dijkstra

The same states, the same step costs and the same neighbor rules, searched in order of increasing `g` with no heuristic. States with `g` greater than `budget` are not expanded. The output holds only costs, which do not depend on tie-breaking, so no tie rule is needed. For `alternating` the cost of a cell is the minimum over its two parities.

## 7. Limits and server behavior

| Limit | Value | Result when exceeded |
| --- | --- | --- |
| Request body | 1 MB | 413 |
| Header block | 8 KB | 400 |
| Grid width and height | 1 to 200 each | 422 |
| `noStop` entries | 10,000 | 422 |
| `budget` | 0 to 10,000,000 | 422 |
| JSON nesting depth | 8 | 400 |
| Search expansions | 100,000 | `found: false`, `EXPANSION_CAP` |
| Search deadline | 200 ms from the moment a worker starts the request | `found: false`, `DEADLINE` |
| Read timeout for a request | 2 seconds | the connection is closed |
| Workers | the smaller of 8 and the number of cores |  |
| Queued requests waiting for a worker | 32 | 503 `BUSY` with `Retry-After: 1` |

**Configuration** is by environment variables: `PORT` (default 9000), `BIND` (default `0.0.0.0`), `WORKERS`, `MAX_QUEUE` (default 32), `PATHFINDER_TOKEN` (default unset), `LOG_LEVEL` (default `info`). A bad value stops the process at start with a clear message.

**Shutdown:** on SIGTERM the service stops accepting connections, lets in-flight requests finish for up to 5 seconds, and then exits.

**Memory:** a request holds at most about 80,000 search states, a few megabytes, and nothing is kept between requests.

## 8. The Java side

The Java engine and application layer own everything about the game; the service only searches.

**Responsibilities of the Java side**

- `GridGeometry` and `DistanceMetric` (Architecture section 7.6): neighbors, step cost and distance for the squares and metrics above.
- **Cost-map builder, per mover.** From the encounter and the moving creature it produces the `cells` digits and the `noStop` list. Difficult terrain, crawling and squeezing add one extra step each (E-16), so a cell's multiplier is 1 plus the number of extras that apply, up to 4; another creature's space is difficult terrain; a hostile creature's space is blocked unless the size difference is at least two; walls are blocked; allies' spaces are in `noStop`. For larger creatures it builds the anchor-cell map of section 3.
- `JavaPathfinder`: the reference implementation of section 6, and also the fallback. It uses the same constants, including the 100,000-expansion cap.
- `CppPathfinderClient`: calls the service under the policy below.
- **Re-validation.** The engine never trusts a path, even its own: it checks that the path is continuous, inside the grid, within the budget, free of blocked cells, and ends on a cell where the creature may stop.

**Client policy**

| Setting | Value |
| --- | --- |
| HTTP client | one shared `HttpClient`, keep-alive |
| Connect timeout | 100 ms |
| Read timeout | 250 ms |
| Retry | none; the fallback is the retry |
| Circuit breaker (Resilience4j) | a window of 20 calls; opens at a 50% failure rate or when 80% of calls take longer than 200 ms; stays open 10 seconds; 3 trial calls when half-open |
| `pathfinder.mode` | `java`, `cpp` or `auto` (default `auto`) |

**What the client does with each outcome**

| Outcome | Action |
| --- | --- |
| 200 with `found: true` | Re-validate and use the path |
| 200 with `found: false` and `NO_PATH_WITHIN_BUDGET`, `GOAL_BLOCKED`, `GOAL_NO_STOP` or `EXPANSION_CAP` | Authoritative: report the move as illegal (`INVALID_PATH`) |
| 200 with `found: false` and `DEADLINE` | Use the Java fallback |
| Any 4xx | A bug in the Java request builder: log at ERROR, count `pathfinder.contract_violation`, and use the Java fallback |
| 503 `BUSY`, any 5xx, a timeout, a connection error, or an open circuit | Use the Java fallback and count `pathfinder.fallback` |

The user never sees any of these; the fallback answers.

## 9. Security

- **Trust boundary:** the service is reachable only on the internal compose network, and only port 8080 of the app is published. No outbound connections are needed or allowed.
- **Optional token:** when `PATHFINDER_TOKEN` is set, every request must carry it as a bearer token, compared in constant time; otherwise the service answers 401. It is off by default because the network is internal; it is a second layer if someone publishes the port.
- **Input is untrusted** (a bug upstream or a stray client on the network): strict JSON parsing with the limits of section 7, ASCII-only digits in `cells`, no unknown fields, no duplicate keys, bounds checks on every coordinate before use.
- **No leaks:** errors are generic and never echo the request; logs never contain `cells` or `noStop`; no stack traces in responses.
- **Process hygiene:** a non-root user, a read-only root file system where the platform allows it, no extra capabilities, pinned dependency versions, image and dependency scanning in CI.
- **Hardening:** a small fuzz target for the request parser runs nightly (should).

## 10. Observability

- **Logs:** JSON on standard output, one line per request: `ts`, `level`, `event` (`request.completed`), `trace_id`, `route`, `status`, `duration_ms`, `found`, `reason`, `expansions`, `width`, `height`, `metric`. A start-up line records the version and the non-secret configuration.
- **Health:** `GET /healthz` and the `--healthcheck` flag (section 5.7). The app's readiness does not depend on this service, because the fallback exists.
- **Metrics (should):** `GET /metrics` in Prometheus text format with `pathfinder_requests_total` by route and status, `pathfinder_request_duration_seconds`, `pathfinder_expansions`, `pathfinder_rejected_total` by reason and `pathfinder_inflight`.
- **Java side:** `pathfinder.call.duration`, `pathfinder.fallback`, `pathfinder.contract_violation` and the breaker state (Architecture section 11).

## 11. Build, test and delivery

**Repository layout** (inside the monorepo):

```
/pathfinder
  CMakeLists.txt
  src/            service code
  include/        headers
  test/           unit tests
  contract/       the shared contract corpus (section 12)
  Dockerfile
  README.md
```

**Toolchain and choices (decision G-13):** C++20; CMake 3.25 or newer; GCC 13 or Clang 17; `cpp-httplib` for HTTP (a single header, with a thread pool); `nlohmann/json` for JSON, with the limits enforced before parsing; GoogleTest for tests; `clang-format` and `clang-tidy`. Dependencies are pinned to exact versions or hashes.

**Testing:** unit tests for the grid, the costs and the search; the contract corpus; AddressSanitizer and UndefinedBehaviorSanitizer on every push; ThreadSanitizer on pull requests. Success is tracked as bugs that escaped, not as a coverage percentage.

**Container:** a multi-stage Dockerfile; a slim runtime image (Debian slim or distroless); a non-root user; `HEALTHCHECK` using `--healthcheck`; images for amd64 and arm64; tagged with the git SHA.

**Benchmarks:** Java in-process versus C++ over HTTP at 30x20, 100x100 and 200x200 (the grid cap), each with an open grid, a maze and an unreachable goal. Results are written as a CSV artifact by the nightly job, and the targets are in section 2.

**Delivery milestones for the service**

| Milestone | Content |
| --- | --- |
| M0 skeleton | `/healthz`, `/v1/info`, strict JSON parsing with limits, the 400, 401, 404, 405, 413, 415, 422 and 503 errors, logging, the optional token, Dockerfile |
| M1 | `/v1/path` with `equal_cost` and the corner rule; the matching corpus cases pass |
| M2 | `/v1/reach`; `noStop`, multipliers and budgets |
| M3 | `true_diagonal` and `alternating` with the parity input |
| M4 | The concurrency limit, graceful shutdown, the deadline and the expansion cap |
| M5 | CI jobs, sanitizers, benchmarks and the multi-architecture image |

## 12. The contract test corpus

One shared set of test cases is the proof that the two implementations agree.

- **Location:** `/pathfinder/contract/cases/*.json`, one file per case.
- **Case format:** `{ "name", "description", "endpoint", "request", "expect": { "status", "body" } }`. For successful results `body` is the exact expected JSON, including `path`, `cost` and `expansions`. For errors `body` holds the expected `code`.
- **Who runs it:** the C++ tests load every case and call the request handler in-process, plus a few over real HTTP; the Java tests read the same files in a parameterized test against `JavaPathfinder`, and a pull-request job runs them against the built container.
- **Where the expected outputs come from:** the first Java reference implementation generates them, a person reviews each by hand against section 6, and the C++ service must then reproduce them exactly.
- **Change control:** a case changes only together with a change to this specification and a new version of this document.

Minimum case list:

| ID | Case | Expectation |
| --- | --- | --- |
| C-01 | Straight line on an open grid, `equal_cost` | The straight path and cost |
| C-02 | The zig-zag example of section 5.3 | Exactly `[[0,0],[1,1],[2,0],[3,0],[4,0]]`, cost 400, expansions 5 |
| C-03 | `start` equals `goal`, including a start that is `noStop` or blocked | `path: [start]`, cost 0 |
| C-04 | The goal is blocked | `GOAL_BLOCKED` |
| C-05 | The goal is in `noStop` | `GOAL_NO_STOP` |
| C-06 | The path must pass through a `noStop` cell | Found, with the right cost |
| C-07 | A wall forces a detour | The detour path |
| C-08 | The goal is enclosed | `NO_PATH_WITHIN_BUDGET` |
| C-09 | Budget exactly equal to the cost, and one less | Found; then `NO_PATH_WITHIN_BUDGET` |
| C-10 | Corner cutting: the same map with `forbid_if_blocked` and with `allow` | Different results as specified |
| C-11 | `true_diagonal`, budget 600 | 4 diagonal steps reachable, the fifth is not (path and reach) |
| C-12 | `alternating`, parity 0 and parity 1 | Different step costs at the start; both reach 4 diagonals in 600 |
| C-13 | Cells with multipliers 2, 3 and 4 | Costs as specified in section 4.2 |
| C-14 | Open grids where many paths tie, for each of the three metrics | The exact canonical path of section 6 |
| C-15 | Reach: the example of section 5.4, budget 0, `noStop` and blocked cells, `alternating` | The exact `costs` and `stop` |
| C-16 | The start cell is blocked | The creature can leave it |
| C-17 | A 1x1 grid; a 200x200 open grid; a 200x200 grid with an unreachable goal | Found; found; `NO_PATH_WITHIN_BUDGET` with the expansion count equal to the number of reachable states |
| C-18 | The error table of section 5.6: invalid JSON, an unknown field, a duplicate key, wrong types, nesting of 9, an `int32` overflow, a dimension of 0 and 201, a row of the wrong length, a bad character, a coordinate outside the grid, a bad `geometry`, `metric` or `corners`, a negative budget, a body over 1 MB, a wrong content type, an unknown path, a wrong method, a missing token | The status and `code` of section 5.6 |
| C-19 | `EXPANSION_CAP` | Tested with the cap lowered by a test-only setting |
| C-20 | `DEADLINE` | Tested with an injected clock |
| C-21 | `BUSY` | Tested with one worker, no queue and concurrent requests |

## 13. Decisions (decided)

| ID | Question | Decision | Status |
| --- | --- | --- | --- |
| G-1 | How are cell costs encoded? | A digit per cell: `0` blocked, `1` to `9` the cost multiplier, replacing the three characters of the old sample | Decided |
| G-2 | How is the cost of a step computed? | The metric's base cost multiplied by the multiplier of the cell entered | Decided |
| G-3 | How are creatures larger than Medium handled? | The Java engine prepares an anchor-cell map (blocked if any footprint cell is blocked, the highest multiplier); the service is size-blind | Decided |
| G-4 | When is a diagonal blocked by a corner? | When either orthogonal neighbor is a blocked cell; creature spaces do not count | Decided |
| G-5 | How does the 5-10-5 rule carry across a turn? | The request carries the starting parity and the search state includes it | Decided |
| G-6 | How are equal-cost paths chosen? | One fixed algorithm (section 6) so Java and C++ return identical paths, costs and expansion counts | Decided |
| G-7 | What does a reach query return? | A flat cost array (-1 for unreachable) and a stoppable bitmap | Decided |
| G-8 | Coordinates and units | `x` column, `y` row, origin top-left, scaled `int32` costs | Decided |
| G-9 | How are failures reported? | `found: false` with a reason for results; `application/problem+json` with stable codes for errors | Decided |
| G-10 | What are the limits? | The numbers of section 7 | Decided |
| G-11 | What happens under load? | A bounded worker pool and queue; 503 `BUSY` beyond them | Decided |
| G-12 | Who may call the service? | The internal network only, with an optional bearer token | Decided |
| G-13 | What are the C++ technology choices? | C++20, CMake, `cpp-httplib`, `nlohmann/json`, GoogleTest, sanitizers, a non-root multi-architecture image | Decided |
| G-14 | How do the two sides stay in agreement? | A shared corpus in `/pathfinder/contract/`, run by both | Decided |
| G-15 | Which inconsistencies are corrected? | The benchmark sizes become 30x20, 100x100 and 200x200; the `/v1/area` endpoint is dropped, because area shapes stay in the engine | Decided |
| G-16 | What is a saved map? | A grid plus a background image. Rules and pathfinding read only the grid. Each obstacle or difficult square records whether it was detected or confirmed by the DM, and only confirmed squares block movement | Decided |
| G-17 | How are obstacles detected, and how good must it be? | Server side in Java, inside the request, by the dark share of each square against an Otsu threshold; at least 90% of squares agree and at least 80% of obstacles are found on a hand-marked set (section 3) | Decided |
| G-18 | Where do eligible squares and random placement live? | In the Java engine (engine.grid), not in the pathfinder, which stays size-blind (G-3). The draw is without replacement, uses the engine's random source and is logged | Decided |

## 14. Later and out of scope

- Line of sight and vision endpoints (`/v1/los`, `/v1/vision`) for the v2 vision work.
- Hex geometry, through the `geometry` field.
- Exact corner and squeeze rules for creatures larger than Medium (version 1 uses the anchor-cell map).
- Paths that avoid opportunity attacks, which would be an extra cost layer sent by the engine.
- Batch queries for several movers, response caching, gRPC and a job queue, only if the benchmarks show a need.

## 15. Version and revision history

Current version: **0.4 (draft)**. The document becomes 1.0 when the contract corpus exists.

| Version | Date | Changes |
| --- | --- | --- |
| 0.1 | 2026-11-15 | First draft: grid model, cost model, the v1 contract, determinism, limits, the Java side, security, observability, build and delivery, the contract corpus, decisions G-1 to G-15 |
| 0.2 | 2026-11-15 | Decisions G-1 to G-15 accepted and marked decided; no change to the contract itself. The document moves to 1.0 when the contract corpus exists |
| 0.3 | 2026-11-21 | UX and API Specification, accepted by the owner on 2026-11-21, applied by reference. Section 3 gains saved maps (a grid plus an image, detected and confirmed squares), the eligible squares query, the random choice inside a rectangle with the case of too few squares, and the obstacle detection method with its accuracy target. Decisions G-16 and G-18 are decided; G-17 is proposed. The contract itself does not change. |
| 0.4 | 2026-11-22 | G-17 accepted by the owner (the detection method and its accuracy target), and the status of G-15, accepted with G-1 to G-14 on 2026-11-15, corrected from Proposed to Decided. The contract itself does not change. |
