"""Public surface availability: the cheapest listing-removal detector."""

from __future__ import annotations

from core.model import platform_result

NAME = "website"


def collect(client, config, **kwargs) -> dict:
    cfg = config.get("website") or {}
    surfaces = [s for s in (cfg.get("surfaces") or []) if isinstance(s, str)]
    if not surfaces:
        return platform_result(NAME, "skipped", notes=["no surfaces configured"])

    statuses: dict = {}
    for url in surfaces:
        response = client.get(url, accept="text/html")
        statuses[url] = response.status if response.status is not None else 0

    up = sum(1 for code in statuses.values() if 200 <= code < 400)
    metrics = {"surfaces_up": up, "surfaces_total": len(statuses)}
    notes = []
    down = [url for url, code in statuses.items() if not 200 <= code < 400]
    if down:
        notes.append("not OK: %s" % ", ".join(down[:5]))
    status = "ok" if not down else "partial"
    return platform_result(NAME, status, metrics=metrics, meta={"surfaces": statuses},
                           source=surfaces[:3], notes=notes)
