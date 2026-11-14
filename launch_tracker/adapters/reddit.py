"""Reddit adapter: tracked launch posts, score/comments, moderation removal.

Uses the official OAuth API when REDDIT_CLIENT_ID/SECRET exist, otherwise the
public JSON endpoint. A 403 is recorded as `blocked`, never as zero engagement.
"""

from __future__ import annotations

import base64
import json
import os
import re
import urllib.parse
import urllib.request

from core.model import platform_result

NAME = "reddit"
TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
OAUTH_BASE = "https://oauth.reddit.com"
PUBLIC_BASE = "https://www.reddit.com"
UA = "PipoLaunchIntel/1.0 (+https://github.com/aspire488/Pipo)"


def _post_form(url: str, data: dict, headers: dict):
    req = urllib.request.Request(url, data=urllib.parse.urlencode(data).encode(),
                                 headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def _access_token(cfg: dict):
    client_id = os.environ.get(cfg.get("client_id_env") or "REDDIT_CLIENT_ID")
    client_secret = os.environ.get(cfg.get("client_secret_env") or "REDDIT_CLIENT_SECRET")
    if not (client_id and client_secret):
        return None
    basic = base64.b64encode(("%s:%s" % (client_id, client_secret)).encode()).decode()
    payload = _post_form(TOKEN_URL, {"grant_type": "client_credentials"},
                         {"Authorization": "Basic %s" % basic, "User-Agent": UA})
    return payload.get("access_token")


def post_id(url: str):
    match = re.search(r"/comments/([a-z0-9]+)", url or "")
    return match.group(1) if match else None


def _extract(data):
    try:
        return data[0]["data"]["children"][0]["data"]
    except (KeyError, IndexError, TypeError):
        return None


def collect(client, config, **kwargs) -> dict:
    cfg = config.get("reddit") or {}
    posts = [p for p in (cfg.get("posts") or []) if isinstance(p, dict) and p.get("url")]
    source = [p["url"] for p in posts]
    if not posts:
        return platform_result(NAME, "skipped", source=[], notes=["no Reddit posts configured"])

    token = None
    notes: list = []
    errors: list = []
    try:
        token = _access_token(cfg)
    except Exception as exc:  # noqa: BLE001
        notes.append("OAuth token request failed: %s" % str(exc)[:120])
    base = OAUTH_BASE if token else PUBLIC_BASE
    headers = {"Authorization": "bearer %s" % token} if token else {}
    if not token:
        notes.append("no Reddit credentials; using the public JSON endpoint")

    details: dict = {}
    scores, comments, found, blocked = 0, 0, 0, 0
    for post in posts:
        pid = post_id(post["url"])
        label = post.get("label") or pid or "post"
        if not pid:
            errors.append("could not parse a post id from %s" % post["url"])
            continue
        data, response = client.get_json(
            "%s/comments/%s.json?limit=1&raw_json=1" % (base, pid), headers=headers)
        entry = _extract(data) if data is not None else None
        if entry is None:
            if response.status in (403, 429):
                blocked += 1
                errors.append("%s: %s" % (label, response.summary()))
            else:
                errors.append("%s: %s" % (label, response.summary()))
            continue
        found += 1
        score = entry.get("score")
        num_comments = entry.get("num_comments")
        if isinstance(score, (int, float)):
            scores += score
        if isinstance(num_comments, (int, float)):
            comments += num_comments
        details[label] = {
            "url": post["url"],
            "score": score,
            "comments": num_comments,
            "upvote_ratio": entry.get("upvote_ratio"),
            "removed": entry.get("removed_by_category"),
            "author": entry.get("author"),
            "title": (entry.get("title") or "")[:120],
        }

    metrics = {}
    if found:
        metrics.update({"score": scores, "comments": comments, "posts_found": found})
    if blocked:
        metrics["posts_blocked"] = blocked
    meta = {"posts": details}

    if found == 0:
        status = "blocked" if blocked else "error"
        if not errors:
            errors.append("no post data returned")
        return platform_result(NAME, status, metrics=metrics, meta=meta, source=source,
                               errors=errors, notes=notes)

    notes.append("Reddit score is a fuzzed counter, not unique readers")
    return platform_result(NAME, "partial" if errors else "ok", metrics=metrics, meta=meta,
                           source=source, errors=errors, notes=notes)
