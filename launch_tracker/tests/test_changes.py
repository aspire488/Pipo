"""Change engine and milestone engine tests."""

import unittest

from core.changes import diff_snapshots
from core.milestones import evaluate


def result(platform, metrics=None, meta=None, status="ok", source=None):
    return {
        "platform": platform,
        "status": status,
        "metrics": metrics or {},
        "meta": meta or {},
        "source": source or ["https://example.test/%s" % platform],
    }


def snapshot(**platforms):
    return {"platforms": platforms}


class ChangeTests(unittest.TestCase):
    def test_first_run_is_a_baseline(self):
        current = snapshot(github=result("github", {"stars": 1}))
        self.assertEqual(diff_snapshots(None, current, {}), [])

    def test_unchanged_snapshot_produces_nothing(self):
        state = snapshot(github=result("github", {"stars": 1, "apk_downloads": 25}))
        self.assertEqual(diff_snapshots(state, state, {}), [])

    def test_metric_increase_is_an_event_with_severity(self):
        old = snapshot(github=result("github", {"apk_downloads": 25}))
        new = snapshot(github=result("github", {"apk_downloads": 26}))
        events = diff_snapshots(old, new, {"severity": {"github.apk_downloads": "normal"}})
        self.assertEqual(len(events), 1)
        event = events[0]
        self.assertEqual((event["type"], event["previous"], event["current"]), ("metric_change", 25, 26))
        self.assertEqual(event["severity"], "normal")
        self.assertIn("26", event["message"])

    def test_disappearing_metric_is_not_zero(self):
        old = snapshot(freebuff=result("freebuff", {"downloads": 23}, status="manual"))
        new = snapshot(freebuff=result("freebuff", {}, status="manual"))
        events = diff_snapshots(old, new, {})
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0]["type"], "metric_missing")
        self.assertIsNone(events[0]["current"])

    def test_event_ids_are_stable(self):
        old = snapshot(github=result("github", {"stars": 1}))
        new = snapshot(github=result("github", {"stars": 2}))
        first = diff_snapshots(old, new, {})[0]["event_id"]
        second = diff_snapshots(old, new, {})[0]["event_id"]
        self.assertEqual(first, second)

    def test_removed_surface_is_critical(self):
        old = snapshot(website=result("website", {"surfaces_up": 2},
                                     meta={"surfaces": {"https://a": 200, "https://b": 200}}))
        new = snapshot(website=result("website", {"surfaces_up": 1},
                                     meta={"surfaces": {"https://a": 200, "https://b": 404}}))
        events = diff_snapshots(old, new, {})
        critical = [e for e in events if e["type"] == "listing_unavailable"]
        self.assertEqual(len(critical), 1)
        self.assertEqual(critical[0]["severity"], "critical")

    def test_fdroid_merge_is_critical(self):
        old = snapshot(fdroid=result("fdroid", {}, meta={"mr_state": "opened", "package_page_status": 404}))
        new = snapshot(fdroid=result("fdroid", {}, meta={"mr_state": "merged", "package_page_status": 404}))
        events = diff_snapshots(old, new, {})
        kinds = {e["type"] for e in events}
        self.assertIn("fdroid_merge", kinds)
        merge = [e for e in events if e["type"] == "fdroid_merge"][0]
        self.assertEqual(merge["severity"], "critical")

    def test_fdroid_publication_is_critical_and_not_duplicated(self):
        old = snapshot(fdroid=result("fdroid", {"package_page_status": 404},
                                     meta={"mr_state": "merged", "package_page_status": 404}))
        new = snapshot(fdroid=result("fdroid", {"package_page_status": 200},
                                     meta={"mr_state": "merged", "package_page_status": 200}))
        events = diff_snapshots(old, new, {})
        published = [e for e in events if e["type"] == "fdroid_published"]
        self.assertEqual(len(published), 1)
        generic = [e for e in events if e["type"] == "metric_change" and e["metric"] == "package_page_status"]
        self.assertEqual(generic, [])

    def test_status_change_to_error_is_high(self):
        old = snapshot(github=result("github", {"stars": 1}, status="ok"))
        new = snapshot(github=result("github", {}, status="error"))
        events = diff_snapshots(old, new, {})
        status = [e for e in events if e["type"] == "status_change"][0]
        self.assertEqual(status["severity"], "high")


class MilestoneTests(unittest.TestCase):
    CONFIG = {"milestones": [{
        "platform": "github", "metric": "apk_downloads", "label": "APK downloads",
        "unit": "downloads", "thresholds": [25, 50, 100], "major": [50]}]}

    def test_threshold_fires_once(self):
        platforms = {"github": result("github", {"apk_downloads": 60})}
        events, achieved = evaluate(platforms, self.CONFIG, {})
        keys = sorted(achieved)
        self.assertEqual(keys, ["github.apk_downloads.25", "github.apk_downloads.50"])
        self.assertEqual(len(events), 2)
        self.assertEqual([e["severity"] for e in events], ["normal", "high"])

        again, achieved_again = evaluate(platforms, self.CONFIG, achieved)
        self.assertEqual(again, [])
        self.assertEqual(achieved_again, achieved)

    def test_missing_metric_never_fires(self):
        events, achieved = evaluate({"github": result("github", {})}, self.CONFIG, {})
        self.assertEqual(events, [])
        self.assertEqual(achieved, {})

    def test_http_status_cannot_satisfy_a_threshold(self):
        # Regression: 404 must not count as "reached 200".
        config = {"milestones": [{"platform": "fdroid", "metric": "listing_live",
                                  "label": "F-Droid listing live", "thresholds": [1]}]}
        events, achieved = evaluate({"fdroid": result("fdroid", {"listing_live": 0})}, config, {})
        self.assertEqual(events, [])
        self.assertEqual(achieved, {})
        events, achieved = evaluate({"fdroid": result("fdroid", {"listing_live": 1})}, config, {})
        self.assertEqual(len(events), 1)

    def test_empty_previous_state_is_treated_as_baseline(self):
        events, _ = evaluate({"github": result("github", {"apk_downloads": 25})}, self.CONFIG, {})
        self.assertEqual(len(events), 1)


if __name__ == "__main__":
    unittest.main()
