"""Core tests: model coercion, HTTP retries, storage durability."""

import io
import os
import tempfile
import time
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

from core.http import HttpClient
from core.model import clean_metrics, platform_result
from core.storage import Store


class FakeRaw:
    def __init__(self, status=200, body=b"{}", headers=None):
        self.status = status
        self._body = body
        self.headers = headers or {}

    def read(self, size=None):
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def http_error(code, body=b"{}"):
    return urllib.error.HTTPError("https://example.test", code, "error", {}, io.BytesIO(body))


class ModelTests(unittest.TestCase):
    def test_missing_values_are_dropped_not_zeroed(self):
        metrics = clean_metrics({"a": None, "b": True, "c": "n/a", "d": 3, "e": 2.5})
        self.assertEqual(metrics, {"d": 3, "e": 2.5})

    def test_non_finite_rejected(self):
        self.assertEqual(clean_metrics({"a": float("nan"), "b": float("inf")}), {})

    def test_invalid_status_coerced(self):
        result = platform_result("x", "wat")
        self.assertEqual(result["status"], "error")
        self.assertTrue(result["errors"])

    def test_source_normalised(self):
        self.assertEqual(platform_result("x", source="u")["source"], ["u"])
        self.assertEqual(platform_result("x", source=None)["source"], [])


class HttpTests(unittest.TestCase):
    def test_retries_429_then_succeeds(self):
        sleeps = []
        client = HttpClient(retries=2, sleep=sleeps.append, backoff=1.0)
        sequence = [http_error(429, b"{}"), FakeRaw(200, b'{"ok": true}')]
        with mock.patch("core.http.urllib.request.urlopen", side_effect=sequence):
            data, response = client.get_json("https://example.test/x")
        self.assertEqual(data, {"ok": True})
        self.assertTrue(response.ok)
        self.assertEqual(response.attempts, 2)
        self.assertTrue(sleeps)

    def test_404_is_not_retried(self):
        client = HttpClient(retries=3, sleep=lambda s: None)
        with mock.patch("core.http.urllib.request.urlopen", side_effect=http_error(404)) as opener:
            response = client.get("https://example.test/x")
        self.assertEqual(response.status, 404)
        self.assertEqual(opener.call_count, 1)

    def test_network_error_is_returned_not_raised(self):
        client = HttpClient(retries=0, sleep=lambda s: None)
        with mock.patch("core.http.urllib.request.urlopen", side_effect=OSError("boom")):
            response = client.get("https://example.test/x")
        self.assertIsNone(response.status)
        self.assertIn("boom", response.error)

    def test_retry_after_header_is_honoured(self):
        sleeps = []
        client = HttpClient(retries=1, sleep=sleeps.append)
        throttled = http_error(429)
        throttled.headers = {"Retry-After": "7"}
        with mock.patch("core.http.urllib.request.urlopen", side_effect=[throttled, FakeRaw(200, b"{}")]):
            client.get("https://example.test/x")
        self.assertEqual(sleeps, [7.0])


class StorageTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.tmp.name)).ensure()

    def tearDown(self):
        self.tmp.cleanup()

    def test_atomic_write_leaves_no_temp_file(self):
        path = self.store.data / "x.json"
        self.store.atomic_write(path, "{}\n")
        self.assertEqual(path.read_text(encoding="utf-8"), "{}\n")
        self.assertEqual(list(self.store.data.glob(".*.tmp")), [])

    def test_events_are_deduplicated(self):
        event = {"event_id": "abc", "message": "x"}
        self.assertEqual(len(self.store.append_events([event])), 1)
        self.assertEqual(self.store.append_events([event]), [])
        self.assertEqual(len(self.store.events()), 1)

    def test_lock_excludes_concurrent_run(self):
        with self.store.lock() as first:
            self.assertTrue(first)
            with self.store.lock(wait_seconds=0) as second:
                self.assertFalse(second)

    def test_stale_lock_is_recovered(self):
        self.store.lock_path.write_text("123 old", encoding="utf-8")
        old = time.time() - 5000
        os.utime(self.store.lock_path, (old, old))
        with self.store.lock(wait_seconds=0) as acquired:
            self.assertTrue(acquired)

    def test_history_and_milestones_survive(self):
        self.store.append_history({"timestamp_utc": "2026-11-14T00:00:00Z", "platforms": {}})
        self.assertEqual(len(self.store.history()), 1)
        self.store.save_milestones({"achieved": {"github.stars.10": "2026-11-14T00:00:00Z"}})
        self.assertIn("github.stars.10", self.store.load_milestones()["achieved"])

    def test_corrupt_history_line_is_skipped(self):
        self.store.append_history({"timestamp_utc": "a", "platforms": {}})
        with open(self.store.history_path, "a", encoding="utf-8") as handle:
            handle.write("{not json}\n")
        self.assertEqual(len(self.store.history()), 1)


if __name__ == "__main__":
    unittest.main()
