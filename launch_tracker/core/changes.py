"""Deterministic change detection across two snapshots.

Rules:
* No previous snapshot -> no events (first run is a baseline, not a change).
* A metric that disappears produces `metric_missing`, never a drop to zero.
* Event ids are content-derived, so re-running on unchanged data cannot duplicate.
"""

from __future__ import annotations

import hashlib

from .model import as_source, utc_now

TYPE_SEVERITY = {
    "listing_unavailable": "critical",
    "fdroid_merge": "critical",
    "fdroid_published": "critical",
    "adapter_failure": "high",
    "new_external_contribution": "high",
    "new_review": "high",
    "new_release": "normal",
    "ci_passed": "normal",
    "metric_change": "low",
    "metric_appeared": "low",
    "metric_missing": "low",
}

# Keys with a dedicated rule, so the generic metric diff does not duplicate them.
_SPECIAL = {("fdroid", "package_page_status"), ("fdroid", "mr_state"), ("fdroid", "listing_live")}


def make_event(platform: str, event_type: str, metric: str, previous, current,
               message: str, severity: str, source=None) -> dict:
    seed = "%s|%s|%s|%s|%s" % (platform, event_type, metric, previous, current)
    return {
        "event_id": hashlib.sha1(seed.encode("utf-8")).hexdigest()[:16],
        "timestamp_utc": utc_now(),
        "platform": platform,
        "type": event_type,
        "metric": metric,
        "previous": previous,
        "current": current,
        "severity": severity,
        "message": message,
        "source": as_source(source),
    }


def _fmt(value) -> str:
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    if isinstance(value, int):
        return "{:,}".format(value)
    if isinstance(value, float):
        return ("%.2f" % value).rstrip("0").rstrip(".")
    return str(value)


def diff_snapshots(previous, current, config=None) -> list:
    config = config or {}
    if not previous or not isinstance(previous.get("platforms"), dict):
        return []
    prior = previous["platforms"]
    watch = config.get("meta_watch") or {}
    severity_map = config.get("severity") or {}
    events: list = []

    for name, result in (current.get("platforms") or {}).items():
        old = prior.get(name)
        if not isinstance(old, dict):
            continue
        source = result.get("source") or []

        old_status, new_status = old.get("status"), result.get("status")
        if old_status != new_status and new_status not in (None, "skipped"):
            severity = "high" if new_status in ("error", "blocked") else "normal"
            if old_status in ("error", "blocked", "auth_required") and new_status in ("ok", "partial", "manual"):
                severity = "normal"
            events.append(make_event(
                name, "status_change", "status", old_status, new_status,
                "%s adapter: %s -> %s" % (name, old_status, new_status), severity, source))

        old_metrics = old.get("metrics") or {}
        new_metrics = result.get("metrics") or {}
        for key in sorted(set(old_metrics) | set(new_metrics)):
            if (name, key) in _SPECIAL:
                continue
            before, after = old_metrics.get(key), new_metrics.get(key)
            if before == after:
                continue
            if after is None:
                events.append(make_event(
                    name, "metric_missing", key, before, None,
                    "%s stopped reporting %s (unavailable, not zero)" % (name, key), "low", source))
                continue
            if before is None:
                events.append(make_event(
                    name, "metric_appeared", key, None, after,
                    "%s started reporting %s: %s" % (name, key, _fmt(after)), "low", source))
                continue
            severity = severity_map.get("%s.%s" % (name, key)) or severity_map.get(key) or "low"
            direction = "up" if float(after) > float(before) else "down"
            events.append(make_event(
                name, "metric_change", key, before, after,
                "%s %s %s: %s -> %s" % (name, key.replace("_", " "), direction, _fmt(before), _fmt(after)),
                severity, source))

        for rule in watch.get(name) or []:
            key = rule.get("key")
            if not key:
                continue
            before = (old.get("meta") or {}).get(key)
            after = (result.get("meta") or {}).get(key)
            if before == after:
                continue
            template = rule.get("message") or ("%s %s: {previous} -> {current}" % (name, key))
            message = template.format(previous=before, current=after, platform=name, key=key)
            events.append(make_event(
                name, rule.get("type") or "meta_change", key, before, after,
                message, rule.get("severity") or TYPE_SEVERITY.get(rule.get("type"), "normal"), source))

    events.extend(_fdroid_rules(prior, current))
    events.extend(_availability_rules(prior, current))
    return events


def _fdroid_rules(prior: dict, current: dict) -> list:
    old = prior.get("fdroid")
    new = (current.get("platforms") or {}).get("fdroid")
    if not isinstance(old, dict) or not isinstance(new, dict):
        return []
    source = new.get("source") or []
    before = old.get("meta") or {}
    after = new.get("meta") or {}
    out = []

    if before.get("mr_state") != "merged" and after.get("mr_state") == "merged":
        out.append(make_event(
            "fdroid", "fdroid_merge", "mr_state", before.get("mr_state"), "merged",
            "F-Droid merge request was merged", "critical", source))
    if before.get("package_page_status") != 200 and after.get("package_page_status") == 200:
        out.append(make_event(
            "fdroid", "fdroid_published", "package_page_status",
            before.get("package_page_status"), 200,
            "Pipo is now listed and installable from f-droid.org", "critical", source))
    if before.get("pipeline_verdict") != after.get("pipeline_verdict") and after.get("pipeline_verdict") == "success":
        out.append(make_event(
            "fdroid", "ci_passed", "pipeline_verdict", before.get("pipeline_verdict"), "success",
            "The F-Droid CI pipeline passed", "normal", source))
    return out


def _availability_rules(prior: dict, current: dict) -> list:
    old = prior.get("website")
    new = (current.get("platforms") or {}).get("website")
    if not isinstance(old, dict) or not isinstance(new, dict):
        return []
    before = (old.get("meta") or {}).get("surfaces") or {}
    after = (new.get("meta") or {}).get("surfaces") or {}
    out = []
    for url, status in after.items():
        prior_status = before.get(url)
        if prior_status is None or prior_status == status:
            continue
        if prior_status == 200 and isinstance(status, int) and (status >= 400 or status == 0):
            out.append(make_event(
                "website", "listing_unavailable", url, prior_status, status,
                "A public surface stopped responding: %s (HTTP %s)" % (url, status),
                "critical", [url]))
    return out
