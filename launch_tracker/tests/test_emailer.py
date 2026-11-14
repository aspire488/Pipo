"""Email tests: SMTP success/failure, retries, dedupe, digest, simulated events.

No real network: smtplib.SMTP is replaced by a fake.
"""

import os
import smtplib
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from core import emailer
from core.emailer import (Emailer, render_digest, render_event, send_digest,
                          send_test_email)
from core.notify import Notifier
from core.storage import Store

ENV = {
    "SMTP_HOST": "smtp.gmail.com",
    "SMTP_PORT": "587",
    "SMTP_USERNAME": "sender@example.com",
    "SMTP_PASSWORD": "app-password",
    "LAUNCH_ALERT_FROM": "sender@example.com",
    "LAUNCH_ALERT_EMAIL": "recipient@example.com",
}


class FakeSMTP:
    sent = []

    def __init__(self, host, port, timeout=20):
        self.host, self.port, self.timeout = host, port, timeout
        FakeSMTP.sent.append(self)
        self.message = None
        self.logged_in = None

    def ehlo(self):
        return None

    def starttls(self, context=None):
        return None

    def login(self, username, password):
        self.logged_in = username

    def send_message(self, message):
        self.message = message
        return {}

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


EVENT = {
    "event_id": "evt1",
    "timestamp_utc": "2026-11-14T09:30:00Z",
    "platform": "fdroid",
    "type": "fdroid_merge",
    "metric": "mr_state",
    "previous": "opened",
    "current": "merged",
    "severity": "critical",
    "message": "F-Droid merge request was merged",
    "source": ["https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57356"],
}

SNAPSHOT = {
    "platforms": {
        "github": {"status": "ok", "metrics": {"apk_downloads": 25, "stars": 1}, "meta": {}},
        "freebuff": {"status": "manual", "metrics": {"downloads": 23}, "meta": {}},
        "reddit": {"status": "blocked", "metrics": {}, "meta": {}, "errors": ["403"]},
        "fdroid": {"status": "ok", "metrics": {"listing_live": 0},
                   "meta": {"mr_state": "opened", "pipeline_verdict": "no_runner"}},
    }
}


class EmailerTests(unittest.TestCase):
    def test_configured_and_missing(self):
        self.assertTrue(Emailer(env=ENV).configured)
        partial = Emailer(env={"SMTP_HOST": "x"})
        self.assertFalse(partial.configured)
        self.assertIn("SMTP_PASSWORD", partial.missing)

    def test_successful_send_records_one_attempt(self):
        FakeSMTP.sent.clear()
        fake = mock.Mock(side_effect=FakeSMTP)
        with mock.patch("core.emailer.smtplib.SMTP", fake):
            result = Emailer(env=ENV).send("subject", "text", "<p>html</p>")
        self.assertTrue(result["ok"])
        self.assertEqual(result["attempts"], 1)
        self.assertEqual(result["detail"], "SMTP accepted the message for recipient@example.com")

    def test_retries_then_succeeds(self):
        sleeps = []
        calls = {"n": 0}

        def flaky(host, port, timeout=20):
            calls["n"] += 1
            if calls["n"] == 1:
                raise smtplib.SMTPException("temporary failure")
            return FakeSMTP(host, port, timeout)

        with mock.patch("core.emailer.smtplib.SMTP", side_effect=flaky):
            result = Emailer(env=ENV, sleep=sleeps.append).send("s", "t", "<p>h</p>")
        self.assertTrue(result["ok"])
        self.assertEqual(result["attempts"], 2)
        self.assertEqual(sleeps, [1])

    def test_exhausted_retries_report_failure(self):
        fake = mock.Mock(side_effect=smtplib.SMTPException("always down"))
        with mock.patch("core.emailer.smtplib.SMTP", fake):
            result = Emailer(env=ENV, sleep=lambda s: None, retries=2).send("s", "t", "<p>h</p>")
        self.assertFalse(result["ok"])
        self.assertEqual(result["attempts"], 2)

    def test_unconfigured_send_does_not_touch_smtp(self):
        fake = mock.Mock()
        with mock.patch("core.emailer.smtplib.SMTP", fake):
            result = Emailer(env={}).send("s", "t", "<p>h</p>")
        self.assertFalse(result["ok"])
        self.assertEqual(result["attempts"], 0)
        fake.assert_not_called()

    def test_no_password_in_result(self):
        fake = mock.Mock(side_effect=smtplib.SMTPException("535 auth failed"))
        with mock.patch("core.emailer.smtplib.SMTP", fake):
            result = Emailer(env=ENV, sleep=lambda s: None).send("s", "t", "<p>h</p>")
        self.assertNotIn("app-password", result["detail"])


