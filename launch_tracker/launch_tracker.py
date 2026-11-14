#!/usr/bin/env python3
"""Pipo launch intelligence: one entry point for the whole launch ecosystem.

    python launch_tracker/launch_tracker.py --all
    python launch_tracker/launch_tracker.py --platform github --platform fdroid
    python launch_tracker/launch_tracker.py --report
    python launch_tracker/launch_tracker.py --events
    python launch_tracker/launch_tracker.py --history
    python launch_tracker/launch_tracker.py --milestones
    python launch_tracker/launch_tracker.py --health
    python launch_tracker/launch_tracker.py --test-notifications
    python launch_tracker/launch_tracker.py --test-email
    python launch_tracker/launch_tracker.py --digest
    python launch_tracker/launch_tracker.py --schedule 6
    python launch_tracker/launch_tracker.py --migrate-schedule
    python launch_tracker/launch_tracker.py --unschedule

Exit codes: 0 success, 1 failure, 2 another run holds the lock.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from core import VERSION, changes as change_engine, milestones as milestone_engine  # noqa: E402
from core.emailer import send_digest, send_test_email  # noqa: E402
from core.http import HttpClient  # noqa: E402
from core.model import platform_result, utc_now  # noqa: E402
from core.notify import Notifier  # noqa: E402
from core.report import build_report  # noqa: E402
from core.storage import Store  # noqa: E402
import adapters  # noqa: E402

TASK_NAME = "PipoLaunchTracker"
LEGACY_TASK = "PipoFdroidMR"
CONFIG_PATH = ROOT / "config" / "platforms.json"


# ---------------------------------------------------------------------------
# Config & collection
# ---------------------------------------------------------------------------


def load_config(path=None) -> dict:
    path = Path(path) if path else CONFIG_PATH
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def collect_platform(name: str, client, config: dict, store: Store, refresh_legacy: bool = True) -> dict:
    module = adapters.get(name)
    if module is None:
        return platform_result(name, "error", errors=["unknown platform %r" % name])
    kwargs = {}
    if name == "fdroid":
        kwargs["refresh_legacy"] = refresh_legacy
    if name == "freebuff":
        kwargs["store"] = store
    started = time.time()
    try:
        result = module.collect(client, config, **kwargs)
    except Exception as exc:  # noqa: BLE001 - adapter isolation is the point
        result = platform_result(name, "error", errors=["adapter crashed: %s" % str(exc)[:200]])
    result["duration_ms"] = int((time.time() - started) * 1000)
    return result


def run_collection(names, config: dict, store: Store, quiet: bool = False,
                   refresh_legacy: bool = True) -> dict:
    http_cfg = config.get("http") or {}
    client = HttpClient(timeout=http_cfg.get("timeout", 20), retries=http_cfg.get("retries", 1))
    results = {}
    for name in names:
        result = collect_platform(name, client, config, store, refresh_legacy)
        results[name] = result
        if not quiet:
            detail = "; ".join(result.get("errors") or result.get("notes") or [])[:90]
            print("[%-12s] %-14s %s" % (name, result.get("status"), detail))
    return results


# ---------------------------------------------------------------------------
# Reporting
# ---------------------------------------------------------------------------


def write_report(snapshot, store: Store, config: dict) -> Path:
    reporting = config.get("reporting") or {}
    path = store.reports_dir / reporting.get("filename", "PIPO_LAUNCH_REPORT.md")
    report = build_report(
        snapshot,
        store.load_latest() if snapshot is None else None,
        store.events(200),
        store.load_milestones(),
        config,
        store.history(),
    )
    store.atomic_write(path, report)
    return path


def refresh_report(store: Store, config: dict) -> Path | None:
    snapshot = store.load_latest()
    if snapshot is None:
        print("no snapshot yet: run --all first")
        return None
    return write_report(snapshot, store, config)


# ---------------------------------------------------------------------------
# Schedule
# ---------------------------------------------------------------------------


def install_schedule(hours: int = 6, migrate: bool = False) -> tuple:
    if os.name != "nt":
        return False, "Windows only. With cron use: %s %s --all --quiet" % (sys.executable, Path(__file__).resolve())
    if migrate:
        subprocess.run(["schtasks", "/Delete", "/TN", LEGACY_TASK, "/F"], capture_output=True, text=True)
    command = '"%s" "%s" --all --quiet --no-email' % (sys.executable, Path(__file__).resolve())
    result = subprocess.run(
        ["schtasks", "/Create", "/TN", TASK_NAME, "/TR", command,
         "/SC", "HOURLY", "/MO", str(hours), "/ST", time.strftime("%H:%M"), "/F"],
        capture_output=True, text=True)
    output = (result.stdout or result.stderr).strip()
    if result.returncode != 0:
        return False, "could not create %s: %s" % (TASK_NAME, output[:300])
    return True, "task %s runs every %dh%s: %s" % (
        TASK_NAME, hours, " (legacy F-Droid task removed)" if migrate else "", output[:160])


def remove_schedule() -> tuple:
    if os.name != "nt":
        return False, "Windows only."
    result = subprocess.run(["schtasks", "/Delete", "/TN", TASK_NAME, "/F"], capture_output=True, text=True)
    detail = (result.stdout or result.stderr).strip()
    return result.returncode == 0, detail[:200] or "task %s removed" % TASK_NAME


# ---------------------------------------------------------------------------
# Views
# ---------------------------------------------------------------------------


def show_history(store: Store, limit: int) -> None:
    history = store.history(limit)
    if not history:
        print("no snapshots yet")
        return
    print("%-22s %-10s %s" % ("snapshot (UTC)", "platforms", "key metrics"))
    for snap in history:
        platforms = snap.get("platforms") or {}
        github = (platforms.get("github") or {}).get("metrics") or {}
        bits = ["apk=%s" % github.get("apk_downloads", "--"),
                "stars=%s" % github.get("stars", "--"),
                "fdroid=%s" % (platforms.get("fdroid") or {}).get("status", "--")]
        print("%-22s %-10s %s" % (snap.get("timestamp_utc", "")[:19].replace("T", " "), len(platforms), " ".join(bits)))


def show_events(store: Store, limit: int) -> None:
    events = store.events(limit)
    if not events:
        print("no events recorded")
        return
    for event in events:
        print("%s [%-8s] %-12s %s" % (
            event.get("timestamp_utc", "")[:19].replace("T", " "),
            event.get("severity", ""), event.get("platform", ""), event.get("message", "")))


def show_milestones(store: Store, config: dict) -> None:
    snapshot = store.load_latest() or {}
    achieved = (store.load_milestones() or {}).get("achieved") or {}
    platforms = snapshot.get("platforms") or {}
    print("%-32s %8s %8s %s" % ("milestone", "target", "now", "state"))
    for spec in config.get("milestones") or []:
        platform, metric = spec.get("platform"), spec.get("metric")
        current = ((platforms.get(platform) or {}).get("metrics") or {}).get(metric)
        for threshold in spec.get("thresholds") or []:
            key = "%s.%s.%s" % (platform, metric, threshold)
            state = "reached " + achieved[key][:10] if key in achieved else "pending"
            print("%-32s %8s %8s %s" % (spec.get("label") or key, threshold,
                                        "--" if current is None else current, state))


def show_health(store: Store, config: dict) -> None:
    snapshot = store.load_latest()
    if snapshot is None:
        print("no snapshot yet: run --all first")
        return
    print("snapshot: %s  (tracker %s)" % (snapshot.get("timestamp_utc"), snapshot.get("tracker_version")))
    print("%-12s %-14s %-10s %s" % ("platform", "status", "metrics", "detail"))
    for name in adapters.ORDER:
        result = (snapshot.get("platforms") or {}).get(name) or {}
        detail = "; ".join(result.get("errors") or result.get("notes") or [])[:80]
        print("%-12s %-14s %-10s %s" % (name, result.get("status", "missing"),
                                        len(result.get("metrics") or {}), detail))


def show_summary(snapshot: dict, events: list, report_path, quiet: bool) -> None:
    if quiet and not events:
        return
    platforms = snapshot.get("platforms") or {}
    ok = sum(1 for p in platforms.values() if p.get("status") in ("ok", "partial", "manual"))
    print("snapshot   : %s (%d platforms, %d measured)" % (snapshot["timestamp_utc"], len(platforms), ok))
    print("events     : %d new" % len(events))
    for event in events[:8]:
        print("  [%-8s] %-12s %s" % (event.get("severity"), event.get("platform"), event.get("message")))
    if report_path:
        print("report     : %s" % report_path)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Pipo launch intelligence tracker")
    parser.add_argument("--all", action="store_true", help="collect from every enabled platform")
    parser.add_argument("--platform", action="append", default=[], metavar="NAME",
                        help="collect one platform (repeatable)")
    parser.add_argument("--report", dest="report_only", action="store_true",
                        help="regenerate the report from the latest snapshot")
    parser.add_argument("--history", dest="history_n", nargs="?", const=10, type=int,
                        help="show recent snapshots")
    parser.add_argument("--events", dest="events_n", nargs="?", const=20, type=int,
                        help="show recent events")
    parser.add_argument("--milestones", action="store_true", help="show milestone progress")
    parser.add_argument("--health", action="store_true", help="show adapter health")
    parser.add_argument("--test-notifications", action="store_true", help="send a test notification")
    parser.add_argument("--test-email", action="store_true", help="send a real SMTP test email")
    parser.add_argument("--simulate-event", metavar="TYPE",
                        help="with --test-email: render a simulated event (fdroid_merge, fdroid_published, milestone)")
    parser.add_argument("--digest", action="store_true", help="send the daily digest email now")
    parser.add_argument("--force-digest", action="store_true",
                        help="send the digest even if already sent today or empty")
    parser.add_argument("--deliveries", nargs="?", const=10, type=int,
                        help="show recent notification delivery records")
    parser.add_argument("--no-email", action="store_true", help="disable email for this run")
    parser.add_argument("--quiet", action="store_true", help="silent unless something changed")
    parser.add_argument("--json", action="store_true", help="print the snapshot as JSON")
    parser.add_argument("--no-notify", action="store_true", help="collect but send no notifications")
    parser.add_argument("--no-legacy", action="store_true", help="do not refresh fdroid_mr_tracker artifacts")
    parser.add_argument("--config", default=str(CONFIG_PATH), help="path to platforms.json")
    parser.add_argument("--schedule", nargs="?", const=6, type=int, metavar="HOURS",
                        help="install or replace the Windows scheduled task")
    parser.add_argument("--migrate-schedule", action="store_true",
                        help="replace the legacy PipoFdroidMR task with the unified one")
    parser.add_argument("--unschedule", action="store_true", help="delete the scheduled task")
    return parser


def main(argv=None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    try:
        config = load_config(args.config)
    except (OSError, ValueError) as exc:
        print("config error: %s" % exc)
        return 1

    if args.unschedule:
        ok, message = remove_schedule()
        print(message)
        return 0 if ok else 1
    if args.migrate_schedule:
        ok, message = install_schedule(args.schedule or 6, migrate=True)
        print(message)
        return 0 if ok else 1
    if args.schedule is not None:
        ok, message = install_schedule(args.schedule)
        print(message)
        return 0 if ok else 1

    store = Store(ROOT).ensure()
    notify_config = dict(config.get("notifications") or {})
    if args.no_email:
        notify_config["email"] = False
    notifier = Notifier(notify_config, store, quiet=args.quiet)

    if args.test_notifications:
        for result in notifier.test():
            print("%-9s %-4s %s" % (result["channel"], "ok" if result["ok"] else "FAIL", result.get("detail", "")))
        return 0

    if args.test_email:
        result = send_test_email(store, simulate=args.simulate_event)
        print("email     %-4s %s" % ("ok" if result.get("ok") else "FAIL", result.get("detail", "")))
        return 0 if result.get("ok") else 1

    if args.digest or args.force_digest:
        result = send_digest(store, config, force=args.force_digest)
        unconfigured = "not configured" in (result.get("detail") or "")
        state = "skipped" if result.get("skipped") else ("ok" if result.get("ok") else "FAIL")
        print("digest    %-7s %s" % (state, result.get("detail", "")))
        # Before SMTP secrets exist, keep scheduled runs green but say so loudly.
        return 0 if (result.get("ok") or unconfigured) else 1

    if args.deliveries is not None:
        records = store.deliveries(args.deliveries)
        if not records:
            print("no deliveries recorded")
        for record in records:
            print("%s %-9s %-6s %s" % (
                (record.get("timestamp_utc") or "")[:19].replace("T", " "),
                record.get("channel"), record.get("status"),
                (record.get("subject") or record.get("error") or "")[:80]))
        return 0

    if args.history_n is not None:
        show_history(store, args.history_n)
        return 0
    if args.events_n is not None:
        show_events(store, args.events_n)
        return 0
    if args.milestones:
        show_milestones(store, config)
        return 0
    if args.health:
        show_health(store, config)
        return 0
    if args.report_only:
        path = refresh_report(store, config)
        return 0 if path else 1

    names = args.platform or list(adapters.ORDER)
    unknown = [n for n in names if adapters.get(n) is None]
    if unknown:
        print("unknown platform(s): %s" % ", ".join(unknown))
        return 1

    with store.lock(wait_seconds=0) as acquired:
        if not acquired:
            print("another run holds the lock; exiting")
            return 2

        previous = store.load_latest()
        started = time.time()
        results = run_collection(names, config, store, quiet=args.quiet,
                                 refresh_legacy=not args.no_legacy)
        snapshot = {
            "schema_version": 2,
            "tracker_version": VERSION,
            "timestamp_utc": utc_now(),
            "duration_ms": int((time.time() - started) * 1000),
            "platforms": results,
        }

        events = change_engine.diff_snapshots(previous, snapshot, config) if previous else []
        milestone_events, achieved = milestone_engine.evaluate(
            results, config, (store.load_milestones() or {}).get("achieved") or {})
        if milestone_events:
            store.save_milestones({"achieved": achieved})

        fresh = store.append_events(events + milestone_events)
        store.save_latest(snapshot)
        store.append_history(snapshot)

        report_path = write_report(snapshot, store, config)
        if not args.no_notify:
            for result in notifier.notify(fresh, snapshot):
                if not result.get("ok"):
                    store.log_notification("channel %s failed: %s" % (result.get("channel"), result.get("detail")))

        if args.json:
            print(json.dumps(snapshot, indent=2, ensure_ascii=False))
        else:
            show_summary(snapshot, fresh, report_path, args.quiet)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
