"""Launch report generation.

The report is deliberately blunt: every number carries its platform, source,
observation time and measurement definition, and an unmeasured metric is shown
as `--`, never as zero.
"""

from __future__ import annotations

from datetime import datetime, timezone

from .model import parse_iso

SEVERITY_RANK = {"low": 0, "normal": 1, "high": 2, "critical": 3}
STATUS_LABEL = {
    "ok": "measured",
    "partial": "partial",
    "manual": "manual observation",
    "blocked": "BLOCKED by platform",
    "auth_required": "needs credentials",
    "unsupported": "unsupported",
    "error": "ERROR",
    "skipped": "disabled",
}
PLATFORM_LABEL = {
    "github": "GitHub",
    "fdroid": "F-Droid",
    "reddit": "Reddit",
    "hackernews": "Hacker News",
    "product_hunt": "Product Hunt",
    "freebuff": "Freebuff",
    "xerj": "XERJ",
    "website": "Public surfaces",
}
TRACKED_GROWTH = [
    ("github", "apk_downloads", "GitHub APK downloads"),
    ("github", "stars", "GitHub stars"),
    ("github", "forks", "GitHub forks"),
    ("reddit", "score", "Reddit score"),
    ("reddit", "comments", "Reddit comments"),
    ("hackernews", "points", "HN points"),
]


def _fmt(value) -> str:
    if value is None:
        return "--"
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    if isinstance(value, bool):
        return str(value)
    if isinstance(value, int):
        return "{:,}".format(value)
    if isinstance(value, float):
        return ("%.2f" % value).rstrip("0").rstrip(".")
    return str(value)


def _md(value) -> str:
    return ("" if value is None else str(value)).replace("|", "\\|").replace("\n", " ").strip()


def _table(headers, rows) -> str:
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    for row in rows:
        out.append("| " + " | ".join(_md(c) for c in row) + " |")
    return "\n".join(out)


def _platform(snapshot, name) -> dict:
    return (snapshot.get("platforms") or {}).get(name) or {}


def _metric(snapshot, platform, metric):
    value = (_platform(snapshot, platform).get("metrics") or {}).get(metric)
    return value


def _at(history, hours):
    """Closest snapshot at least `hours` old, or None."""
    target = datetime.now(timezone.utc).timestamp() - hours * 3600
    best = None
    for snap in history:
        dt = parse_iso(snap.get("timestamp_utc"))
        if dt is None:
            continue
        if dt.timestamp() <= target:
            if best is None or dt > parse_iso(best.get("timestamp_utc")):
                best = snap
    return best


def _growth(snapshot, history, platform, metric):
    now = _metric(snapshot, platform, metric)
    row = []
    for hours in (24, 24 * 7, 24 * 30):
        old = _metric(_at(history, hours) or {}, platform, metric)
        if now is None or old is None:
            row.append("--")
        else:
            diff = now - old
            row.append("0" if diff == 0 else ("+%s" % _fmt(diff) if diff > 0 else _fmt(diff)))
    return row


def _latest_event(events):
    if not events:
        return None
    return sorted(
        events,
        key=lambda e: (SEVERITY_RANK.get(e.get("severity"), 0), e.get("timestamp_utc") or ""),
    )[-1]


def _definition(config, metric) -> str:
    definitions = config.get("definitions") or {}
    return definitions.get(metric) or definitions.get("default") or "Platform-reported value."


def _env_hint(config, platform) -> str:
    cfg = config.get(platform) or {}
    envs = cfg.get("env") or []
    return ", ".join("`%s`" % e for e in envs) if envs else "credentials"


