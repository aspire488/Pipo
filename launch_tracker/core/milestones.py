"""Milestone engine.

Each platform/threshold pair fires exactly once, ever. Achieved milestones are
persisted in data/milestones.json, so a later dip and recovery cannot re-fire.
"""

from __future__ import annotations

from .changes import make_event
from .model import utc_now


def _number(value):
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def evaluate(platforms: dict, config: dict, achieved: dict) -> tuple:
    """Return (new_events, updated_achieved_map). Missing metrics are skipped."""
    events: list = []
    updated = dict(achieved or {})
    for spec in config.get("milestones") or []:
        platform = spec.get("platform")
        metric = spec.get("metric")
        if not platform or not metric:
            continue
        result = platforms.get(platform) or {}
        value = _number((result.get("metrics") or {}).get(metric))
        if value is None:
            continue
        majors = set(spec.get("major") or [])
        unit = spec.get("unit") or ""
        label = spec.get("label") or "%s %s" % (platform, metric.replace("_", " "))
        for threshold in spec.get("thresholds") or []:
            limit = _number(threshold)
            if limit is None or value < limit:
                continue
            key = "%s.%s.%s" % (platform, metric, threshold)
            if key in updated:
                continue
            updated[key] = utc_now()
            shown = int(limit) if float(limit).is_integer() else limit
            events.append(make_event(
                platform, "milestone", key, None, shown,
                ("%s reached %s %s" % (label, shown, unit)).strip(),
                "high" if shown in majors else "normal",
                result.get("source") or []))
    return events, updated
