"""Unit tests for the console's pure logic and its HTTP client.

Run with:  python -m unittest discover -s tests
"""

from __future__ import annotations

import json
import os
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from textpic_console.api import (  # noqa: E402
    ApiClient,
    ApiError,
    OfflineError,
    normalize_device_code,
    normalize_license_key,
)
from textpic_console.formatting import (  # noqa: E402
    activation_message,
    end_date_label,
    format_ts,
    mask_key,
    plan_label,
    plan_summary,
    relative_time,
    status_color,
    status_label,
)

DEVICE = "TP-5RKD-8WQZ-3MNB-6YTF"
LICENSE_ID = "lic-1"


class FakeHandler(BaseHTTPRequestHandler):
    """Tiny licence server used by the client tests."""

    calls: list[tuple[str, str, dict]] = []
    token = "secret-token"

    def log_message(self, *args):  # silence the test output
        pass

    def _authorized(self) -> bool:
        return self.headers.get("Authorization") == f"Bearer {self.token}"

    def _send(self, status: int, payload) -> None:
        body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802
        FakeHandler.calls.append(("GET", self.path, {}))
        if not self._authorized():
            self._send(401, {"error": "UNAUTHORIZED", "message": "bad token"})
            return
        if self.path.startswith("/admin/licenses"):
            self._send(200, {"licenses": [{"id": LICENSE_ID, "name": "Amina"}]})
        elif self.path == "/admin/stats":
            self._send(200, {"total": 1, "active": 1})
        elif self.path == "/admin/export.csv":
            self.send_response(200)
            self.send_header("Content-Type", "text/csv")
            self.end_headers()
            self.wfile.write(b"id,name\n1,Amina\n")
        else:
            self._send(404, {"error": "NOT_FOUND", "message": "no"})

    def do_POST(self):  # noqa: N802
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        FakeHandler.calls.append(("POST", self.path, body))
        if not self._authorized():
            self._send(401, {"error": "UNAUTHORIZED", "message": "bad token"})
            return
        if self.path == "/admin/licenses":
            self._send(201, {"license": {"id": LICENSE_ID, "key": "ABCD-EFGH-JKLM-NPQR", **body}})
        elif self.path == f"/admin/licenses/{LICENSE_ID}/block":
            self._send(200, {"license": {"id": LICENSE_ID, "status": "blocked"}})
        elif self.path == f"/admin/licenses/{LICENSE_ID}/extend":
            self._send(200, {"license": {"id": LICENSE_ID, "expiresAt": 1}})
        else:
            self._send(404, {"error": "NOT_FOUND", "message": "no"})


class ApiClientTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = HTTPServer(("127.0.0.1", 0), FakeHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = f"http://127.0.0.1:{cls.server.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def setUp(self):
        FakeHandler.calls = []
        self.client = ApiClient(self.base, "secret-token")

    def test_list_licenses(self):
        rows = self.client.list_licenses()
        self.assertEqual(rows[0]["name"], "Amina")

    def test_wrong_token_is_reported_clearly(self):
        client = ApiClient(self.base, "wrong")
        with self.assertRaises(ApiError) as caught:
            client.stats()
        self.assertIn("token", caught.exception.message.lower())

    def test_create_normalises_the_device_code(self):
        row = self.client.create_license(
            "Amina Khan", "tp 5rkd 8wqz 3mnb 6ytf", 30, None, "daily", 50
        )
        self.assertEqual(row["key"], "ABCD-EFGH-JKLM-NPQR")
        sent = FakeHandler.calls[-1][2]
        self.assertEqual(sent["deviceCode"], DEVICE)
        self.assertEqual(sent["quotaMode"], "daily")
        self.assertEqual(sent["quotaLimit"], 50)
        self.assertEqual(sent["durationDays"], 30)

    def test_lifetime_sends_null_duration(self):
        self.client.create_license("Amina", DEVICE, None, None, "unlimited", None)
        sent = FakeHandler.calls[-1][2]
        self.assertIsNone(sent["durationDays"])
        self.assertIsNone(sent["quotaLimit"])

    def test_a_bad_device_code_never_reaches_the_server(self):
        with self.assertRaises(ApiError):
            self.client.create_license("Amina", "TP-1234", 30, None)
        self.assertEqual(FakeHandler.calls, [])

    def test_csv_export_returns_bytes(self):
        self.assertTrue(self.client.export_csv().startswith(b"id,name"))

    def test_unreachable_server_raises_offline(self):
        client = ApiClient("http://127.0.0.1:9", "secret-token")
        with self.assertRaises(OfflineError):
            client.stats()


class NormalisationTest(unittest.TestCase):
    def test_device_codes(self):
        self.assertEqual(normalize_device_code("5rkd8wqz3mnb6ytf"), DEVICE)
        self.assertEqual(normalize_device_code("TP-5RKD-8WQZ-3MNB-6YTF"), DEVICE)
        self.assertIsNone(normalize_device_code("TP-1234"))
        self.assertIsNone(normalize_device_code("TP-0000-0000-0000-0000"))

    def test_license_keys(self):
        self.assertEqual(normalize_license_key("abcd efgh jklm npqr"), "ABCD-EFGH-JKLM-NPQR")
        self.assertIsNone(normalize_license_key("ABCD"))


class FormattingTest(unittest.TestCase):
    def test_status_labels_and_colors(self):
        self.assertEqual(status_label("pending"), "Pending")
        self.assertEqual(status_label("active"), "Active")
        self.assertEqual(status_color("blocked"), "#E5484D")
        self.assertEqual(status_color("nonsense"), "#8B8D98")

    def test_plan_labels(self):
        self.assertEqual(plan_label({"quotaMode": "unlimited"}), "Unlimited")
        self.assertEqual(plan_label({"quotaMode": "daily", "quotaLimit": 50}), "50/day")
        self.assertEqual(plan_label({"quotaMode": "total", "quotaLimit": 1000}), "1000 total")

    def test_plan_summary(self):
        self.assertEqual(
            plan_summary(30, "daily", 50), "30 days · max 50 images per day"
        )
        self.assertEqual(
            plan_summary(None, "total", 1000), "Lifetime · 1000 images in total"
        )
        self.assertEqual(plan_summary(30, "unlimited", None), "30 days · unlimited images")

    def test_end_date_label(self):
        row = {"expiresAt": 1_800_000_000_000, "daysLeft": 5}
        self.assertIn("5 days left", end_date_label(row))
        self.assertEqual(end_date_label({"expiresAt": None}), "Lifetime")
        self.assertIn("last day", end_date_label({"expiresAt": 1_800_000_000_000, "daysLeft": 1}))
        self.assertIn("ended", end_date_label({"expiresAt": 1_800_000_000_000, "daysLeft": 0}))

    def test_relative_time(self):
        now = 1_800_000_000_000
        self.assertEqual(relative_time(None, now), "never")
        self.assertEqual(relative_time(now - 30_000, now), "just now")
        self.assertEqual(relative_time(now - 5 * 60_000, now), "5 min ago")
        self.assertEqual(relative_time(now - 3 * 3_600_000, now), "3 h ago")
        self.assertEqual(relative_time(now - 2 * 86_400_000, now), "2 d ago")

    def test_activation_message(self):
        text = activation_message("ABCD-EFGH-JKLM-NPQR", "Amina")
        self.assertIn("ABCD-EFGH-JKLM-NPQR", text)
        self.assertIn("Amina", text)
        self.assertIn("Activate", text)

    def test_mask_key(self):
        self.assertEqual(mask_key("ABCD-EFGH-JKLM-NPQR"), "••••-••••-NPQR")

    def test_format_ts(self):
        self.assertEqual(format_ts(None), "—")
        self.assertIn("2027", format_ts(1_800_000_000_000))


if __name__ == "__main__":
    unittest.main()
