"""Record HTTP round-trip times for the Pathfinder benchmark grids."""

import argparse
import csv
from http.client import HTTPConnection
import json
import math
from pathlib import Path
from statistics import median
from time import perf_counter


SIZES = ((30, 20), (100, 100), (200, 200))
LAYOUTS = ("open", "maze", "unreachable")


def path_request(width, height, layout):
    grid = [["1"] * width for _ in range(height)]
    if layout == "maze":
        for index, row in enumerate(range(3, height - 2, 5)):
            grid[row] = ["0"] * width
            grid[row][width - 1 if index % 2 == 0 else 0] = "1"
    if layout == "unreachable":
        grid[height - 2][width - 2] = "0"
        grid[height - 2][width - 1] = "0"
        grid[height - 1][width - 2] = "0"
    return json.dumps({
        "width": width, "height": height, "cells": ["".join(row) for row in grid],
        "start": [0, 0], "goal": [width - 1, height - 1], "budget": 10_000_000,
    })


def measure(connection, width, height, layout, samples):
    body = path_request(width, height, layout)
    timings = []
    for _ in range(samples):
        started = perf_counter()
        connection.request("POST", "/v1/path", body, {"Content-Type": "application/json"})
        response = connection.getresponse()
        received = response.read()
        timings.append((perf_counter() - started) * 1000)
        result = json.loads(received)
        if response.status != 200:
            raise RuntimeError(f"{width}x{height} {layout}: HTTP {response.status}")
        if layout == "unreachable":
            valid = result["found"] is False and result["reason"] == "NO_PATH_WITHIN_BUDGET"
        else:
            valid = result["found"] is True
        if not valid:
            raise RuntimeError(f"{width}x{height} {layout}: unexpected result {result}")
    timings.sort()
    return (
        "cpp-http", width, height, layout, samples, f"{median(timings):.3f}",
        f"{timings[math.ceil(0.95 * samples) - 1]:.3f}",
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=9000)
    parser.add_argument("--samples", type=int, default=15)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.samples < 1:
        parser.error("--samples must be positive")
    connection = HTTPConnection("127.0.0.1", args.port, timeout=3)
    try:
        rows = [
            measure(connection, width, height, layout, args.samples)
            for width, height in SIZES
            for layout in LAYOUTS
        ]
    finally:
        connection.close()

    with args.out.open("w", encoding="utf-8", newline="") as report:
        writer = csv.writer(report)
        writer.writerow(("implementation", "width", "height", "layout", "samples", "median_ms", "p95_ms"))
        writer.writerows(rows)


if __name__ == "__main__":
    main()