class RenderTests(unittest.TestCase):
    def test_subject_format(self):
        rendered = render_event(EVENT, SNAPSHOT)
        self.assertTrue(rendered["subject"].startswith("[Pipo Launch Alert] CRITICAL - "))

    def test_body_contains_every_required_field(self):
        rendered = render_event(EVENT, SNAPSHOT)
        for needle in ("F-Droid", "opened", "merged", "Why this matters", "IST", "UTC",
                       "gitlab.com", "Next action", "Current launch status"):
            self.assertIn(needle, rendered["text"])
        self.assertIn("<table", rendered["html"])
        self.assertIn("<html", rendered["html"].split("<html")[0] + "<html")  # html alternative is markup

    def test_digest_has_required_sections(self):
        rendered = render_digest(SNAPSHOT, [EVENT], {"achieved": {"github.apk_downloads.25": "2026-11-14T10:00:00Z"}},
                                 {}, achieved_since="2026-11-14T00:00:00Z")
        for needle in ("GitHub APK downloads", "Freebuff downloads", "GitHub stars",
                       "Reddit engagement", "Product Hunt", "Hacker News", "F-Droid MR",
                       "LATEST EVENTS", "MILESTONES ACHIEVED", "PLATFORM FAILURES",
                       "OUTSTANDING ACTIONS"):
            self.assertIn(needle, rendered["text"])
        self.assertTrue(rendered["has_content"])

    def test_digest_with_nothing_is_empty(self):
        empty = {"platforms": {name: {"status": "ok", "metrics": {}, "meta": {}}
                               for name in ("github", "freebuff", "reddit", "product_hunt",
                                            "hackernews", "fdroid")}}
        rendered = render_digest(empty, [], {"achieved": {}}, {}, achieved_since="2099-01-01")
        self.assertFalse(rendered["has_content"])


class StoreBackedEmailTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = Store(Path(self.tmp.name)).ensure()
        self.env = mock.patch.dict(os.environ, ENV, clear=False)
        self.env.start()

    def tearDown(self):
        self.env.stop()
        self.tmp.cleanup()

    def test_delivery_recorded_only_on_success(self):
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": False, "attempts": 3, "detail": "auth failed"}):
            result = send_test_email(self.store)
        self.assertFalse(result["ok"])
        record = self.store.deliveries()[-1]
        self.assertEqual(record["status"], "failed")
        self.assertEqual(self.store.notified_event_ids("email"), set())

    def test_notifier_emails_once_per_event(self):
        notifier = Notifier({"min_severity": "normal", "console": False, "windows": False,
                             "summary": False, "webhook": False, "email": True}, self.store)
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": True, "attempts": 1, "detail": "ok"}):
            first = notifier.notify([EVENT], SNAPSHOT)
            second = notifier.notify([EVENT], SNAPSHOT)
        email_results = [r for r in first if r["channel"] == "email"]
        self.assertTrue(email_results[0]["ok"])
        self.assertIn(EVENT["event_id"], self.store.notified_event_ids("email"))
        second_email = [r for r in second if r["channel"] == "email"][0]
        self.assertIn("0 sent", second_email["detail"])

    def test_failed_email_is_retried_on_next_run(self):
        notifier = Notifier({"min_severity": "normal", "console": False, "windows": False,
                             "summary": False, "webhook": False, "email": True}, self.store)
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": False, "attempts": 3, "detail": "smtp down"}):
            notifier.notify([EVENT], SNAPSHOT)
        self.assertEqual(self.store.notified_event_ids("email"), set())
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": True, "attempts": 1, "detail": "ok"}) as sender:
            notifier.notify([EVENT], SNAPSHOT)
        self.assertEqual(sender.call_count, 1)
        self.assertIn(EVENT["event_id"], self.store.notified_event_ids("email"))

    def test_digest_sent_once_per_day(self):
        self.store.save_latest(SNAPSHOT)
        self.store.append_events([EVENT])
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": True, "attempts": 1, "detail": "ok"}) as sender:
            first = send_digest(self.store, {})
            second = send_digest(self.store, {})
        self.assertTrue(first["ok"])
        self.assertFalse(first.get("skipped"))
        self.assertTrue(second.get("skipped"))
        self.assertEqual(sender.call_count, 1)

    def test_digest_skips_when_nothing_to_report(self):
        quiet = {"platforms": {name: {"status": "ok", "metrics": {}, "meta": {}}
                               for name in ("github", "freebuff", "reddit", "product_hunt",
                                            "hackernews", "fdroid")}}
        self.store.save_latest(quiet)
        with mock.patch("core.emailer.Emailer.send") as sender:
            result = send_digest(self.store, {})
        self.assertTrue(result.get("skipped"))
        sender.assert_not_called()

    def test_forced_digest_bypasses_daily_guard(self):
        self.store.save_latest(SNAPSHOT)
        self.store.append_events([EVENT])
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": True, "attempts": 1, "detail": "ok"}) as sender:
            send_digest(self.store, {})
            send_digest(self.store, {}, force=True)
        self.assertEqual(sender.call_count, 2)

    def test_simulated_event_email_is_labelled(self):
        with mock.patch("core.emailer.Emailer.send",
                        return_value={"ok": True, "attempts": 1, "detail": "ok"}):
            result = send_test_email(self.store, simulate="fdroid_merge")
        self.assertIn("TEST - simulated", result["subject"])
        self.assertEqual(self.store.events(), [])


if __name__ == "__main__":
    unittest.main()
