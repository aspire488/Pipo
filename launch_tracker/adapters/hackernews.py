"""Hacker News adapter: Algolia search plus optional Firebase submission ids."""

from __future__ import annotations

import urllib.parse

from core.model import platform_result

NAME = "hackernews"
ALGOLIA = "https://hn.algolia.com/api/v1/search"
FIREBASE = "https://hacker-news.firebaseio.com/v0/item/%s.json"


def _relevant(hit: dict) -> bool:
    haystack = " ".join(str(hit.get(key) or "") for key in
                        ("title", "url", "story_title", "story_text", "comment_text")).lower()
    return ("aspire488" in haystack or "com.pipo.robot" in haystack
            or ("pipo" in haystack and "github" in haystack))


def collect(client, config, **kwargs) -> dict:
    cfg = config.get("hackernews") or {}
    queries = cfg.get("queries") or ["aspire488"]
    submission_ids = cfg.get("submission_ids") or []
    source = ["https://hn.algolia.com", "https://news.ycombinator.com"]
    errors: list = []
    notes: list = []
    hits: dict = {}
    searched = 0

    for query in queries:
        url = "%s?query=%s&tags=story&hitsPerPage=20" % (ALGOLIA, urllib.parse.quote(query))
        data, response = client.get_json(url)
        if not isinstance(data, dict):
            errors.append("search %r: %s" % (query, response.summary()))
            continue
        searched += 1
        for hit in data.get("hits") or []:
            if _relevant(hit):
                hits[str(hit.get("objectID"))] = hit

    for sid in submission_ids:
        data, response = client.get_json(FIREBASE % sid)
        if isinstance(data, dict):
            hits[str(sid)] = {"objectID": str(sid), "title": data.get("title"),
                              "points": data.get("score"), "num_comments": data.get("descendants"),
                              "url": data.get("url")}
        else:
            errors.append("item %s: %s" % (sid, response.summary()))

    points = sum(int(h.get("points") or 0) for h in hits.values())
    comments = sum(int(h.get("num_comments") or 0) for h in hits.values())
    # Zero is only a measurement when the search actually succeeded.
    metrics = ({"submissions": len(hits), "points": points, "comments": comments}
               if searched else {})
    meta = {
        "submissions": {key: {"title": (h.get("title") or "")[:120],
                              "points": h.get("points"),
                              "comments": h.get("num_comments"),
                              "url": h.get("url") or "https://news.ycombinator.com/item?id=%s" % key}
                        for key, h in hits.items()}
    }
    notes.append("a successful search returning nothing is a measured zero, not missing data")
    return platform_result(NAME, "partial" if errors else "ok", metrics=metrics, meta=meta,
                           source=source, errors=errors, notes=notes)