def build_report(snapshot: dict, previous, events: list, milestones_state: dict,
                 config: dict, history: list | None = None) -> str:
    history = history or []
    platforms = snapshot.get("platforms") or {}
    generated = snapshot.get("timestamp_utc") or ""
    latest = _latest_event(events)
    measured = [n for n, p in platforms.items() if p.get("status") in ("ok", "partial", "manual")]
    attention = [n for n, p in platforms.items() if p.get("status") in ("error", "blocked", "auth_required")]
    unsupported = [n for n, p in platforms.items() if p.get("status") == "unsupported"]
    critical_recent = [e for e in events if e.get("severity") == "critical"]

    lines = []
    add = lines.append

    add("# Pipo - Launch Intelligence Report\n")
    add("**Generated:** %s  " % generated)
    add("**Platforms:** %d configured, %d measured, %d needing attention  " % (
        len(platforms), len(measured), len(attention)))
    add("**Method:** public endpoints only; no app analytics, no install telemetry.  ")
    add("**Snapshot:** `launch_tracker/data/latest.json` (git-ignored)\n")
    add("> Downloads are not installs, stars are not users, and a merge is not a listing. "
        "Every number below is a platform counter with a stated source.\n")

    # -- executive -------------------------------------------------------
    add("## Executive overview\n")
    health = "healthy" if not attention and not critical_recent else ("attention needed" if attention else "degraded")
    add(_table(
        ["Field", "Value"],
        [
            ["Overall health", health],
            ["Platforms monitored", len(platforms)],
            ["Measured successfully", len(measured)],
            ["Needing attention", ", ".join(PLATFORM_LABEL.get(n, n) for n in attention) or "none"],
            ["Unsupported", ", ".join(PLATFORM_LABEL.get(n, n) for n in unsupported) or "none"],
            ["Latest significant event", latest.get("message") if latest else "none recorded"],
            ["Critical events in this run", len(critical_recent)],
        ],
    ))
    add("")

    # -- distribution ----------------------------------------------------
    add("## Distribution\n")
    gh_apk = _metric(snapshot, "github", "apk_downloads")
    fb = _metric(snapshot, "freebuff", "downloads")
    rows = [
        ["GitHub APK downloads", _fmt(gh_apk), "GitHub REST API", _platform(snapshot, "github").get("collected_at")],
        ["Freebuff downloads", _fmt(fb), "manual observation", _platform(snapshot, "freebuff").get("collected_at")],
        ["Latest GitHub release", _platform(snapshot, "github").get("meta", {}).get("latest_release_tag") or "--",
         "GitHub REST API", _platform(snapshot, "github").get("collected_at")],
        ["F-Droid package page", _platform(snapshot, "fdroid").get("meta", {}).get("package_page_status") or "--",
         "f-droid.org", _platform(snapshot, "fdroid").get("collected_at")],
        ["F-Droid MR", _platform(snapshot, "fdroid").get("meta", {}).get("mr_state") or "--",
         "gitlab.com API", _platform(snapshot, "fdroid").get("collected_at")],
    ]
    add(_table(["Signal", "Value", "Source", "Observed"], rows))
    add("")
    if gh_apk is not None and fb is not None:
        add("Reported downloads across counters: **%s** (GitHub %s + Freebuff %s). This is a "
            "non-deduplicated sum of two different platform counters, not a number of people.\n"
            % (_fmt(gh_apk + fb), _fmt(gh_apk), _fmt(fb)))

    # -- community -------------------------------------------------------
    add("## Community traction\n")
    rows = [
        ["GitHub stars", _fmt(_metric(snapshot, "github", "stars"))],
        ["GitHub forks", _fmt(_metric(snapshot, "github", "forks"))],
        ["GitHub watchers", _fmt(_metric(snapshot, "github", "watchers"))],
        ["Open issues", _fmt(_metric(snapshot, "github", "open_issues"))],
        ["Open pull requests", _fmt(_metric(snapshot, "github", "open_prs"))],
        ["External issues/PRs", _fmt(_metric(snapshot, "github", "external_contributions"))],
        ["Reddit score (tracked posts)", _fmt(_metric(snapshot, "reddit", "score"))],
        ["Reddit comments", _fmt(_metric(snapshot, "reddit", "comments"))],
        ["Product Hunt votes", _fmt(_metric(snapshot, "product_hunt", "votes"))],
        ["Hacker News points", _fmt(_metric(snapshot, "hackernews", "points"))],
        ["Hacker News comments", _fmt(_metric(snapshot, "hackernews", "comments"))],
        ["XERJ indexed documents", _fmt(_metric(snapshot, "xerj", "indexed_documents"))],
    ]
    add(_table(["Signal", "Value"], rows))
    add("")

    # -- growth ----------------------------------------------------------
    add("## Growth\n")
    rows = []
    for platform, metric, label in TRACKED_GROWTH:
        delta = _growth(snapshot, history, platform, metric)
        rows.append([label, _fmt(_metric(snapshot, platform, metric)), delta[0], delta[1], delta[2]])
    add(_table(["Metric", "Now", "24h", "7d", "30d"], rows))
    add("\n`--` means no snapshot old enough exists yet; it does not mean zero.\n")

    # -- timeline --------------------------------------------------------
    add("## Launch timeline\n")
    if events:
        rows = [[e.get("timestamp_utc", "")[:16].replace("T", " "), e.get("severity"),
                 PLATFORM_LABEL.get(e.get("platform"), e.get("platform")), e.get("message")]
                for e in events[-20:]]
        add(_table(["When (UTC)", "Severity", "Platform", "Event"], rows))
    else:
        add("No events yet. The first run only establishes a baseline.")
    add("")

    # -- issues ----------------------------------------------------------
    add("## Issues requiring attention\n")
    issues = []
    for name, result in platforms.items():
        if result.get("status") not in ("ok", "partial", "manual", "skipped"):
            detail = "; ".join(result.get("errors") or result.get("notes") or []) or "no detail"
            hint = ""
            if result.get("status") == "auth_required":
                hint = " Provide %s." % _env_hint(config, name)
            issues.append("- **%s** (%s): %s%s" % (
                PLATFORM_LABEL.get(name, name), STATUS_LABEL.get(result.get("status"), result.get("status")),
                detail, hint))
        elif result.get("notes"):
            issues.append("- _%s_: %s" % (PLATFORM_LABEL.get(name, name), "; ".join(result["notes"])))
    for name in ("fdroid", "product_hunt"):
        state = _platform(snapshot, name).get("meta", {})
        if state.get("mr_state") == "opened" and not state.get("reviewers"):
            issues.append("- **F-Droid**: MR open with no reviewer assigned yet.")
    if not issues:
        issues.append("Nothing requires attention.")
    add("\n".join(issues) + "\n")

    # -- milestones ------------------------------------------------------
    add("## Milestones\n")
    achieved = (milestones_state or {}).get("achieved") or {}
    rows = []
    for spec in config.get("milestones") or []:
        platform = spec.get("platform")
        metric = spec.get("metric")
        current = _metric(snapshot, platform, metric)
        for threshold in spec.get("thresholds") or []:
            key = "%s.%s.%s" % (platform, metric, threshold)
            state = "reached %s" % achieved[key][:10] if key in achieved else "pending"
            rows.append([spec.get("label") or key, threshold, _fmt(current), state])
    if rows:
        add(_table(["Milestone", "Target", "Now", "State"], rows))
    else:
        add("No milestones configured.")
    add("")

    # -- next actions ----------------------------------------------------
    add("## Next actions\n")
    actions = []
    fdroid = _platform(snapshot, "fdroid")
    fd_meta = fdroid.get("meta") or {}
    if fd_meta.get("pipeline_verdict") == "no_runner":
        actions.append("Ask an F-Droid maintainer to run CI upstream for MR %s; the fork pipeline never reached a runner."
                       % (fd_meta.get("mr_url") or "the open MR"))
    if fd_meta.get("mr_state") == "opened" and not fd_meta.get("labels"):
        actions.append("Apply the `New App` label on the F-Droid MR (quick actions only work from comments).")
    stars = _metric(snapshot, "github", "stars")
    if stars is not None and stars < 10:
        actions.append("GitHub stars are %s/10: one honest post in a FOSS community is the cheapest next step." % _fmt(stars))
    apk = _metric(snapshot, "github", "apk_downloads")
    if apk is not None and apk < 50:
        actions.append("APK downloads are %s/50; record the count before and 24h after any post." % _fmt(apk))
    fb_age = (_platform(snapshot, "freebuff").get("meta") or {}).get("observation_age_days")
    if isinstance(fb_age, (int, float)) and fb_age > 7:
        actions.append("Freebuff observation is %s days old; update `data/manual/freebuff.json`." % _fmt(fb_age))
    for name in attention:
        if platforms[name].get("status") == "auth_required":
            actions.append("Configure %s for %s." % (_env_hint(config, name), PLATFORM_LABEL.get(name, name)))
    if not actions:
        actions.append("No action needed; keep the schedule running.")
    for index, action in enumerate(actions[:6], 1):
        add("%d. %s" % (index, action))
    add("")

    # -- sources ---------------------------------------------------------
    add("## Sources\n")
    rows = []
    for name, result in platforms.items():
        rows.append([
            PLATFORM_LABEL.get(name, name),
            STATUS_LABEL.get(result.get("status"), result.get("status")),
            ", ".join((result.get("source") or [])[:3]) or "--",
            result.get("collected_at") or "--",
        ])
    add(_table(["Platform", "Status", "Source", "Observed"], rows))
    add("")

    add("## Measurement definitions\n")
    used = sorted({metric for p in platforms.values() for metric in (p.get("metrics") or {})})
    rows = [[metric, _definition(config, metric)] for metric in used]
    add(_table(["Metric", "Definition"], rows) if rows else "No metrics collected.")
    add("")
    add("## Caveats\n")
    add("- Downloads are platform counters: repeat downloads, verification hits and mirrors included.")
    add("- A missing value is reported as `--`; it is never coerced to zero.")
    add("- GitHub release downloads and Freebuff downloads are different counters and are not deduplicated.")
    add("- This report is generated data; the tracker code is committed, the data stays local.")
    return "\n".join(lines) + "\n"
