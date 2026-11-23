"""Exercise the transport contract through a localhost server."""

from contextlib import contextmanager
from http.client import HTTPConnection, HTTPException
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import unittest


@contextmanager
def server(binary, **settings):
    with socket.socket() as address:
        address.bind(("127.0.0.1", 0))
        port = address.getsockname()[1]
    env = dict(os.environ, BIND="127.0.0.1", PORT=str(port), PATHFINDER_TOKEN="")
    env.update(settings)
    with tempfile.TemporaryFile() as logs:
        process = subprocess.Popen([binary], env=env, stdout=logs, stderr=logs)
        try:
            until = time.monotonic() + 5
            while time.monotonic() < until and process.poll() is None:
                try:
                    if get_health(port)[0] == 200:
                        break
                except (OSError, HTTPException):
                    time.sleep(0.05)
            else:
                logs.seek(0)
                raise RuntimeError(f"Pathfinder did not start: {logs.read().decode(errors='replace')}")
            yield port
        except Exception:
            logs.seek(0)
            print(logs.read().decode(errors="replace"), file=sys.stderr)
            raise
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=7)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


def get_health(port, headers=None):
    return request_json(port, "GET", "/healthz", headers=headers)


def request_json(port, method, path, body=None, headers=None, timeout=2):
    connection = HTTPConnection("127.0.0.1", port, timeout=timeout)
    try:
        connection.request(method, path, body, headers or {})
        response = connection.getresponse()
        return response.status, response.headers, json.loads(response.read())
    finally:
        connection.close()


class HttpContract(unittest.TestCase):
    def test_shared_corpus_examples_over_http(self):
        cases = Path(__file__).resolve().parents[1] / "contract" / "cases"
        names = ("C-02-zigzag-example", "C-15-spec-example", "C-18-nostop-blocked")
        with server(BINARY) as port:
            for name in names:
                with self.subTest(case=name):
                    case = json.loads((cases / f"{name}.json").read_text(encoding="utf-8"))
                    status, _, body = request_json(
                        port, "POST", case["endpoint"], json.dumps(case["request"]),
                        {"Content-Type": "application/json"},
                    )
                    expected = case["expect"]
                    self.assertEqual(status, expected["status"])
                    if status == 200:
                        self.assertEqual(body, expected["body"])
                    else:
                        for key, value in expected["body"].items():
                            self.assertEqual(body[key], value)

    def test_busy_is_returned_when_the_worker_and_overflow_lane_are_full(self):
        with server(BINARY, WORKERS="1", MAX_QUEUE="0") as port:
            worker = HTTPConnection("127.0.0.1", port, timeout=2)
            try:
                # Keep the normal worker assigned to this connection.
                worker.request("GET", "/healthz")
                response = worker.getresponse()
                self.assertEqual(response.status, 200)
                response.read()

                status, headers, body = get_health(port)
                self.assertEqual((status, headers["Retry-After"], body["code"]), (503, "1", "BUSY"))

                # Expect: 100-continue confirms that the rejection worker has taken this request.
                with socket.create_connection(("127.0.0.1", port), timeout=2) as rejection:
                    rejection.sendall(
                        b"POST /v1/path HTTP/1.1\r\nHost: localhost\r\n"
                        b"Content-Type: application/json\r\nContent-Length: 2\r\n"
                        b"Expect: 100-continue\r\nConnection: close\r\n\r\n"
                    )
                    with rejection.makefile("rb") as stream:
                        self.assertEqual(stream.readline(), b"HTTP/1.1 100 Continue\r\n")
                        self.assertEqual(stream.readline(), b"\r\n")
                        with socket.create_connection(("127.0.0.1", port), timeout=2):
                            self.assert_busy(*request_json(port, "GET", "/healthz", timeout=1))
                        rejection.sendall(b"{}")
                self.assertEqual(headers["Connection"], "close")
            finally:
                worker.close()

    def assert_busy(self, status, headers, body):
        self.assertEqual(status, 503)
        self.assertEqual(headers["Retry-After"], "1")
        self.assertEqual(headers["Connection"], "close")
        self.assertEqual(headers.get_content_type(), "application/problem+json")
        self.assertEqual(body["code"], "BUSY")
        self.assertTrue(headers["X-Request-Id"])

    def test_header_block_limit_counts_all_header_lines(self):
        with server(BINARY) as port:
            below = {f"X-Contract-{n}": "a" * 2600 for n in range(3)}
            self.assertEqual(get_health(port, below)[0], 200)

            above = {f"X-Contract-{n}": "a" * 2800 for n in range(3)}
            status, headers, body = get_health(port, above)
            self.assertEqual(status, 400)
            self.assertEqual(headers.get_content_type(), "application/problem+json")
            self.assertEqual(body["code"], "INVALID_JSON")
            self.assertEqual(get_health(port)[0], 200)


if __name__ == "__main__":
    BINARY = str(Path(sys.argv.pop(1)).resolve(strict=True))
    unittest.main()
