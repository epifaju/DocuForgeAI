#!/usr/bin/env python3
"""Minimal health/status HTTP endpoint for the LibreOffice container (Phase 1)."""

from __future__ import annotations

import argparse
import json
import os
import socket
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def tcp_open(host: str, port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def pid_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8081)
    parser.add_argument("--uno-host", default="127.0.0.1")
    parser.add_argument("--uno-port", type=int, default=2002)
    parser.add_argument("--soffice-pid", type=int, default=0)
    args = parser.parse_args()

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt: str, *log_args) -> None:  # noqa: A003
            return

        def _write(self, code: int, payload: dict) -> None:
            body = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self) -> None:  # noqa: N802
            path = urllib.parse.urlparse(self.path).path
            uno_ok = tcp_open(args.uno_host, args.uno_port)
            process_ok = pid_alive(args.soffice_pid)
            healthy = uno_ok and process_ok
            payload = {
                "service": "docuforge-libreoffice",
                "status": "ok" if healthy else "degraded",
                "uno": {"host": args.uno_host, "port": args.uno_port, "listening": uno_ok},
                "sofficeProcessAlive": process_ok,
                "conversionApi": "deferred-to-phase-10",
            }
            if path in ("/health", "/"):
                self._write(200 if healthy else 503, payload)
                return
            if path == "/ready":
                self._write(200 if healthy else 503, payload)
                return
            self._write(404, {"status": "not_found", "path": path})

    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"LibreOffice health endpoint listening on 0.0.0.0:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()