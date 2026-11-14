"""F-Droid adapter.

Reuses the existing fdroid_mr_tracker.py collectors (single set of API calls)
and can refresh that tool's own history/report so migrating the scheduled job
does not break the artifacts it produces.
"""

from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path

from core.model import platform_result

NAME = "fdroid"


def _legacy_module():
    path = Path(__file__).resolve().parent.parent / "fdroid_mr_tracker.py"
    spec = importlib.util.spec_from_file_location("pipo_fdroid_legacy_mr", path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def _refresh_legacy(mod, mr, pipelines, metadata, upstream) -> None:
    snapshot = {
        "schema_version": 1,
        "timestamp_utc": mod.utc_now(),
        "mr": mr,
        "pipelines": pipelines,
        "pipeline_latest": pipelines[0] if pipelines else {},
        "metadata": metadata,
        "upstream": upstream,
    }
    snapshot["diagnosis"] = mod.diagnose(snapshot)
    history = mod.load_history()
    previous = history[-1] if history else None
    changes = mod.detect_changes(previous, snapshot)
    with mod.HISTORY_PATH.open("a", encoding="utf-8", newline="\n") as handle:
        handle.write(json.dumps(snapshot, ensure_ascii=False) + "\n")
    if changes:
        mod.append_change(snapshot["timestamp_utc"], changes)
    report = mod.build_report(snapshot, previous, len(history) + 1, mod.load_changes(6))
    mod.REPORT_PATH.write_text(report, encoding="utf-8", newline="\n")


def collect(client, config, legacy=None, refresh_legacy: bool = True, **kwargs) -> dict:
    cfg = config.get("fdroid") or {}
    mr_url = cfg.get("mr_url") or "https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57356"
    source = [mr_url, "https://f-droid.org/packages/%s/" % (cfg.get("appid") or "com.pipo.robot")]
    notes: list = []
    errors: list = []

    try:
        mod = legacy or _legacy_module()
        mr = mod.collect_mr()
        pipelines = mod.collect_pipelines(mr)
        metadata = mod.collect_metadata()
        upstream = mod.collect_upstream()
    except Exception as exc:  # noqa: BLE001 - one adapter must never kill the run
        return platform_result(NAME, "error", source=source,
                               errors=["collector failed: %s" % str(exc)[:200]])

    if not mr.get("found"):
        return platform_result(NAME, "error", source=source,
                               errors=["merge request not found: %s" % (mr.get("error") or "unknown")])

    latest = pipelines[0] if pipelines else {}
    checks = metadata.get("checks") or {}
    metrics = {
        "pipeline_jobs": latest.get("jobs"),
        "pipelines": len(pipelines),
        "mr_comments": mr.get("user_notes_count"),
        "tasks_total": (mr.get("tasks") or {}).get("count"),
        "tasks_done": (mr.get("tasks") or {}).get("completed_count"),
        "metadata_checks_passed": sum(1 for v in checks.values() if v is True),
        "metadata_checks_total": sum(1 for v in checks.values() if isinstance(v, bool)),
        "package_page_status": (upstream or {}).get("package_page_status"),
        # Explicit flag: an HTTP status is not a milestone threshold (404 >= 200).
        "listing_live": 1 if (upstream or {}).get("package_page_status") == 200 else 0,
    }
    meta = {
        "mr_url": mr.get("web_url") or mr_url,
        "mr_state": mr.get("state"),
        "merge_status": mr.get("merge_status"),
        "labels": ", ".join(mr.get("labels") or []),
        "reviewers": ", ".join(mr.get("reviewers") or []),
        "pipeline_verdict": latest.get("verdict"),
        "pipeline_status": latest.get("status"),
        "pipeline_id": latest.get("id"),
        "metadata_sha256": metadata.get("sha256"),
        "branch_match": metadata.get("remote_match"),
        "upstream_present": (upstream or {}).get("meta_present_on_master"),
        "package_page_status": (upstream or {}).get("package_page_status"),
        "pipeline_job_count": latest.get("jobs"),
        "pipeline_bridge_count": latest.get("bridges"),
        "yaml_errors": bool(latest.get("yaml_errors")),
    }
    if latest.get("verdict") == "no_runner":
        notes.append("pipeline failed with zero jobs: runner availability, not a lint result")
    if metadata.get("remote_match") is False:
        errors.append("local metadata differs from the pushed branch")
    if checks.get("all_pass") is False:
        errors.append("metadata structural checks failed")

    if refresh_legacy:
        try:
            _refresh_legacy(mod, mr, pipelines, metadata, upstream)
        except Exception as exc:  # noqa: BLE001
            notes.append("legacy report not refreshed: %s" % str(exc)[:120])

    return platform_result(NAME, "ok" if not errors else "partial", metrics=metrics,
                           meta=meta, source=source, errors=errors, notes=notes)
