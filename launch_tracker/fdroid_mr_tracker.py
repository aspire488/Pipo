#!/usr/bin/env python3
"""Pipo F-Droid submission tracker.

Polls GitLab's public API for the merge request opened from
`aspire488/fdroiddata:new-app-pipo` into `fdroid/fdroiddata:master` and records
what is actually happening: MR state, CI pipeline outcome, metadata integrity
(local file vs. the branch that was pushed), and whether the app has landed
upstream or on f-droid.org yet.

No third-party dependencies. No auth required; set GITLAB_TOKEN to raise rate
limits. Nothing here touches app code, and no analytics are involved.

    python launch_tracker/fdroid_mr_tracker.py                # snapshot + report
    python launch_tracker/fdroid_mr_tracker.py --print        # also dump report
    python launch_tracker/fdroid_mr_tracker.py --no-history   # report only
    python launch_tracker/fdroid_mr_tracker.py --schedule     # install the 6-hour Windows task
    python launch_tracker/fdroid_mr_tracker.py --quiet        # scheduled-run mode: silent unless changed
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TRACKER_DIR = ROOT / "launch_tracker"
HISTORY_PATH = TRACKER_DIR / "fdroid_history.jsonl"
CHANGES_PATH = TRACKER_DIR / "fdroid_changes.jsonl"
REPORT_PATH = TRACKER_DIR / "FDROID_MR_TRACKER.md"
TASK_NAME = "PipoFdroidMR"

UPSTREAM_ID = 36528          # fdroid/fdroiddata
FORK_ID = 88755444           # aspire488/fdroiddata
BRANCH = "new-app-pipo"
TARGET_BRANCH = "master"
APPID = "com.pipo.robot"
META_PATH = "metadata/%s.yml" % APPID

LOCAL_META = ROOT / "launch/fdroid/com.pipo.robot.fdroiddata.yml"
EXPECTED_COMMIT = "73afc91002c6d81c452fe6cd38b6b29e39fef626"
EXPECTED_VERSION_CODE = 1
PACKAGE_PAGE = "https://f-droid.org/packages/%s/" % APPID

API = "https://gitlab.com/api/v4"
UA = "PipoFdroidTracker/1.0 (+https://github.com/aspire488/Pipo; local submission tracking)"
MR_URL_FALLBACK = "https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57356"

CHECKLIST_RE = re.compile(r"^\s*[-*]\s*\[( |x|X)\]\s+(.+)$", re.M)


# ---------------------------------------------------------------------------
# Formatting helpers
# ---------------------------------------------------------------------------


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def parse_iso(value):
    if not value:
        return None
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError:
        return None


def short_dt(value, fmt: str = "%Y-%m-%d %H:%M UTC") -> str:
    dt = parse_iso(value)
    return dt.strftime(fmt) if dt else "--"


def md(value) -> str:
    text = "" if value is None else str(value)
    return text.replace("|", "\\|").replace("\n", " ").strip()


def md_table(headers, rows) -> str:
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    for row in rows:
        out.append("| " + " | ".join(md(c) for c in row) + " |")
    return "\n".join(out)


def signed(n: int) -> str:
    return "+%d" % n if n > 0 else str(n)


def delta_text(current, previous) -> str:
    if previous is None or current is None:
        return "--"
    try:
        diff = current - previous
    except TypeError:
        return "--"
    return "0" if diff == 0 else signed(diff)


def jnull(value) -> str:
    """Render a JSON null distinguishably from a missing value."""
    return "null" if value is None else value


def yes_no(value) -> str:
    if value is None:
        return "--"
    return "yes" if value else "no"


# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------


def request(url: str, *, timeout: int = 25, accept: str = "application/json") -> dict:
    headers = {"User-Agent": UA, "Accept": accept}
    token = os.environ.get("GITLAB_TOKEN")
    if token:
        headers["PRIVATE-TOKEN"] = token
    started = time.time()
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=timeout) as r:
            return {
                "status": r.status,
                "body": r.read(),
                "elapsed_ms": int((time.time() - started) * 1000),
                "error": None,
            }
    except urllib.error.HTTPError as e:
        return {
            "status": e.code,
            "body": e.read(),
            "elapsed_ms": int((time.time() - started) * 1000),
            "error": "HTTP %s" % e.code,
        }
    except Exception as e:  # noqa: BLE001 - tracker must never crash on a probe
        return {
            "status": None,
            "body": b"",
            "elapsed_ms": int((time.time() - started) * 1000),
            "error": str(e)[:200],
        }


def get_json(url: str):
    res = request(url)
    if res["status"] != 200:
        return None, res
    try:
        return json.loads(res["body"].decode("utf-8")), res
    except ValueError:
        return None, res


def file_url(project_id: int, ref: str, raw: bool = False) -> str:
    quoted = urllib.parse.quote(META_PATH, safe="")
    base = "%s/projects/%d/repository/files/%s" % (API, project_id, quoted)
    return base + ("/raw?ref=%s" % ref if raw else "?ref=%s" % ref)


# ---------------------------------------------------------------------------
# Collectors
# ---------------------------------------------------------------------------


def collect_mr() -> dict:
    url = "%s/projects/%d/merge_requests?source_branch=%s&state=all&per_page=5" % (
        API, UPSTREAM_ID, BRANCH,
    )
    data, meta = get_json(url)
    if not isinstance(data, list) or not data:
        return {
            "found": False,
            "error": (meta or {}).get("error") or "no merge request returned",
            "web_url": MR_URL_FALLBACK,
        }
    data.sort(key=lambda m: m.get("updated_at") or "", reverse=True)
    m = data[0]
    return {
        "found": True,
        "iid": m.get("iid"),
        "state": m.get("state"),
        "title": m.get("title"),
        "web_url": m.get("web_url") or MR_URL_FALLBACK,
        "merge_status": m.get("detailed_merge_status") or m.get("merge_status"),
        "has_conflicts": m.get("has_conflicts"),
        "allow_collaboration": m.get("allow_collaboration"),
        "labels": m.get("labels") or [],
        "user_notes_count": m.get("user_notes_count"),
        "reviewers": [r.get("username") for r in (m.get("reviewers") or [])],
        "created_at": m.get("created_at"),
        "updated_at": m.get("updated_at"),
        "sha": m.get("sha"),
        "tasks": m.get("task_completion_status") or {},
        "description": m.get("description") or "",
    }


def classify(p: dict) -> str:
    """Name the pipeline outcome, not just the status string."""
    if p.get("yaml_errors"):
        return "yaml_error"
    status = p.get("status")
    if status == "success":
        return "success"
    if status in ("running", "pending", "created", "preparing", "waiting_for_resource", "scheduled"):
        return "pending"
    if status == "canceled":
        return "canceled"
    if status == "skipped":
        return "skipped"
    if status == "failed":
        if not p.get("jobs") and not p.get("started_at"):
            return "no_runner"
        if p.get("jobs"):
            return "job_failure"
        return "failed_no_jobs"
    return status or "unknown"


def collect_pipelines(mr: dict) -> list:
    if not mr.get("found") or mr.get("iid") is None:
        return []
    data, _ = get_json("%s/projects/%d/merge_requests/%s/pipelines" % (API, UPSTREAM_ID, mr["iid"]))
    if not isinstance(data, list):
        return []
    out = []
    for p in data[:5]:
        pid = p.get("project_id")
        plid = p.get("id")
        detail, _ = get_json("%s/projects/%s/pipelines/%s" % (API, pid, plid))
        jobs, _ = get_json("%s/projects/%s/pipelines/%s/jobs?per_page=100" % (API, pid, plid))
        bridges, _ = get_json("%s/projects/%s/pipelines/%s/bridges" % (API, pid, plid))
        d = detail if isinstance(detail, dict) else p
        rec = {
            "id": plid,
            "project_id": pid,
            "source": d.get("source"),
            "status": d.get("status"),
            "yaml_errors": d.get("yaml_errors"),
            "started_at": d.get("started_at"),
            "finished_at": d.get("finished_at"),
            "created_at": d.get("created_at"),
            "duration": d.get("duration"),
            "jobs": len(jobs) if isinstance(jobs, list) else None,
            "bridges": len(bridges) if isinstance(bridges, list) else None,
            "web_url": d.get("web_url"),
        }
        rec["instant_fail"] = bool(
            rec["created_at"] and rec["finished_at"] and rec["started_at"] is None
        )
        rec["verdict"] = classify(rec)
        out.append(rec)
    return out


def validate_metadata(raw: bytes) -> dict:
    try:
        import yaml
    except ImportError:
        return {"parses": None, "_note": "PyYAML not installed; structural checks skipped"}
    try:
        doc = yaml.safe_load(raw.decode("utf-8"))
    except Exception as e:  # noqa: BLE001
        return {"parses": False, "_error": str(e)[:200]}
    if not isinstance(doc, dict):
        return {"parses": False, "_error": "top level is not a mapping"}
    build = (doc.get("Builds") or [{}])[0]
    checks = {
        "parses": True,
        "name_is_pipo": doc.get("Name") == "Pipo",
        "license_mit": doc.get("License") == "MIT",
        "repo_url": doc.get("Repo") == "https://github.com/aspire488/Pipo.git",
        "repo_type_git": doc.get("RepoType") == "git",
        "build_commit_is_v1_0": build.get("commit") == EXPECTED_COMMIT,
        "version_code_1": build.get("versionCode") == EXPECTED_VERSION_CODE,
        "current_version_code_1": doc.get("CurrentVersionCode") == EXPECTED_VERSION_CODE,
        "prebuild_unsigns_release": "signingConfigs.findByName" in (build.get("prebuild") or ""),
        "summary_len_ok": 30 <= len(doc.get("Summary") or "") <= 80,
        "description_len_ok": len(doc.get("Description") or "") <= 4000,
        "categories_ok": (doc.get("Categories") or []) == ["Casual Game"],
        "autoupdate_tags": doc.get("AutoUpdateMode") == "Version"
        and doc.get("UpdateCheckMode") == "Tags",
        "no_signature_pins": not ({"Binaries", "AllowedAPKSigningKeys"} & set(doc)),
    }
    checks["all_pass"] = all(v for v in checks.values() if isinstance(v, bool))
    return checks


def collect_metadata() -> dict:
    info = {"path": META_PATH, "local_path": str(LOCAL_META.relative_to(ROOT)), "exists": LOCAL_META.exists()}
    if not info["exists"]:
        return info
    raw = LOCAL_META.read_bytes()
    info["bytes"] = len(raw)
    info["crlf"] = b"\r\n" in raw
    info["sha256"] = hashlib.sha256(raw).hexdigest()
    info["checks"] = validate_metadata(raw)

    remote = request(file_url(FORK_ID, BRANCH, raw=True))
    if remote["status"] == 200:
        info["remote_bytes"] = len(remote["body"])
        info["remote_sha256"] = hashlib.sha256(remote["body"]).hexdigest()
        info["remote_match"] = info["remote_sha256"] == info["sha256"]
    else:
        info["remote_match"] = None
        info["remote_error"] = remote["error"] or "HTTP %s" % remote["status"]
    return info


def collect_upstream() -> dict:
    present = request(file_url(UPSTREAM_ID, TARGET_BRANCH))
    page = request(PACKAGE_PAGE, accept="text/html")
    title = None
    if page["status"] == 200:
        m = re.search(r"<title[^>]*>(.*?)</title>", page["body"].decode("utf-8", "replace"), re.S | re.I)
        if m:
            title = re.sub(r"\s+", " ", m.group(1)).strip()[:120]
    return {
        "meta_present_on_master": present["status"] == 200,
        "package_page_status": page["status"],
        "package_page_title": title,
        "package_page_error": page["error"],
    }


def checklist_items(description: str) -> tuple:
    items = [{"done": m.group(1).lower() == "x", "text": m.group(2).strip()} for m in CHECKLIST_RE.finditer(description)]
    return sum(1 for i in items if i["done"]), items


# ---------------------------------------------------------------------------
# Diagnosis
# ---------------------------------------------------------------------------


def diagnose(snapshot: dict) -> dict:
    pipelines = snapshot.get("pipelines") or []
    meta = snapshot.get("metadata") or {}
    if not pipelines:
        return {
            "code": "no_pipeline",
            "headline": "No merge request pipeline exists yet.",
            "detail": "GitLab creates one when the MR is opened or the branch is pushed.",
            "action": "If the MR page shows no pipeline, ask a maintainer to run CI on the upstream project.",
        }
    p = pipelines[0]
    verdict = p.get("verdict")
    if verdict == "no_runner":
        return {
            "code": "no_runner",
            "headline": "No CI job ever started: the failed pipeline is a runner-availability problem, not a metadata problem.",
            "detail": (
                "Pipeline %s ran on the fork project (%s), with 0 jobs, 0 bridges, "
                "yaml_errors: null, started_at: null and created_at == finished_at. "
                "GitLab parsed the config and created no work for a runner to pick up."
                % (p.get("id"), p.get("project_id"))
            ),
            "action": (
                "Leave the metadata alone. Ask a maintainer to run the pipeline from the upstream "
                "project during review, and quote the job counts so it is clear this is not a lint failure."
            ),
        }
    if verdict == "yaml_error":
        return {
            "code": "yaml_error",
            "headline": "The pipeline failed while parsing `.gitlab-ci.yml`.",
            "detail": "yaml_errors: %s" % (p.get("yaml_errors"),),
            "action": "This is the repository's CI config, not the app metadata. Do not edit metadata for it.",
        }
    if verdict == "job_failure":
        return {
            "code": "job_failure",
            "headline": "%s job(s) ran and failed — this one is worth reading." % p.get("jobs"),
            "detail": "Pipeline %s finished with status failed and a non-zero job list." % p.get("id"),
            "action": "Open the failing job log; if it names %s, fix the metadata and push." % APPID,
        }
    if verdict == "pending":
        return {
            "code": "pending",
            "headline": "The latest pipeline is still queued or running.",
            "detail": "Status: %s" % p.get("status"),
            "action": "Re-run the tracker in a few minutes.",
        }
    if verdict == "success":
        return {
            "code": "success",
            "headline": "The latest pipeline passed.",
            "detail": "Nothing to fix on the CI side; wait for review.",
            "action": "Check for maintainer comments on the MR.",
        }
    detail = "Status: %s, jobs: %s, yaml_errors: %s" % (
        p.get("status"), p.get("jobs"), p.get("yaml_errors"),
    )
    if meta.get("remote_match") is False:
        detail += ". Local metadata differs from the pushed branch — re-push before asking for review."
    return {"code": verdict or "unknown", "headline": "Pipeline state: %s" % (verdict,), "detail": detail, "action": "Inspect the MR page."}


def next_steps(snapshot: dict) -> list:
    steps = []
    mr = snapshot["mr"]
    diag = snapshot["diagnosis"]
    meta = snapshot["metadata"]
    upstream = snapshot["upstream"]

    if not mr.get("found"):
        steps.append("Find the MR: %s (the API returned no match for branch `%s`)." % (MR_URL_FALLBACK, BRANCH))
        return steps

    if diag["code"] == "no_runner":
        steps.append(
            "Post the CI note on %s: quote `0 jobs / 0 bridges / yaml_errors: null` and ask a "
            "maintainer to run the pipeline upstream." % mr["web_url"]
        )
    elif diag["code"] == "job_failure":
        steps.append("Open the failing job on %s and fix only what it reports." % mr["web_url"])
    elif diag["code"] == "yaml_error":
        steps.append("Fix `.gitlab-ci.yml` on the branch — metadata is not involved.")

    if not mr.get("labels"):
        steps.append(
            'Apply the `New App` label from the MR sidebar: the `/label` line in the description is '
            "inert, because quick actions only run from comments."
        )

    done, items = checklist_items(mr.get("description") or "")
    open_items = [i["text"] for i in items if not i["done"]]
    if open_items:
        steps.append("Decide on %d open checklist item(s): %s" % (
            len(open_items),
            "; ".join(t[:70] + ("..." if len(t) > 70 else "") for t in open_items[:4]),
        ))

    if meta.get("remote_match") is False:
        steps.append("Re-push `%s`: local bytes no longer match the branch." % META_PATH)
    if meta.get("checks", {}).get("all_pass") is False:
        failing = [k for k, v in meta["checks"].items() if v is False]
        steps.append("Fix metadata checks: %s" % ", ".join(failing))

    if mr.get("has_conflicts"):
        steps.append("Rebase onto `%s`: the MR has conflicts." % TARGET_BRANCH)
    if not mr.get("reviewers"):
        steps.append("No reviewer assigned yet — nothing to do but wait for triage.")

    if upstream.get("package_page_status") == 200:
        steps.append("The app is live on f-droid.org: update the README/itch pages and next launch post.")
    elif upstream.get("meta_present_on_master"):
        steps.append("Metadata is merged upstream but the package page is not live yet — wait for the next index build.")

    if not steps:
        steps.append("Nothing actionable: the MR is in a good state, wait for review.")
    return steps


# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------


def build_report(snapshot: dict, previous: dict | None, history_count: int,
                 recent_changes: list | None = None) -> str:
    mr = snapshot["mr"]
    meta = snapshot["metadata"]
    upstream = snapshot["upstream"]
    diag = snapshot["diagnosis"]
    pipelines = snapshot["pipelines"]
    checks = meta.get("checks") or {}

    prev_pipe = (previous or {}).get("pipeline_latest") or {}
    prev_meta = (previous or {}).get("metadata") or {}

    opened = parse_iso(mr.get("created_at"))
    days_open = None
    if opened:
        days_open = (datetime.now(timezone.utc) - opened).days

    out = []
    add = out.append

    add("# Pipo - F-Droid MR Tracker\n")
    add("**Generated:** %s  " % short_dt(snapshot["timestamp_utc"]))
    add("**Snapshot:** #%d%s  " % (
        history_count,
        "" if previous is None else " (previous: %s)" % short_dt(previous.get("timestamp_utc")),
    ))
    add("**Merge request:** [%s](%s)  " % (mr.get("title") or "Pipo MR", mr.get("web_url") or MR_URL_FALLBACK))
    add("**Method:** GitLab public API (`gitlab.com/api/v4`), anonymous; optional `GITLAB_TOKEN`.  ")
    add("**Tracker files:** `launch_tracker/fdroid_mr_tracker.py`, `fdroid_history.jsonl` (git-ignored).\n")
    add("> A download is not an install and a merge is not a listing. This file records submission state, "
        "not users.\n")

    add("## Verdict\n")
    add("**%s**\n" % diag["headline"])
    add("%s\n" % diag["detail"])
    if checks.get("all_pass"):
        add("Metadata re-validated in this run: **%d/%d checks pass**, and the file on `%s` is %s "
            "the local copy.\n" % (
                sum(1 for v in checks.values() if v is True),
                sum(1 for v in checks.values() if isinstance(v, bool)),
                BRANCH,
                "byte-identical to" if meta.get("remote_match") else "NOT identical to",
            ))

    add("## Merge request\n")
    add(md_table(
        ["Field", "Value"],
        [
            ["MR", mr.get("web_url") or MR_URL_FALLBACK],
            ["IID", mr.get("iid")],
            ["State", mr.get("state")],
            ["Merge status", mr.get("merge_status")],
            ["Conflicts", yes_no(mr.get("has_conflicts"))],
            ["Maintainers may push", yes_no(mr.get("allow_collaboration"))],
            ["Labels", ", ".join(mr.get("labels") or []) or "**none**"],
            ["Reviewers", ", ".join(mr.get("reviewers") or []) or "**none assigned**"],
            ["Comments", mr.get("user_notes_count")],
            ["Opened", "%s (%s days ago)" % (short_dt(mr.get("created_at")), days_open if days_open is not None else "--")],
            ["Last update", short_dt(mr.get("updated_at"))],
            ["Head commit", (mr.get("sha") or "--")[:12]],
        ],
    ))
    add("")

    add("## Pipelines\n")
    if pipelines:
        rows = []
        for p in pipelines:
            rows.append([
                p["id"],
                "fork" if p["project_id"] == FORK_ID else "upstream",
                p.get("source"),
                p.get("status"),
                p.get("verdict"),
                p.get("jobs"),
                p.get("bridges"),
                "--" if p.get("duration") is None else "%ds" % p["duration"],
                short_dt(p.get("created_at")),
            ])
        add(md_table(
            ["Pipeline", "Project", "Source", "Status", "Verdict", "Jobs", "Bridges", "Duration", "Created"],
            rows,
        ))
        add("")
        latest = pipelines[0]
        add("Latest pipeline detail:\n")
        add(md_table(
            ["Field", "Value"],
            [
                ["yaml_errors", jnull(latest.get("yaml_errors"))],
                ["started_at", jnull(latest.get("started_at"))],
                ["created_at", latest.get("created_at")],
                ["finished_at", latest.get("finished_at")],
                ["instant fail (no start)", yes_no(latest.get("instant_fail"))],
                ["jobs", latest.get("jobs")],
                ["page", latest.get("web_url")],
            ],
        ))
        add("")
        add("Pipeline verdict moved `%s` -> `%s` since the last snapshot.\n" % (
            prev_pipe.get("verdict") or "--", latest.get("verdict") or "--",
        ))
    else:
        add("No merge request pipeline found (or the API returned none).\n")

    add("## Metadata integrity\n")
    if meta.get("exists"):
        add(md_table(
            ["Field", "Value"],
            [
                ["File", meta.get("local_path")],
                ["Bytes", meta.get("bytes")],
                ["Line endings", "CRLF" if meta.get("crlf") else "LF"],
                ["SHA-256 (local)", (meta.get("sha256") or "--")[:16] + "..."],
                ["SHA-256 (branch)", (meta.get("remote_sha256") or "--")[:16] + "..."],
                ["Local == pushed branch", yes_no(meta.get("remote_match"))],
                ["Expected commit", EXPECTED_COMMIT[:12]],
            ],
        ))
        add("")
        if checks:
            add(md_table(
                ["Check", "Result"],
                [[k.replace("_", " "), "OK" if v is True else ("FAIL" if v is False else str(v))]
                 for k, v in checks.items()],
            ))
            add("")
    else:
        add("Local metadata file missing: `%s`\n" % meta.get("path"))

    done, items = checklist_items(mr.get("description") or "")
    if items:
        add("## MR checklist\n")
        add("%d/%d checked. Open items:\n" % (done, len(items)))
        for item in items:
            if not item["done"]:
                add("- [ ] %s" % item["text"][:110])
        add("")

    add("## Upstream status\n")
    add(md_table(
        ["Signal", "Value"],
        [
            ["`%s` on master" % META_PATH, yes_no(upstream.get("meta_present_on_master"))],
            ["f-droid.org package page", upstream.get("package_page_status")],
            ["Page title", upstream.get("package_page_title") or upstream.get("package_page_error") or "--"],
        ],
    ))
    add("")

    add("## Changes since last snapshot\n")
    latest = pipelines[0] if pipelines else {}
    add(md_table(
        ["Metric", "Now", "Delta"],
        [
            ["Pipeline id", latest.get("id"), "--" if not prev_pipe else delta_text(latest.get("id"), prev_pipe.get("id"))],
            ["Jobs in latest pipeline", latest.get("jobs"), delta_text(latest.get("jobs"), prev_pipe.get("jobs"))],
            ["Comments on MR", mr.get("user_notes_count"), delta_text(mr.get("user_notes_count"), (previous or {}).get("mr", {}).get("user_notes_count"))],
            ["Metadata SHA changed", "yes" if prev_meta and prev_meta.get("sha256") != meta.get("sha256") else "no", "--"],
            ["MR state", mr.get("state"), "--" if not previous else ("changed" if (previous.get("mr") or {}).get("state") != mr.get("state") else "unchanged")],
        ],
    ))
    add("")

    if recent_changes:
        add("## Change log\n")
        for event in recent_changes:
            add("- `%s` — %s" % (short_dt(event.get("timestamp_utc")), "; ".join(event.get("changes") or [])))
        add("")

    add("## What to do next\n")
    for i, step in enumerate(next_steps(snapshot), 1):
        add("%d. %s" % (i, step))
    add("")

    add("## Sources & caveats\n")
    add("- GitLab public API (`/projects/%d/merge_requests`, `/pipelines`, `/repository/files`); no auth used." % UPSTREAM_ID)
    add("- A pipeline that fails with zero jobs and no `started_at` never reached a runner; it is not a lint result.")
    add("- `fdroid lint` cannot run here (fdroidserver is not installed); the structural checks above are local YAML checks.")
    add("- Snapshot history is appended to `fdroid_history.jsonl`; material changes go to `fdroid_changes.jsonl`.")
    add("- `--schedule [HOURS]` installs the Windows task `%s`; `--unschedule` removes it." % TASK_NAME)
    add("- No app code, Kotlin source, or simulation file is touched by this tracker.")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------------------
# History
# ---------------------------------------------------------------------------


def load_history() -> list:
    if not HISTORY_PATH.exists():
        return []
    records = []
    for line in HISTORY_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            records.append(json.loads(line))
        except ValueError:
            continue
    return records


def load_changes(limit: int = 20) -> list:
    if not CHANGES_PATH.exists():
        return []
    events = []
    for line in CHANGES_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except ValueError:
            continue
    return events[-limit:] if limit else events


def append_change(timestamp: str, changes: list) -> None:
    with CHANGES_PATH.open("a", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps({"timestamp_utc": timestamp, "changes": changes}, ensure_ascii=False) + "\n")


def detect_changes(previous, current: dict) -> list:
    """Name what changed between two snapshots, in reviewer language."""
    if not previous:
        return []
    changes = []

    def compare(old: dict, new: dict, key: str, label: str) -> None:
        if old.get(key) != new.get(key):
            changes.append("%s: %s -> %s" % (label, old.get(key), new.get(key)))

    pm, cm = previous.get("mr") or {}, current.get("mr") or {}
    for key, label in (("state", "MR state"), ("merge_status", "merge status"),
                       ("labels", "labels"), ("reviewers", "reviewers"),
                       ("user_notes_count", "comments")):
        compare(pm, cm, key, label)

    pp, cp = previous.get("pipeline_latest") or {}, current.get("pipeline_latest") or {}
    for key, label in (("id", "pipeline id"), ("status", "pipeline status"),
                       ("verdict", "pipeline verdict"), ("jobs", "pipeline jobs")):
        compare(pp, cp, key, label)

    pmeta, cmeta = previous.get("metadata") or {}, current.get("metadata") or {}
    for key, label in (("sha256", "metadata sha256"), ("remote_match", "branch match"),
                       ("bytes", "metadata bytes")):
        compare(pmeta, cmeta, key, label)

    pu, cu = previous.get("upstream") or {}, current.get("upstream") or {}
    for key, label in (("meta_present_on_master", "metadata on master"),
                       ("package_page_status", "f-droid.org page")):
        compare(pu, cu, key, label)
    return changes


def install_schedule(hours: int = 6) -> tuple:
    if os.name != "nt":
        return False, "Windows only. With cron: %s %s --quiet" % (sys.executable, Path(__file__).resolve())
    command = '"%s" "%s" --quiet' % (sys.executable, Path(__file__).resolve())
    result = subprocess.run(
        ["schtasks", "/Create", "/TN", TASK_NAME, "/TR", command,
         "/SC", "HOURLY", "/MO", str(hours), "/ST", datetime.now().strftime("%H:%M"), "/F"],
        capture_output=True, text=True,
    )
    output = (result.stdout or result.stderr).strip()
    if result.returncode != 0:
        return False, "could not create %s: %s" % (TASK_NAME, output[:300])
    return True, "task %s runs every %dh: %s" % (TASK_NAME, hours, output[:200])


def remove_schedule() -> tuple:
    if os.name != "nt":
        return False, "Windows only."
    result = subprocess.run(["schtasks", "/Delete", "/TN", TASK_NAME, "/F"], capture_output=True, text=True)
    output = (result.stdout or result.stderr).strip()
    return result.returncode == 0, output[:200] or "task %s removed" % TASK_NAME


def main() -> int:
    parser = argparse.ArgumentParser(description="Track the Pipo F-Droid merge request.")
    parser.add_argument("--print", dest="show", action="store_true", help="print the report to stdout")
    parser.add_argument("--no-history", dest="history", action="store_false", help="do not append a snapshot")
    parser.add_argument("--quiet", action="store_true", help="print only when something changed")
    parser.add_argument("--schedule", nargs="?", const=6, type=int, metavar="HOURS",
                        help="install or replace the Windows scheduled task (default: every 6 hours)")
    parser.add_argument("--unschedule", action="store_true", help="delete the scheduled task")
    args = parser.parse_args()

    if args.unschedule:
        ok, message = remove_schedule()
        print(message)
        return 0 if ok else 1

    if args.schedule is not None:
        ok, message = install_schedule(args.schedule)
        print(message)
        return 0 if ok else 1

    TRACKER_DIR.mkdir(parents=True, exist_ok=True)
    history = load_history()
    previous = history[-1] if history else None

    mr = collect_mr()
    pipelines = collect_pipelines(mr)
    snapshot = {
        "schema_version": 1,
        "timestamp_utc": utc_now(),
        "mr": mr,
        "pipelines": pipelines,
        "pipeline_latest": pipelines[0] if pipelines else {},
        "metadata": collect_metadata(),
        "upstream": collect_upstream(),
    }
    snapshot["diagnosis"] = diagnose(snapshot)

    changes = detect_changes(previous, snapshot)
    if args.history:
        with HISTORY_PATH.open("a", encoding="utf-8", newline="\n") as fh:
            fh.write(json.dumps(snapshot, ensure_ascii=False) + "\n")
        count = len(history) + 1
    else:
        count = len(history) if history else 1
    if changes:
        append_change(snapshot["timestamp_utc"], changes)

    report = build_report(snapshot, previous, count, load_changes(6))
    REPORT_PATH.write_text(report, encoding="utf-8", newline="\n")

    if not args.quiet or changes:
        diag = snapshot["diagnosis"]
        latest = snapshot["pipeline_latest"]
        print("snapshot      : #%d%s" % (count, "" if args.history else " (not saved)"))
        print("mr            : !%s %s %s" % (mr.get("iid"), mr.get("state"), mr.get("web_url")))
        print("pipeline      : %s %s jobs=%s verdict=%s" % (
            latest.get("id"), latest.get("status"), latest.get("jobs"), latest.get("verdict"),
        ))
        print("metadata      : %d bytes sha256=%s branch_match=%s all_pass=%s" % (
            snapshot["metadata"].get("bytes", 0),
            (snapshot["metadata"].get("sha256") or "--")[:12],
            snapshot["metadata"].get("remote_match"),
            (snapshot["metadata"].get("checks") or {}).get("all_pass"),
        ))
        print("diagnosis     : [%s] %s" % (diag["code"], diag["headline"]))
        if changes:
            print("changed       : %s" % " | ".join(changes))
        print("report        : %s" % REPORT_PATH)
    if args.show:
        print()
        print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
