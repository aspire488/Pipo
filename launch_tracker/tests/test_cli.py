"""CLI tests: argument parsing, config validity, unknown-platform handling."""

import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("pipo_launch_cli", ROOT / "launch_tracker.py")
CLI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CLI)


class CliTests(unittest.TestCase):
    def test_platform_is_repeatable(self):
        args = CLI.build_parser().parse_args(["--platform", "github", "--platform", "fdroid"])
        self.assertEqual(args.platform, ["github", "fdroid"])

    def test_schedule_defaults_to_six_hours(self):
        args = CLI.build_parser().parse_args(["--schedule"])
        self.assertEqual(args.schedule, 6)

    def test_report_flag_does_not_collect(self):
        args = CLI.build_parser().parse_args(["--report"])
        self.assertTrue(args.report_only)
        self.assertEqual(args.platform, [])

    def test_unknown_platform_exits_nonzero(self):
        self.assertEqual(CLI.main(["--platform", "myspace"]), 1)

    def test_quiet_events_history_parse(self):
        args = CLI.build_parser().parse_args(["--quiet", "--events", "--history", "5"])
        self.assertTrue(args.quiet)
        self.assertEqual(args.events_n, 20)
        self.assertEqual(args.history_n, 5)

    def test_email_flags_parse(self):
        args = CLI.build_parser().parse_args(["--test-email", "--simulate-event", "fdroid_merge"])
        self.assertTrue(args.test_email)
        self.assertEqual(args.simulate_event, "fdroid_merge")
        digest = CLI.build_parser().parse_args(["--digest"])
        self.assertTrue(digest.digest)
        self.assertFalse(digest.force_digest)
        self.assertFalse(CLI.build_parser().parse_args(["--all"]).no_email)

    def test_config_is_valid_and_complete(self):
        config = CLI.load_config()
        self.assertIn("github", config)
        self.assertIn("fdroid", config)
        self.assertIn("milestones", config)
        self.assertTrue(any(m["metric"] == "apk_downloads" for m in config["milestones"]))
        notifications = config["notifications"]
        self.assertTrue(notifications["email"])
        self.assertEqual(notifications["digest"]["hour_ist"], 9)
        json.dumps(config)  # must be serialisable

    def test_adapter_registry_matches_config(self):
        for name in ("github", "fdroid", "reddit", "hackernews", "product_hunt", "freebuff", "xerj", "website"):
            self.assertIsNotNone(CLI.adapters.get(name), name)


if __name__ == "__main__":
    unittest.main()
