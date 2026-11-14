"""Product Hunt adapter.

Product Hunt has no anonymous API. Without PRODUCT_HUNT_TOKEN the adapter
reports `auth_required` and records what the public page probe returned; it
never guesses votes or comments.
"""

from __future__ import annotations

import json
import os
import urllib.request

from core.model import platform_result

NAME = "product_hunt"
GRAPHQL = "https://api.producthunt.com/v2/api/graphql"
QUERY = 'query { post(slug: "%s") { name votesCount commentsCount reviewsRating url } }'


def _graphql(token: str, slug: str):
    payload = json.dumps({"query": QUERY % slug}).encode("utf-8")
    request = urllib.request.Request(
        GRAPHQL, data=payload, method="POST",
        headers={"Authorization": "Bearer %s" % token, "Content-Type": "application/json",
                 "Accept": "application/json", "User-Agent": "PipoLaunchIntel/1.0"})
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def collect(client, config, **kwargs) -> dict:
    cfg = config.get("product_hunt") or {}
    slug = cfg.get("slug") or "pipo"
    page = cfg.get("product_url")
    token = os.environ.get(cfg.get("token_env") or "PRODUCT_HUNT_TOKEN")
    source = [s for s in (page, GRAPHQL) if s]

    if not token:
        probe = client.get(page, accept="text/html") if page else None
        detail = probe.summary() if probe is not None else "no product URL configured"
        return platform_result(
            NAME, "auth_required", source=source,
            errors=["PRODUCT_HUNT_TOKEN not set; public page probe: %s" % detail],
            notes=["Product Hunt requires a developer token; votes and comments are not inferred"])

    try:
        payload = _graphql(token, slug)
    except Exception as exc:  # noqa: BLE001
        return platform_result(NAME, "error", source=source,
                               errors=["GraphQL request failed: %s" % str(exc)[:160]])

    errors = [e.get("message", "GraphQL error") for e in (payload.get("errors") or [])]
    post = (payload.get("data") or {}).get("post")
    if not isinstance(post, dict):
        errors.append("post %r not returned" % slug)
        return platform_result(NAME, "error", source=source, errors=errors)

    metrics = {
        "votes": post.get("votesCount"),
        "comments": post.get("commentsCount"),
        "rating": post.get("reviewsRating"),
    }
    meta = {"name": post.get("name"), "url": post.get("url") or page,
            "listing_status": "live" if post.get("url") else "unknown"}
    return platform_result(NAME, "partial" if errors else "ok", metrics=metrics, meta=meta,
                           source=source, errors=errors,
                           notes=["votes are Product Hunt's counter, not distinct visitors"])
