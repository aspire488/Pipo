"""Notification routing.

Channels: console, Windows toast, GitHub Actions step summary, webhook.
Only material events reach a channel; the threshold is configurable and the
console can be silenced so scheduled runs stay quiet when nothing happened.

A channel that fails never raises: it returns a result dict that the caller
prints and logs. `--test-notifications` reports what actually worked.
"""

from __future__ import annotations

import json
import os
import subprocess
import urllib.request

from .model import utc_now

SEVERITY_ORDER = {"low": 0, "normal": 1, "high": 2, "critical": 3}

TOAST_LIMIT = 3


def _escape_ps(text: str) -> str:
    return str(text).replace("'", "''").replace("\r", " ").replace("\n", " ")[:220]


class Notifier:
    def __init__(self, config: dict, store, quiet: bool = False):
        self.config = config or {}
        self.store = store
        self.quiet = quiet
        self.min_severity = self.config.get("min_severity", "normal")
        self.enabled = {
            "console": bool(self.config.get("console", True)),
            "windows": bool(self.config.get("windows", True)),
            "summary": bool(self.config.get("summary", True)),
            "webhook": bool(self.config.get("webhook", True)),
            "email": bool(self.config.get("email", True)),
        }

    # -- routing ----------------------------------------------------------
    def material(self, events: list) -> list:
        threshold = SEVERITY_ORDER.get(self.min_severity, 1)
        return [e for e in events if SEVERITY_ORDER.get(e.get("severity"), 0) >= threshold]

    def notify(self, events: list, snapshot=None) -> list:
        material = self.material(events)
        if not material:
            return []
        results = []

        if self.enabled["console"] and not self.quiet:
            for event in material:
                print("[%s] %s: %s" % (event.get("severity", "").upper(), event.get("platform"), event.get("message")))
            results.append({"channel": "console", "ok": True, "detail": "%d event(s)" % len(material)})

        if self.enabled["windows"]:
            toasts = [e for e in material if SEVERITY_ORDER.get(e.get("severity"), 0) >= 2][:TOAST_LIMIT]
            if toasts:
                results.append(self._windows(toasts))
                for event in toasts:
                    self.store.log_notification("toast: %s" % event.get("message"))

        if self.enabled["summary"]:
            results.append(self._step_summary(material))

        if self.enabled["webhook"]:
            results.append(self._webhook(material))

        if self.enabled["email"]:
            results.append(self._email(material, snapshot))

        return results

    def _email(self, events: list, snapshot=None) -> dict:
        """One email per material event; only SMTP acceptance counts as delivered."""
        from .emailer import Emailer, render_event

        emailer = Emailer()
        if not emailer.configured:
            return {"channel": "email", "ok": False,
                    "detail": "not configured: missing %s" % ", ".join(emailer.missing)}
        already = self.store.notified_event_ids("email")
        sent = failed = skipped = 0
        last_error = ""
        for event in events:
            event_id = event.get("event_id")
            if not event_id or event_id in already:
                skipped += 1
                continue
            rendered = render_event(event, snapshot)
            result = emailer.send(rendered["subject"], rendered["text"], rendered["html"])
            self.store.append_delivery({
                "timestamp_utc": utc_now(),
                "event_id": event_id,
                "channel": "email",
                "status": "sent" if result.get("ok") else "failed",
                "attempts": result.get("attempts"),
                "subject": rendered["subject"],
                "error": None if result.get("ok") else result.get("detail"),
            })
            if result.get("ok"):
                sent += 1
            else:
                failed += 1
                last_error = result.get("detail") or ""
        detail = "%d sent, %d failed, %d already delivered" % (sent, failed, skipped)
        if last_error:
            detail += " (%s)" % last_error[:120]
        return {"channel": "email", "ok": failed == 0, "detail": detail}

    # -- channels ---------------------------------------------------------
    def _windows(self, events: list) -> dict:
        if os.name != "nt":
            return {"channel": "windows", "ok": False, "detail": "not Windows"}
        title = "Pipo launch: %s" % (events[0].get("severity", "normal").upper() if events else "update")
        body = " | ".join(e.get("message", "") for e in events)[:200]
        script = (
            "$ErrorActionPreference='Stop';"
            "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null;"
            "$t=[Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent("
            "[Windows.UI.Notifications.ToastTemplateType]::ToastText02);"
            "$x=$t.GetElementsByTagName('text');"
            "$x.Item(0).AppendChild($t.CreateTextNode('%s')) | Out-Null;"
            "$x.Item(1).AppendChild($t.CreateTextNode('%s')) | Out-Null;"
            "$n=[Windows.UI.Notifications.ToastNotification]::new($t);"
            "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Pipo Launch Tracker').Show($n)"
        ) % (_escape_ps(title), _escape_ps(body))
        try:
            proc = subprocess.run(
                ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
                capture_output=True, text=True, timeout=25)
        except Exception as exc:  # noqa: BLE001
            return {"channel": "windows", "ok": False, "detail": str(exc)[:200]}
        if proc.returncode == 0:
            return {"channel": "windows", "ok": True, "detail": "%d toast(s)" % len(events)}
        detail = (proc.stderr or proc.stdout or "").strip()[:200]
        self.store.log_notification("toast failed: %s" % detail)
        return {"channel": "windows", "ok": False, "detail": detail or "exit %s" % proc.returncode}

    def _step_summary(self, events: list) -> dict:
        path = os.environ.get("GITHUB_STEP_SUMMARY")
        if not path:
            return {"channel": "summary", "ok": False, "detail": "GITHUB_STEP_SUMMARY not set"}
        lines = ["### Pipo launch events", ""]
        for event in events:
            lines.append("- **%s** `%s` %s" % (event.get("severity", "").upper(), event.get("platform"), event.get("message")))
        try:
            with open(path, "a", encoding="utf-8") as handle:
                handle.write("\n".join(lines) + "\n")
        except OSError as exc:
            return {"channel": "summary", "ok": False, "detail": str(exc)[:160]}
        return {"channel": "summary", "ok": True, "detail": "%d line(s)" % len(lines)}

    def _webhook(self, events: list) -> dict:
        url = os.environ.get(self.config.get("webhook_env", "LAUNCH_WEBHOOK_URL"))
        if not url:
            return {"channel": "webhook", "ok": False, "detail": "LAUNCH_WEBHOOK_URL not set"}
        text = "**Pipo launch update**\n" + "\n".join(
            "- [%s] %s: %s" % (e.get("severity", "").upper(), e.get("platform"), e.get("message"))
            for e in events)
        payload = json.dumps({"content": text[:1900], "events": events[:10]}).encode("utf-8")
        request = urllib.request.Request(
            url, data=payload, method="POST",
            headers={"Content-Type": "application/json", "User-Agent": "PipoLaunchIntel/1.0"})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                return {"channel": "webhook", "ok": 200 <= response.status < 300,
                        "detail": "HTTP %s" % response.status}
        except Exception as exc:  # noqa: BLE001
            return {"channel": "webhook", "ok": False, "detail": str(exc)[:160]}

    # -- self test --------------------------------------------------------
    def test(self) -> list:
        event = {
            "event_id": "test",
            "timestamp_utc": None,
            "platform": "tracker",
            "type": "test",
            "metric": "notification",
            "severity": "high",
            "message": "Pipo launch tracker notification test",
            "source": ["https://github.com/aspire488/Pipo"],
        }
        results = []
        if self.enabled["console"]:
            results.append({"channel": "console", "ok": True, "detail": "configured"})
        if self.enabled["windows"]:
            results.append(self._windows([event]))
        if self.enabled["summary"]:
            results.append(self._step_summary([event]))
        if self.enabled["webhook"]:
            results.append(self._webhook([event]))
        if self.enabled["email"]:
            from .emailer import Emailer, test_email_body

            emailer = Emailer()
            if not emailer.configured:
                results.append({"channel": "email", "ok": False,
                                "detail": "missing %s" % ", ".join(emailer.missing)})
            else:
                body = test_email_body()
                outcome = emailer.send(body["subject"], body["text"], body["html"])
                results.append({"channel": "email", "ok": bool(outcome.get("ok")),
                                "detail": outcome.get("detail", "")})
        return results
