"""Email alerts: SMTP delivery, retries, plain-text + HTML, IST timestamps.

Configuration comes only from the environment, never from source:

    SMTP_HOST          default smtp.gmail.com
    SMTP_PORT          default 587 (STARTTLS)
    SMTP_USERNAME      SMTP account
    SMTP_PASSWORD      app password / provider token
    LAUNCH_ALERT_FROM  sender address (falls back to SMTP_USERNAME)
    LAUNCH_ALERT_EMAIL recipient (comma-separated allowed)

No password is ever logged. Delivery is only reported as successful when the
SMTP server accepted the message for the recipient.
"""

from __future__ import annotations

import html as html_lib
import os
import smtplib
import ssl
import time
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from email.utils import formatdate

IST = timezone(timedelta(hours=5, minutes=30))

STATUS_LABEL = {
    "ok": "measured",
    "partial": "partial",
    "manual": "manual observation",
    "blocked": "blocked by platform",
    "auth_required": "needs credentials",
    "unsupported": "unsupported",
    "error": "error",
    "skipped": "disabled",
}

TYPE_TITLE = {
    "fdroid_merge": "F-Droid merge request merged",
    "fdroid_published": "Pipo is live on F-Droid",
    "fdroid_published_listing": "Pipo is live on F-Droid",
    "listing_unavailable": "A public listing stopped responding",
    "milestone": "Launch milestone reached",
    "new_release": "New GitHub release",
    "status_change": "Platform status changed",
    "metric_change": "Launch metric changed",
    "metric_missing": "A platform stopped reporting a metric",
    "ci_passed": "F-Droid CI passed",
    "adapter_failure": "A platform adapter failed",
}

WHY = {
    "fdroid_merge": "The merge is the gate to a permanent, compounding F-Droid listing.",
    "fdroid_published": "F-Droid is now a durable distribution channel with its own discovery.",
    "listing_unavailable": "A listing that used to work is gone; that is lost distribution, not a dip.",
    "milestone": "A first threshold was crossed, which is the only public proof of outside interest so far.",
    "new_release": "A new version extends the update path for everyone already on Pipo.",
    "status_change": "A data source changed state, so the numbers may be incomplete.",
    "ci_passed": "F-Droid's own pipeline validated the submission upstream.",
}

NEXT = {
    "fdroid_merge": "Follow the publication build and confirm the package page returns 200.",
    "fdroid_published": "Update the README/itch pages to point at F-Droid and mention the re-signing caveat.",
    "listing_unavailable": "Open the URL, check for a platform policy change, and restore or replace the listing.",
    "milestone": "Record the date in the launch log and keep the posting cadence honest.",
    "new_release": "Publish release notes and check that existing installs can update.",
    "ci_passed": "Check for reviewer comments and answer them.",
}


def ist_now() -> datetime:
    return datetime.now(IST)


def fmt_ist(value=None) -> str:
    dt = value or ist_now()
    return dt.astimezone(IST).strftime("%d %b %Y, %H:%M IST")


def fmt_utc(value=None) -> str:
    dt = value or datetime.now(timezone.utc)
    return dt.astimezone(timezone.utc).strftime("%d %b %Y, %H:%M UTC")


def _parse(value):
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


def _clean(value) -> str:
    if value is None:
        return "not reported"
    return str(value)


