"""Adapter tests. No network: every response is canned or an injected stub."""

import json
import unittest
from unittest import mock

from adapters import fdroid as fdroid_adapter
from adapters import freebuff as freebuff_adapter
from adapters import github as github_adapter
from adapters import hackernews as hn_adapter
from adapters import product_hunt as ph_adapter
from adapters import reddit as reddit_adapter
from adapters import website as website_adapter
from adapters import xerj as xerj_adapter
from core.http import Response


def response(data, status=200):
    return Response("", status, json.dumps(data).encode("utf-8"), {}, None, 5, 1)


class FakeClient:
    def __init__(self, routes):
        self.routes = routes
        self.calls = []

    def get(self, url, headers=None, accept="application/json"):
        self.calls.append(url)
        for fragment, value in self.routes.items():
            if fragment in url:
                return value(url) if callable(value) else value
        return Response(url, 404, b"{}", {}, "HTTP 404", 3, 1)

    def get_json(self, url, headers=None):
        got = self.get(url, headers)
        return got.json(), got


class FakeStore:
    def __init__(self, data):
        self.data = data

    def load_manual(self, name):
        return self.data


REPO = {
    "stargazers_count": 5, "forks_count": 1, "subscribers_count": 2,
    "default_branch": "main", "created_at": "2026-09-29T00:00:00Z",
    "pushed_at": "2026-11-14T00:00:00Z", "license": {"spdx_id": "MIT"},
}
ISSUES = [
    {"user": {"login": "stranger"}},
    {"user": {"login": "dependabot[bot]"}, "pull_request": {}},
]
RELEASES = [{
    "tag_name": "v1.0", "published_at": "2026-10-04T01:17:28Z",
    "html_url": "https://github.com/aspire488/Pipo/releases/tag/v1.0",
    "assets": [{"name": "Pipo.apk", "download_count": 25}, {"name": "notes.txt", "download_count": 3}],
}]
COMMITS = [{"sha": "abcdef123456", "commit": {"committer": {"date": "2026-11-14T02:50:29Z"},
                                             "message": "docs: update\n\nbody"}}]


def github_client(repo=None, issues=None, releases=None, commits=None):
    return FakeClient({
        "/issues": response(ISSUES if issues is None else issues),
        "/releases": response(RELEASES if releases is None else releases),
        "/commits": response(COMMITS if commits is None else commits),
        "/repos/aspire488/Pipo": response(REPO if repo is None else repo),
    })


class GithubTests(unittest.TestCase):
    def test_normalises_metrics_and_meta(self):
        result = github_adapter.collect(github_client(), {"github": {}})
        self.assertEqual(result["status"], "ok")
        metrics = result["metrics"]
        self.assertEqual(metrics["stars"], 5)
        self.assertEqual(metrics["open_issues"], 1)
        self.assertEqual(metrics["open_prs"], 1)
        self.assertEqual(metrics["external_contributions"], 1)
        self.assertEqual(metrics["apk_downloads"], 25)
        self.assertEqual(metrics["all_asset_downloads"], 28)
        self.assertEqual(result["meta"]["latest_release_tag"], "v1.0")
        self.assertEqual(result["meta"]["latest_commit_sha"], "abcdef123456")
        self.assertEqual(result["meta"]["latest_commit_message"], "docs: update")

    def test_missing_field_is_error_not_zero(self):
        partial = dict(REPO)
        partial.pop("subscribers_count")
        result = github_adapter.collect(github_client(repo=partial), {"github": {}})
        self.assertNotIn("watchers", result["metrics"])
        self.assertEqual(result["status"], "partial")
        self.assertTrue(any("subscribers_count" in e for e in result["errors"]))

    def test_repository_failure_isolated(self):
        client = FakeClient({"/repos/aspire488/Pipo": Response("", 500, b"", {}, "HTTP 500")})
        result = github_adapter.collect(client, {"github": {}})
        self.assertEqual(result["status"], "error")
        self.assertEqual(result["metrics"], {})


