"""Report generation and notification routing tests."""

import os
import unittest
from unittest import mock

from core.notify import Notifier
from core.report import build_report


def platform(name, status="ok", metrics=None, meta=None, source=None, errors=None, notes=None):
    return {
        "platform": name, "status": status, "metrics": metrics or {}, "meta": meta or {},
        "source": source or ["https://example.test/%s" % name],
        "errors": errors or [], "notes": notes or [],
        "collected_at": "2026-11-14T09:00:00Z",
    }


SNAPSHOT = {
    "schema_version": 2,
    "timestamp_utc": "2026-11-14T10:00:00Z",
    "platforms": {
        "github": platform("github", metrics={"stars": 1, "apk_downloads": 25, "forks": 0},
                           meta={"latest_release_tag": "v1.0"}),
        "fdroid": platform("fdroid", metrics={"pipeline_jobs": 0},
                           meta={"mr_state": "opened", "package_page_status": 404,
                                 "pipeline_verdict": "no_runner", "mr_url": "https://gitlab.com/mr"}),
        "freebuff": platform("freebuff", "manual", metrics={"downloads": 23},
                             meta={"observation_age_days": 1}),
        "product_hunt": platform("product_hunt", "auth_required", errors=["PRODUCT_HUNT_TOKEN not set"]),
    },
}

CONFIG = {
    "definitions": {"apk_downloads": "GitHub release APK asset downloads. Downloads, not installs.",
                    "default": "Platform value."},
    "milestones": [{"platform": "github", "metric": "apk_downloads", "label": "GitHub APK downloads",
                    "unit": "downloads", "thresholds": [25, 50], "major": [50]}],
    "product_hunt": {"env": ["PRODUCT_HUNT_TOKEN"]},
}

EVENTS = [{
    "timestamp_utc": "2026-11-14T09:30:00Z", "platform": "github", "type": "milestone",
    "severity": "normal", "message": "GitHub APK downloads reached 25 downloads",
    "event_id": "x",
}]


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.report = build_report(SNAPSHOT, None, EVENTS, {"achieved": {"github.apk_downloads.25": "2026-11-14"}},
                                   CONFIG, history=[])

    def test_has_required_sections(self):
        for heading in ("## Executive overview", "## Distribution", "## Community traction",
                        "## Growth", "## Launch timeline", "## Issues requiring attention",
                        "## Milestones", "## Next actions", "## Sources"):
            self.assertIn(heading, self.report)

    def test_missing_values_render_as_dashes(self):
        self.assertIn("Product Hunt votes | --", self.report)
        self.assertNotIn("None", self.report)

    def test_aggregate_is_labelled_non_deduplicated(self):
        self.assertIn("non-deduplicated", self.report)
        self.assertIn("48", self.report)

    def test_definitions_and_sources_present(self):
        self.assertIn("Downloads, not installs", self.report)
        self.assertIn("https://example.test/github", self.report)

    def test_auth_required_is_reported_with_env_hint(self):
        self.assertIn("PRODUCT_HUNT_TOKEN", self.report)

    def test_event_timeline_included(self):
        self.assertIn("reached 25 downloads", self.report)

    def test_milestone_state_shown(self):
        self.assertIn("reached 2026-11-14", self.report)


class NotifyTests(unittest.TestCase):
    class Store:
        def log_notification(self, line):
            pass

    def notifier(self, **overrides):
        config = {"min_severity": "normal", "console": False, "windows": False,
                  "summary": False, "webhook": False}
        config.update(overrides)
        return Notifier(config, self.Store())

    def test_low_severity_is_filtered(self):
        events = [{"severity": "low", "platform": "github", "message": "x"}]
        self.assertEqual(self.notifier().material(events), [])
        self.assertEqual(self.notifier().notify(events), [])

    def test_material_events_pass_threshold(self):
        events = [{"severity": "critical", "platform": "fdroid", "message": "merged"}]
        self.assertEqual(len(self.notifier().material(events)), 1)

    def test_min_severity_configurable(self):
        events = [{"severity": "low", "platform": "x", "message": "x"}]
        self.assertEqual(len(self.notifier(min_severity="low").material(events)), 1)

    def test_failing_webhook_does_not_raise(self):
        notifier = self.notifier(webhook=True)
        with mock.patch.dict(os.environ, {"LAUNCH_WEBHOOK_URL": "https://127.0.0.1:9/none"}):
            result = notifier._webhook([{"severity": "high", "platform": "x", "message": "y"}])
        self.assertFalse(result["ok"])
        self.assertEqual(result["channel"], "webhook")

    def test_summary_without_actions_env_is_reported_not_crashing(self):
        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("GITHUB_STEP_SUMMARY", None)
            result = self.notifier()._step_summary([])
        self.assertFalse(result["ok"])
        self.assertIn("GITHUB_STEP_SUMMARY", result["detail"])

    def test_test_command_reports_console(self):
        results = self.notifier(console=True).test()
        self.assertEqual(results[0]["channel"], "console")
        self.assertTrue(results[0]["ok"])


if __name__ == "__main__":
    unittest.main()