class Emailer:
    """SMTP sender. Returns result dicts; never raises, never prints secrets."""

    def __init__(self, env=None, timeout: int = 20, retries: int = 3, sleep=time.sleep):
        env = os.environ if env is None else env
        self.host = env.get("SMTP_HOST") or "smtp.gmail.com"
        try:
            self.port = int(env.get("SMTP_PORT") or 587)
        except ValueError:
            self.port = 587
        self.username = env.get("SMTP_USERNAME") or ""
        self.password = env.get("SMTP_PASSWORD") or ""
        self.sender = env.get("LAUNCH_ALERT_FROM") or self.username
        self.recipient = env.get("LAUNCH_ALERT_EMAIL") or ""
        self.timeout = timeout
        self.retries = max(1, retries)
        self._sleep = sleep

    @property
    def missing(self) -> list:
        required = {
            "SMTP_PASSWORD": self.password,
            "SMTP_USERNAME": self.username,
            "LAUNCH_ALERT_FROM": self.sender,
            "LAUNCH_ALERT_EMAIL": self.recipient,
        }
        return [name for name, value in required.items() if not value]

    @property
    def configured(self) -> bool:
        return not self.missing

    def _message(self, subject: str, text: str, html_body: str) -> EmailMessage:
        message = EmailMessage()
        message["Subject"] = subject
        message["From"] = self.sender
        message["To"] = self.recipient
        message["Date"] = formatdate(localtime=False)
        message.set_content(text)
        message.add_alternative(html_body, subtype="html")
        return message

    def send(self, subject: str, text: str, html_body: str) -> dict:
        if not self.configured:
            return {"ok": False, "attempts": 0,
                    "detail": "email not configured: missing %s" % ", ".join(self.missing)}
        message = self._message(subject, text, html_body)
        last_error = "unknown error"
        for attempt in range(1, self.retries + 1):
            try:
                context = ssl.create_default_context()
                with smtplib.SMTP(self.host, self.port, timeout=self.timeout) as smtp:
                    smtp.ehlo()
                    smtp.starttls(context=context)
                    smtp.ehlo()
                    if self.username:
                        smtp.login(self.username, self.password)
                    refused = smtp.send_message(message)
                if refused:
                    last_error = "recipient refused: %s" % ", ".join(refused)
                else:
                    return {"ok": True, "attempts": attempt,
                            "detail": "SMTP accepted the message for %s" % self.recipient}
            except (smtplib.SMTPException, OSError, ssl.SSLError) as exc:
                last_error = str(exc)[:200]
            if attempt < self.retries:
                self._sleep(min(8, 2 ** (attempt - 1)))
        return {"ok": False, "attempts": self.retries, "detail": last_error}


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------


def status_rows(snapshot) -> list:
    platforms = (snapshot or {}).get("platforms") or {}
    rows = []
    for name in ("github", "fdroid", "reddit", "hackernews", "product_hunt", "freebuff", "website", "xerj"):
        result = platforms.get(name)
        if not result:
            continue
        metrics = result.get("metrics") or {}
        rows.append([
            name.replace("_", " ").title(),
            STATUS_LABEL.get(result.get("status"), result.get("status") or "?"),
            "APK %s / stars %s" % (metrics.get("apk_downloads", "--"), metrics.get("stars", "--"))
            if name == "github" else
            ("downloads %s" % metrics.get("downloads", "--")) if name == "freebuff" else
            ("score %s / comments %s" % (metrics.get("score", "--"), metrics.get("comments", "--")))
            if name == "reddit" else
            ("MR %s" % ((result.get("meta") or {}).get("mr_state") or "--")) if name == "fdroid" else "",
        ])
    return rows


def _plain_status(snapshot) -> str:
    lines = []
    for row in status_rows(snapshot):
        line = "  - %s: %s" % (row[0], row[1])
        if row[2]:
            line += " (%s)" % row[2]
        lines.append(line)
    return "\n".join(lines) or "  - no snapshot available"


def _html_table(rows, headers) -> str:
    head = "".join("<th align='left'>%s</th>" % html_lib.escape(str(h)) for h in headers)
    body = "".join(
        "<tr>%s</tr>" % "".join("<td>%s</td>" % html_lib.escape(str(cell)) for cell in row)
        for row in rows)
    return ("<table cellpadding='6' cellspacing='0' style='border-collapse:collapse;"
            "font-family:Arial,sans-serif;font-size:14px'>"
            "<tr style='background:#f2f2f2'>%s</tr>%s</table>" % (head, body))


def event_subject(event) -> str:
    severity = (event.get("severity") or "normal").upper()
    title = TYPE_TITLE.get(event.get("type")) or event.get("message") or "launch update"
    title = title if len(title) <= 70 else title[:67] + "..."
    return "[Pipo Launch Alert] %s - %s" % (severity, title)


