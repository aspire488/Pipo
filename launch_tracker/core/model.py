"""Normalized platform result model.

Every adapter returns the same shape. Metrics are numbers only, and a missing
measurement is an ABSENT KEY -- never 0. That rule is what keeps the report
from inventing data when a platform is unreachable or needs credentials.
"""

from __future__ import annotations

import math
from datetime import datetime, timezone

PLATFORM_STATUSES = {
    "ok",            # measured successfully
    "partial",       # some metrics measured, some unavailable
    "manual",        # value came from a human observation, not an API
    "blocked",       # platform actively refused the request (403/challenge)
    "auth_required", # needs credentials we do not have
    "unsupported",   # no verifiable source exists at all
    "error",         # we tried and failed
    "skipped",       # disabled by configuration
}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_iso(value):
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None
    # Date-only strings parse as naive; treat them as UTC so arithmetic is safe.
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed


def age_hours(value) -> float | None:
    dt = parse_iso(value)
    if dt is None:
        return None
    return round((datetime.now(timezone.utc) - dt).total_seconds() / 3600.0, 1)


def clean_metrics(raw: dict | None) -> dict:
    """Keep finite numbers, drop everything else. Booleans are not metrics."""
    out = {}
    for key, value in (raw or {}).items():
        if value is None or isinstance(value, bool):
            continue
        try:
            number = float(value)
        except (TypeError, ValueError):
            continue
        if math.isnan(number) or math.isinf(number):
            continue
        out[str(key)] = int(number) if number.is_integer() else round(number, 4)
    return out


def as_source(source) -> list:
    if not source:
        return []
    if isinstance(source, str):
        return [source]
    return [str(s) for s in source if s]


def platform_result(platform: str, status: str = "ok", metrics: dict | None = None,
                    meta: dict | None = None, source=None, errors=None, notes=None,
                    collected_at: str | None = None) -> dict:
    if status not in PLATFORM_STATUSES:
        status = "error"
        errors = list(errors or []) + ["invalid status, coerced to error"]
    return {
        "platform": platform,
        "status": status,
        "collected_at": collected_at or utc_now(),
        "source": as_source(source),
        "metrics": clean_metrics(metrics),
        "meta": {k: v for k, v in (meta or {}).items() if v is not None},
        "errors": [str(e)[:300] for e in (errors or [])],
        "notes": [str(n)[:300] for n in (notes or [])],
    }
