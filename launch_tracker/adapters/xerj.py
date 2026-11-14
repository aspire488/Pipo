"""XERJ adapter.

No XERJ endpoint, API or reference exists in this repository, so the adapter is
honestly `unsupported` until a verifiable base URL is configured. If one is, it
probes it and only reports document counts the service actually returns.
"""

from __future__ import annotations

from core.model import platform_result

NAME = "xerj"
COUNT_KEYS = ("indexed_documents", "document_count", "documents", "count")


def collect(client, config, **kwargs) -> dict:
    cfg = config.get("xerj") or {}
    base = cfg.get("base_url")
    if not base:
        return platform_result(
            NAME, "unsupported", source=[],
            notes=["no public XERJ endpoint exists or is documented; configure base_url to enable"],
            meta={"reason": "no_endpoint_configured"})

    response = client.get(base)
    if not response.ok:
        status = "blocked" if response.status in (401, 403) else "error"
        return platform_result(NAME, status, source=[base], errors=[response.summary()])

    data = response.json()
    metrics = {}
    meta = {"endpoint": base}
    if isinstance(data, dict):
        for key in COUNT_KEYS:
            value = data.get(key)
            if isinstance(value, (int, float)) and not isinstance(value, bool):
                metrics["indexed_documents"] = value
                break
        else:
            meta["shape"] = "no document count in response"
    else:
        meta["shape"] = type(data).__name__
    return platform_result(NAME, "ok" if metrics else "partial", metrics=metrics, meta=meta,
                           source=[base],
                           notes=["XERJ exposes no documented schema; only observed counts are reported"])