def render_event(event, snapshot=None) -> dict:
    severity = (event.get("severity") or "normal").upper()
    event_type = event.get("type") or "update"
    when = _parse(event.get("timestamp_utc"))
    source = event.get("source") or []
    source_url = source[0] if source else "https://github.com/aspire488/Pipo"
    why = WHY.get(event_type) or "This changed a signal the launch plan depends on."
    action = NEXT.get(event_type) or "Check the source link and confirm the change is real."

    subject = event_subject(event)
    platform = event.get("platform") or "unknown"
    text = "\n".join([
        "Pipo has a launch update.",
        "",
        "What happened: %s" % (event.get("message") or event_type),
        "Platform: %s" % platform,
        "Previous value: %s" % _clean(event.get("previous")),
        "New value: %s" % _clean(event.get("current")),
        "Why this matters: %s" % why,
        "When: %s (%s)" % (fmt_ist(when), fmt_utc(when)),
        "Source: %s" % source_url,
        "Next action: %s" % action,
        "",
        "Current launch status:",
        _plain_status(snapshot),
        "",
        "Severity: %s" % severity,
        "Generated by the Pipo launch tracker.",
    ])

    html_body = (
        "<div style='font-family:Arial,sans-serif;font-size:14px;color:#222;max-width:640px'>"
        "<h2 style='margin:0 0 4px'>Pipo launch update</h2>"
        "<p style='margin:0 0 16px;color:#666'>Severity: <strong>%s</strong></p>"
        "<p><strong>What happened:</strong> %s</p>"
        "<p><strong>Platform:</strong> %s<br>"
        "<strong>Previous:</strong> %s<br>"
        "<strong>New:</strong> %s</p>"
        "<p><strong>Why this matters:</strong> %s</p>"
        "<p><strong>When:</strong> %s (%s)<br>"
        "<strong>Source:</strong> <a href='%s'>%s</a></p>"
        "<p style='background:#fff8e1;padding:10px'><strong>Next action:</strong> %s</p>"
        "<h3>Current launch status</h3>%s"
        "<p style='color:#888;font-size:12px'>Generated by the Pipo launch tracker.</p></div>"
    ) % (
        html_lib.escape(severity), html_lib.escape(str(event.get("message") or event_type)),
        html_lib.escape(str(platform)), html_lib.escape(_clean(event.get("previous"))),
        html_lib.escape(_clean(event.get("current"))), html_lib.escape(why),
        html_lib.escape(fmt_ist(when)), html_lib.escape(fmt_utc(when)),
        html_lib.escape(source_url), html_lib.escape(source_url), html_lib.escape(action),
        _html_table(status_rows(snapshot), ["Platform", "Status", "Signal"]) if snapshot else "",
    )
    return {"subject": subject, "text": text, "html": html_body, "severity": severity,
            "source": source_url, "next_action": action}


def digest_subject(events, attention) -> str:
    day = ist_now().strftime("%d %b %Y")
    bits = []
    if events:
        bits.append("%d event%s" % (len(events), "" if len(events) == 1 else "s"))
    if attention:
        bits.append("%d needing attention" % len(attention))
    return "[Pipo Launch Digest] %s - %s" % (day, ", ".join(bits) or "status")