class RedditTests(unittest.TestCase):
    POST = "https://www.reddit.com/r/fossdroid/comments/1xvygzx/app_pipo/"

    def test_blocked_is_not_zero(self):
        client = FakeClient({"comments/1xvygzx": Response("", 403, b"", {}, "HTTP 403")})
        result = reddit_adapter.collect(client, {"reddit": {"posts": [{"url": self.POST}]}})
        self.assertEqual(result["status"], "blocked")
        self.assertNotIn("score", result["metrics"])

    def test_parses_score_and_comments(self):
        payload = [{"data": {"children": [{"data": {
            "score": 7, "num_comments": 3, "upvote_ratio": 0.9,
            "removed_by_category": None, "author": "aspire488", "title": "Pipo"}}]}}]
        client = FakeClient({"comments/1xvygzx": response(payload)})
        with mock.patch.dict("os.environ", {"REDDIT_CLIENT_ID": "", "REDDIT_CLIENT_SECRET": ""}):
            result = reddit_adapter.collect(client, {"reddit": {"posts": [{"url": self.POST}]}})
        self.assertEqual(result["metrics"]["score"], 7)
        self.assertEqual(result["metrics"]["comments"], 3)
        self.assertEqual(result["status"], "ok")

    def test_no_posts_configured_is_skipped(self):
        result = reddit_adapter.collect(FakeClient({}), {"reddit": {"posts": []}})
        self.assertEqual(result["status"], "skipped")


class HackerNewsTests(unittest.TestCase):
    def test_empty_search_is_measured_zero(self):
        client = FakeClient({"hn.algolia.com": response({"hits": []})})
        result = hn_adapter.collect(client, {"hackernews": {"queries": ["aspire488"]}})
        self.assertEqual(result["metrics"]["submissions"], 0)
        self.assertEqual(result["status"], "ok")

    def test_irrelevant_hits_are_filtered(self):
        hits = [{"objectID": "1", "title": "Pipo the cat", "url": "https://cats.example"},
                {"objectID": "2", "title": "Pipo", "url": "https://github.com/aspire488/Pipo",
                 "points": 4, "num_comments": 2}]
        client = FakeClient({"hn.algolia.com": response({"hits": hits})})
        result = hn_adapter.collect(client, {"hackernews": {"queries": ["aspire488"]}})
        self.assertEqual(result["metrics"]["submissions"], 1)
        self.assertEqual(result["metrics"]["points"], 4)

    def test_search_failure_is_partial(self):
        client = FakeClient({"hn.algolia.com": Response("", 503, b"", {}, "HTTP 503")})
        result = hn_adapter.collect(client, {"hackernews": {"queries": ["aspire488"]}})
        self.assertEqual(result["status"], "partial")
        self.assertNotIn("points", result["metrics"])


class ProductHuntTests(unittest.TestCase):
    def test_without_token_reports_auth_required(self):
        client = FakeClient({"producthunt.com": Response("", 403, b"", {}, "HTTP 403")})
        with mock.patch.dict("os.environ", {"PRODUCT_HUNT_TOKEN": ""}):
            result = ph_adapter.collect(client, {"product_hunt": {"product_url": "https://www.producthunt.com/products/pipo-2"}})
        self.assertEqual(result["status"], "auth_required")
        self.assertNotIn("votes", result["metrics"])


class FreebuffTests(unittest.TestCase):
    def test_manual_observation(self):
        store = FakeStore({"observations": [
            {"date": "2026-11-01", "downloads": 12},
            {"date": "2026-11-13", "downloads": 23, "source": "README"}], })
        result = freebuff_adapter.collect(FakeClient({}), {}, store=store)
        self.assertEqual(result["status"], "manual")
        self.assertEqual(result["metrics"]["downloads"], 23)
        self.assertEqual(result["metrics"]["downloads_since_previous"], 11)

    def test_stale_observation_is_partial(self):
        store = FakeStore({"observations": [{"date": "2020-01-01", "downloads": 1}]})
        result = freebuff_adapter.collect(FakeClient({}), {}, store=store)
        self.assertEqual(result["status"], "partial")
        self.assertTrue(any("stale" in note for note in result["notes"]))

    def test_no_observation_is_not_zero(self):
        result = freebuff_adapter.collect(FakeClient({}), {}, store=FakeStore({}))
        self.assertNotIn("downloads", result["metrics"])
        self.assertEqual(result["status"], "partial")


