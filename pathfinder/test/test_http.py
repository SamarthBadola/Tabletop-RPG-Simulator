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
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=7)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()


def get_health(port, headers=None):
    connection = HTTPConnection("127.0.0.1", port, timeout=2)
    try:
        connection.request("GET", "/healthz", headers=headers or {})
        response = connection.getresponse()
        return response.status, response.headers, json.loads(response.read())
    finally:
        connection.close()


class HttpContract(unittest.TestCase):
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