def render_digest(snapshot, events, milestones_state, config, achieved_since=None) -> dict:
    platforms = (snapshot or {}).get("platforms") or {}
    status = platforms.get("github", {})
    metrics = status.get("metrics") or {}
    freebuff = (platforms.get("freebuff") or {}).get("metrics") or {}
    reddit = (platforms.get("reddit") or {})
    ph = (platforms.get("product_hunt") or {})
    hn = (platforms.get("hackernews") or {}).get("metrics") or {}
    fdroid = platforms.get("fdroid", {})
    fd_meta = fdroid.get("meta") or {}

    attention = [name for name, result in platforms.items()
                 if result.get("status") in ("error", "blocked", "auth_required")]
    achieved = (milestones_state or {}).get("achieved") or {}
    recent = {key: when for key, when in achieved.items()
              if achieved_since is None or (when or "") >= achieved_since}
    actions = []
    if fd_meta.get("pipeline_verdict") == "no_runner":
        actions.append("Ask a maintainer to run F-Droid CI upstream (fork pipeline never reached a runner).")
    if fd_meta.get("mr_state") == "opened" and not fd_meta.get("labels"):
        actions.append("Apply the New App label on the F-Droid MR.")
    if isinstance(metrics.get("stars"), (int, float)) and metrics["stars"] < 10:
        actions.append("GitHub stars %s/10 - one FOSS community post is the next step." % int(metrics["stars"]))
    if "product_hunt" in attention:
        actions.append("Product Hunt needs PRODUCT_HUNT_TOKEN for votes/comments.")
    if "reddit" in attention:
        actions.append("Reddit returned 403 - configure REDDIT_CLIENT_ID/SECRET.")

    rows = [
        ["GitHub APK downloads", metrics.get("apk_downloads", "--")],
        ["Freebuff downloads", freebuff.get("downloads", "--")],
        ["GitHub stars", metrics.get("stars", "--")],
        ["Reddit engagement", "score %s / comments %s" % (
            ((reddit.get("metrics") or {}).get("score", "--")),
            ((reddit.get("metrics") or {}).get("comments", "--")))],
        ["Product Hunt", "%s / votes %s" % (STATUS_LABEL.get(ph.get("status"), ph.get("status") or "--"),
                                            (ph.get("metrics") or {}).get("votes", "--"))],
        ["Hacker News", "points %s / comments %s" % (hn.get("points", "--"), hn.get("comments", "--"))],
        ["F-Droid MR", "%s / pipeline %s / page %s" % (
            fd_meta.get("mr_state", "--"), fd_meta.get("pipeline_verdict", "--"),
            fd_meta.get("package_page_status", "--"))],
    ]

    subject = digest_subject(events, attention)
    text_lines = [
        "Pipo launch digest - %s" % ist_now().strftime("%d %b %Y"),
        "",
        "KEY NUMBERS",
    ] + ["  - %s: %s" % (row[0], row[1]) for row in rows] + [
        "",
        "LATEST EVENTS (%d)" % len(events),
    ] + (["  - [%s] %s" % (e.get("severity", "").upper(), e.get("message", "")) for e in events[-8:]]
         or ["  - no material changes in this period"]) + [
        "",
        "MILESTONES ACHIEVED (%d)" % len(recent),
    ] + (["  - %s on %s" % (key, (when or "")[:10]) for key, when in sorted(recent.items())]
         or ["  - none new"]) + [
        "",
        "PLATFORM FAILURES",
    ] + (["  - %s: %s" % (name, STATUS_LABEL.get(platforms[name].get("status"), "?")) for name in attention]
         or ["  - none"]) + [
        "",
        "OUTSTANDING ACTIONS",
    ] + (["  - %s" % item for item in actions] or ["  - none"]) + [
        "",
        "Report: launch_tracker/reports/PIPO_LAUNCH_REPORT.md",
    ]

    html_body = (
        "<div style='font-family:Arial,sans-serif;font-size:14px;color:#222;max-width:660px'>"
        "<h2 style='margin:0 0 4px'>Pipo launch digest</h2>"
        "<p style='color:#666;margin:0 0 16px'>%s</p>"
        "<h3>Key numbers</h3>%s"
        "<h3>Latest events</h3>%s"
        "<h3>Milestones achieved</h3>%s"
        "<h3>Platform failures</h3>%s"
        "<h3>Outstanding actions</h3>%s"
        "</div>"
    ) % (
        html_lib.escape(ist_now().strftime("%d %b %Y, %H:%M IST")),
        _html_table(rows, ["Metric", "Value"]),
        _html_table([[e.get("timestamp_utc", "")[:16].replace("T", " "), (e.get("severity") or "").upper(),
                      e.get("platform", ""), e.get("message", "")] for e in events[-8:]],
                    ["When (UTC)", "Severity", "Platform", "Event"]) if events else "<p>No material changes.</p>",
        _html_table([[key, (when or "")[:10]] for key, when in sorted(recent.items())],
                    ["Milestone", "Reached"]) if recent else "<p>None new.</p>",
        _html_table([[name, STATUS_LABEL.get(platforms[name].get("status"), "?")] for name in attention],
                    ["Platform", "Status"]) if attention else "<p>None.</p>",
        "<ul>%s</ul>" % "".join("<li>%s</li>" % html_lib.escape(item) for item in actions)
        if actions else "<p>None.</p>",
    )
    return {"subject": subject, "text": "\n".join(text_lines), "html": html_body,
            "has_content": bool(events or attention or recent or actions)}


