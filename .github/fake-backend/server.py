"""
Stand-in for the skyblock-mythos server in CI (that repository is private).
Replays responses recorded from the real endpoints - see fixtures/README.md.
Logs every request so the client game test's traffic shows in the job log.
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

FIX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
ROUTES = {
    ("POST", "/api/auth/login"): "auth_login",
    ("POST", "/api/auth/logout"): "alerts_ack",
    ("GET", "/api/mod/shards"): "mod_shards",
    ("GET", "/api/mod/bazaar-names"): "mod_bazaar_names",
    ("GET", "/api/mod/item"): "mod_item",
    ("GET", "/api/mod/election"): "mod_election",
    ("POST", "/api/mod/absorb"): "mod_absorb",
    ("POST", "/api/shards/optimize-inventory"): "optimize_inventory",
    ("GET", "/api/shards/absorb"): "shards_absorb",
    ("GET", "/api/shards/farms"): "farms",
    ("GET", "/api/forge"): "forge",
    ("GET", "/api/status"): "status",
    ("POST", "/api/alerts/ack"): "alerts_ack",
}


class Handler(BaseHTTPRequestHandler):
    def _serve(self, method):
        path = urlparse(self.path).path
        body = ""
        if method == "POST":
            body = self.rfile.read(int(self.headers.get("Content-Length") or 0)).decode()
        auth = self.headers.get("Authorization", "")
        name = ROUTES.get((method, path))
        print(f"[fake-backend] {method} {self.path} auth={'yes' if auth else 'no'} {body[:300]}", flush=True)
        if name is None:
            self._send(404, {"ok": False, "error": "not in fake backend"})
        elif path != "/api/auth/login" and auth != "Bearer ci-token":
            self._send(401, {"ok": False, "error": "login required"})
        else:
            with open(os.path.join(FIX, name + ".json"), encoding="utf-8") as f:
                self._send(200, json.load(f))

    def _send(self, code, obj):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._serve("GET")

    def do_POST(self):
        self._serve("POST")

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5658
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
