"""GitHub adapter: repo, releases, issue/PR split, commit activity, traffic."""

from __future__ import annotations

import os

from core.model import platform_result

NAME = "github"
API = "https://api.github.com"


def _is_bot(login: str) -> bool:
    login = (login or "").lower()
    return login.endswith("[bot]") or login.endswith("-bot") or login in {"dependabot", "github-actions"}


def collect(client, config, **kwargs) -> dict:
    cfg = config.get("github") or {}
    repo = cfg.get("repo") or "aspire488/Pipo"
    owner = repo.split("/")[0]
    token = os.environ.get(cfg.get("token_env") or "GITHUB_TOKEN")
    headers = {"Authorization": "Bearer %s" % token} if token else {}

    source = ["https://github.com/%s" % repo, "%s/repos/%s" % (API, repo)]
    metrics: dict = {}
    meta: dict = {}
    errors: list = []
    notes: list = []

    repo_data, repo_resp = client.get_json("%s/repos/%s" % (API, repo), headers=headers)
    if not isinstance(repo_data, dict):
        return platform_result(NAME, "error", source=source,
                               errors=["repository: %s" % repo_resp.summary()])

    for key, metric in (("stargazers_count", "stars"), ("forks_count", "forks"),
                        ("subscribers_count", "watchers")):
        if repo_data.get(key) is None:
            errors.append("%s missing from repository response" % key)
        else:
            metrics[metric] = repo_data[key]

    meta["default_branch"] = repo_data.get("default_branch")
    meta["created_at"] = repo_data.get("created_at")
    meta["pushed_at"] = repo_data.get("pushed_at")
    meta["license"] = (repo_data.get("license") or {}).get("spdx_id")

    issues_data, issues_resp = client.get_json(
        "%s/repos/%s/issues?state=open&per_page=100" % (API, repo), headers=headers)
    if isinstance(issues_data, list):
        issues = [i for i in issues_data if "pull_request" not in i]
        prs = [i for i in issues_data if "pull_request" in i]
        metrics["open_issues"] = len(issues)
        metrics["open_prs"] = len(prs)
        external = [i for i in issues_data
                    if (i.get("user") or {}).get("login") != owner
                    and not _is_bot((i.get("user") or {}).get("login", ""))]
        metrics["external_contributions"] = len(external)
        metrics["external_issues"] = len([i for i in issues
                                          if (i.get("user") or {}).get("login") != owner
                                          and not _is_bot((i.get("user") or {}).get("login", ""))])
        if len(issues_data) >= 100:
            notes.append("open item list truncated at 100; counts are a lower bound")
    else:
        errors.append("issues: %s" % issues_resp.summary())

    releases, releases_resp = client.get_json(
        "%s/repos/%s/releases?per_page=10" % (API, repo), headers=headers)
    if isinstance(releases, list):
        metrics["releases"] = len(releases)
        apk_downloads = 0
        all_downloads = 0
        for release in releases:
            for asset in release.get("assets") or []:
                count = asset.get("download_count") or 0
                all_downloads += count
                if (asset.get("name") or "").lower().endswith(".apk"):
                    apk_downloads += count
        metrics["apk_downloads"] = apk_downloads
        metrics["all_asset_downloads"] = all_downloads
        if releases:
            newest = releases[0]
            meta["latest_release_tag"] = newest.get("tag_name")
            meta["latest_release_date"] = newest.get("published_at")
            meta["latest_release_url"] = newest.get("html_url")
    else:
        errors.append("releases: %s" % releases_resp.summary())

    commits, commits_resp = client.get_json(
        "%s/repos/%s/commits?per_page=1" % (API, repo), headers=headers)
    if isinstance(commits, list) and commits:
        commit = commits[0]
        meta["latest_commit_sha"] = (commit.get("sha") or "")[:12]
        meta["latest_commit_date"] = ((commit.get("commit") or {}).get("committer") or {}).get("date")
        meta["latest_commit_message"] = (((commit.get("commit") or {}).get("message") or "")
                                         .splitlines() or [""])[0][:120]
    else:
        errors.append("commits: %s" % commits_resp.summary())

    if token:
        traffic, traffic_resp = client.get_json(
            "%s/repos/%s/traffic/views" % (API, repo), headers=headers)
        if isinstance(traffic, dict) and traffic.get("count") is not None:
            metrics["traffic_views_14d"] = traffic.get("count")
            metrics["traffic_uniques_14d"] = traffic.get("uniques")
        else:
            notes.append("traffic unavailable: %s (token lacks push access)" % traffic_resp.summary())
    else:
        notes.append("no GITHUB_TOKEN: traffic and higher rate limits unavailable")

    notes.append("release asset downloads are not installs")
    status = "ok" if not errors else "partial"
    return platform_result(NAME, status, metrics=metrics, meta=meta, source=source,
                           errors=errors, notes=notes)