def test_email_body() -> dict:
    event = {
        "event_id": "test-email",
        "timestamp_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "platform": "tracker",
        "type": "test",
        "metric": "email",
        "severity": "high",
        "message": "Test email from the Pipo launch tracker",
        "source": ["https://github.com/aspire488/Pipo"],
    }
    rendered = render_event(event)
    rendered["subject"] = "[Pipo Launch Alert] TEST - SMTP delivery check"
    return rendered


# ---------------------------------------------------------------------------
# Sending helpers used by the CLI and the workflow
# ---------------------------------------------------------------------------

_DIGEST_SEVERITIES = {"normal", "high", "critical"}


def _now_utc() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def send_test_email(store, simulate: str | None = None) -> dict:
    """Send a real email. `simulate` renders a launch event without recording it."""
    if simulate:
        simulated = {
            "event_id": "test-simulated-%s" % simulate,
            "timestamp_utc": _now_utc(),
            "platform": "fdroid" if simulate.startswith("fdroid") else "github",
            "type": simulate,
            "metric": "simulated",
            "severity": "critical",
            "previous": "opened",
            "current": "merged",
            "message": "SIMULATED EVENT - %s (not a real launch event)" % (
                TYPE_TITLE.get(simulate) or simulate),
            "source": ["https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57356"],
        }
        rendered = render_event(simulated, store.load_latest())
        rendered["subject"] = "[Pipo Launch Alert] TEST - simulated %s" % (
            TYPE_TITLE.get(simulate) or simulate)
    else:
        rendered = test_email_body()

    result = Emailer().send(rendered["subject"], rendered["text"], rendered["html"])
    store.append_delivery({
        "timestamp_utc": _now_utc(),
        "event_id": rendered["subject"],
        "channel": "email",
        "status": "sent" if result.get("ok") else "failed",
        "attempts": result.get("attempts"),
        "subject": rendered["subject"],
        "error": None if result.get("ok") else result.get("detail"),
    })
    return dict(result, subject=rendered["subject"])


def send_digest(store, config: dict, force: bool = False) -> dict:
    """Send the daily digest at most once per IST day unless forced."""
    snapshot = store.load_latest()
    if snapshot is None:
        return {"ok": False, "detail": "no snapshot yet: run --all first"}

    state = store.load_digest_state()
    today = ist_now().strftime("%Y-%m-%d")
    if not force and state.get("last_date") == today:
        return {"ok": True, "skipped": True,
                "detail": "digest already sent today (%s)" % today}

    since = state.get("last_sent") or (
        datetime.now(timezone.utc) - timedelta(hours=24)).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    events = [event for event in store.events()
              if (event.get("timestamp_utc") or "") >= since
              and event.get("severity") in _DIGEST_SEVERITIES]
    rendered = render_digest(snapshot, events, store.load_milestones(), config, achieved_since=since)
    if not rendered["has_content"] and not force:
        return {"ok": True, "skipped": True, "detail": "nothing to report; no digest sent"}

    emailer = Emailer()
    result = emailer.send(rendered["subject"], rendered["text"], rendered["html"])
    store.append_delivery({
        "timestamp_utc": _now_utc(),
        "event_id": "digest:%s" % today,
        "channel": "email",
        "status": "sent" if result.get("ok") else "failed",
        "attempts": result.get("attempts"),
        "subject": rendered["subject"],
        "error": None if result.get("ok") else result.get("detail"),
    })
    if result.get("ok"):
        store.save_digest_state({"last_sent": _now_utc(), "last_date": today,
                                 "subject": rendered["subject"]})
    return dict(result, subject=rendered["subject"], events=len(events))