class XerjTests(unittest.TestCase):
    def test_unsupported_without_endpoint(self):
        result = xerj_adapter.collect(FakeClient({}), {"xerj": {"base_url": None}})
        self.assertEqual(result["status"], "unsupported")
        self.assertEqual(result["metrics"], {})


class WebsiteTests(unittest.TestCase):
    def test_partial_when_a_surface_fails(self):
        client = FakeClient({"good": response({"ok": True}), "bad": Response("", 404, b"", {}, "HTTP 404")})
        result = website_adapter.collect(client, {"website": {"surfaces": ["https://good", "https://bad"]}})
        self.assertEqual(result["status"], "partial")
        self.assertEqual(result["metrics"]["surfaces_up"], 1)
        self.assertEqual(result["meta"]["surfaces"]["https://bad"], 404)


class LegacyStub:
    def collect_mr(self):
        return {"found": True, "iid": 57356, "state": "opened", "web_url": "https://gitlab.com/x",
                "user_notes_count": 1, "tasks": {"count": 17, "completed_count": 13},
                "labels": [], "reviewers": [], "merge_status": "mergeable"}

    def collect_pipelines(self, mr):
        return [{"id": 1, "jobs": 0, "verdict": "no_runner", "status": "failed",
                 "bridges": 0, "yaml_errors": None, "web_url": "https://gitlab.com/p"}]

    def collect_metadata(self):
        return {"sha256": "abc123", "remote_match": True,
                "checks": {"parses": True, "prebuild_unsigns_release": True, "all_pass": True}}

    def collect_upstream(self):
        return {"meta_present_on_master": False, "package_page_status": 404}


class FdroidTests(unittest.TestCase):
    def test_maps_legacy_collectors(self):
        result = fdroid_adapter.collect(FakeClient({}), {}, legacy=LegacyStub(), refresh_legacy=False)
        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["metrics"]["pipeline_jobs"], 0)
        self.assertEqual(result["meta"]["mr_state"], "opened")
        self.assertEqual(result["meta"]["pipeline_verdict"], "no_runner")
        self.assertEqual(result["meta"]["package_page_status"], 404)

    def test_listing_live_flag_not_http_status(self):
        class Published(LegacyStub):
            def collect_upstream(self):
                return {"meta_present_on_master": True, "package_page_status": 200}
        pending = fdroid_adapter.collect(FakeClient({}), {}, legacy=LegacyStub(), refresh_legacy=False)
        live = fdroid_adapter.collect(FakeClient({}), {}, legacy=Published(), refresh_legacy=False)
        self.assertEqual(pending["metrics"]["listing_live"], 0)
        self.assertEqual(live["metrics"]["listing_live"], 1)

    def test_missing_mr_is_error(self):
        class Broken(LegacyStub):
            def collect_mr(self):
                return {"found": False, "error": "404"}
        result = fdroid_adapter.collect(FakeClient({}), {}, legacy=Broken(), refresh_legacy=False)
        self.assertEqual(result["status"], "error")

    def test_crash_is_isolated(self):
        class Exploding(LegacyStub):
            def collect_mr(self):
                raise RuntimeError("boom")
        result = fdroid_adapter.collect(FakeClient({}), {}, legacy=Exploding(), refresh_legacy=False)
        self.assertEqual(result["status"], "error")
        self.assertIn("boom", result["errors"][0])


if __name__ == "__main__":
    unittest.main()
