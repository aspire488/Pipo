"""Freebuff adapter.

Freebuff exposes no public API. Values come from a human-recorded observation
file (data/manual/freebuff.json) with an explicit date and source. Nothing is
interpolated, estimated or carried forward as if it were measured today.
"""

from __future__ import annotations

from datetime import datetime, timezone

from core.model import parse_iso, platform_result

NAME = "freebuff"


def _observations(config, store) -> list:
    data = store.load_manual("freebuff") if store is not None else {}
    if not data:
        return []
    entries = [e for e in (data.get("observations") or [])
               if isinstance(e, dict) and isinstance(e.get("downloads"), (int, float))]
    entries.sort(key=lambda e: str(e.get("date") or ""))
    return entries


def collect(client, config, store=None, **kwargs) -> dict:
    cfg = config.get("freebuff") or {}
    source = [cfg.get("listing_url")] if cfg.get("listing_url") else []
    notes = ["Freebuff has no public API; the value is a dated human observation"]
    entries = _observations(config, store)

    if not entries:
        return platform_result(
            NAME, "partial", source=source,
            errors=["no observation recorded; add data/manual/freebuff.json"],
            notes=notes)

    latest = entries[-1]
    previous = entries[-2] if len(entries) > 1 else None
    stamp = parse_iso(latest.get("date"))
    age_days = None
    if stamp is not None:
        age_days = round((datetime.now(timezone.utc) - stamp).total_seconds() / 86400.0, 1)

    metrics = {"downloads": latest["downloads"], "observations": len(entries)}
    if previous is not None:
        delta = latest["downloads"] - previous["downloads"]
        metrics["downloads_since_previous"] = delta
    meta = {
        "last_observation_date": latest.get("date"),
        "observation_age_days": age_days,
        "observation_source": latest.get("source"),
        "listing_status": latest.get("listing_status"),
    }
    if cfg.get("listing_url"):
        probe = client.get(cfg["listing_url"], accept="text/html")
        meta["listing_http_status"] = probe.status
        if not probe.ok:
            notes.append("listing probe: %s" % probe.summary())

    status = "manual"
    if age_days is not None and age_days > 14:
        notes.append("observation is stale (%.1f days old)" % age_days)
        status = "partial"
    return platform_result(NAME, status, metrics=metrics, meta=meta, source=source, notes=notes)
