#!/usr/bin/env python3
"""
Pipo 25,000-user, five-year longitudinal simulation (deterministic seed 488).

Every user is an independent agent tracked on their own clock from discovery (Day 1)
through Day 30, 60, 180, Year 1, Year 2, Year 3, Year 4 and Year 5 (Day 1,825).

The model is deliberately grounded in Pipo's real long-term constraints:

  * one local JSON save, schema 2, no export/import UI
  * allowBackup=false + all cloud/D2D domains excluded
  * Simulator.catchUp replays at most 3 days after a long absence
  * journal capped at 400 entries; memories at 120; events at 30; pet memories at 20
  * traits clamped to 0.05..0.95; milestones only at 7/30/100/365 days
  * moving festival dates are tabled only through 2028
  * finite authored content: 20 projects, 6 Nib tricks, 10 festivals

No network, no LLM, no external services. Standard library only.
"""

import csv
import json
import os
import random
import statistics
import sys
from collections import Counter, defaultdict

SEED = 488
POPULATION = 25_000
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(BASE_DIR, "agents_25k_5yr.csv")
SUMMARY_PATH = os.path.join(BASE_DIR, "summary_5yr.json")

# Windows consoles default to cp1252; the table uses arrows and a few typographic dashes.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

CHECKPOINTS = [7, 30, 60, 180, 365, 730, 1095, 1460, 1825]
CHECKPOINT_LABELS = ["day_7", "day_30", "day_60", "day_180", "year_1", "year_2", "year_3", "year_4", "year_5"]
CSV_CHECKPOINTS = ["day_30", "day_60", "day_180", "year_1", "year_2", "year_3", "year_4", "year_5"]

# ---------------------------------------------------------------------------
# Waves: organic discovery over five calendar years. Each row still gets a full
# five-year individual horizon, so late cohorts are observed for five years from
# their own Day 1. This is cohort retention, not calendar-year prevalence.
# ---------------------------------------------------------------------------
WAVE_BASE = [
    ("Y1 Launch: GitHub + Product Hunt", 26),
    ("Y1 Reddit: r/Android word of mouth", 22),
    ("Y2 FOSS: privacy forums", 12),
    ("Y2 Seasonal: Diwali / New Year", 14),
    ("Y3 Indie: itch.io browse", 8),
    ("Y3 Seasonal: Diwali / New Year", 8),
    ("Y4 Long tail: Obtainium / F-Droid", 6),
    ("Y5 Revival: old threads & search", 4),
]

# ---------------------------------------------------------------------------
# Archetypes. Counts sum to 25,000. survival is the conditional probability of
# reaching each checkpoint from the previous one, after install.
# ---------------------------------------------------------------------------
ARCHETYPES = [
    dict(
        name="Android Power User", count=3500, tech=(8, 10), discover=0.96,
        download=0.86, pp=0.94, attachment=0.62, journal=0.32, content=0.50,
        dormant=0.05, sentiment=0.30, update_scale=0.70, recovery_bonus=0.05,
        survival=[0.78, 0.86, 0.90, 0.82, 0.80, 0.80, 0.86, 0.90, 0.93],
        wave_mult=[1.5, 1.0, 1.7, 0.6, 0.5, 1.3, 0.8, 0.6],
    ),
    dict(
        name="Cozy Virtual-Pet Fan", count=3750, tech=(3, 7), discover=0.93,
        download=0.78, pp=0.56, attachment=0.70, journal=0.38, content=0.60,
        dormant=0.06, sentiment=0.46, update_scale=1.10, recovery_bonus=0.0,
        survival=[0.76, 0.86, 0.91, 0.85, 0.83, 0.83, 0.88, 0.91, 0.93],
        wave_mult=[0.8, 0.7, 0.3, 2.2, 2.0, 1.0, 0.8, 0.8],
    ),
    dict(
        name="Open-Source / Privacy Advocate", count=2750, tech=(7, 10), discover=0.94,
        download=0.86, pp=0.95, attachment=0.62, journal=0.28, content=0.50,
        dormant=0.05, sentiment=0.38, update_scale=0.80, recovery_bonus=0.05,
        survival=[0.75, 0.85, 0.89, 0.83, 0.81, 0.81, 0.86, 0.90, 0.92],
        wave_mult=[0.9, 0.9, 2.8, 0.5, 0.4, 2.0, 0.7, 0.8],
    ),
    dict(
        name="Casual Play Protect-Wary User", count=3250, tech=(2, 5), discover=0.85,
        download=0.62, pp=0.30, attachment=0.30, journal=0.16, content=0.95,
        dormant=0.02, sentiment=-0.06, update_scale=1.35, recovery_bonus=0.0,
        survival=[0.55, 0.62, 0.62, 0.52, 0.56, 0.66, 0.76, 0.85, 0.90],
        wave_mult=[0.9, 1.6, 0.2, 1.9, 0.8, 0.6, 0.8, 0.9],
    ),
    dict(
        name="Indie Developer / Maker", count=2250, tech=(7, 10), discover=0.95,
        download=0.88, pp=0.90, attachment=0.50, journal=0.27, content=0.80,
        dormant=0.04, sentiment=0.30, update_scale=0.80, recovery_bonus=0.05,
        survival=[0.70, 0.78, 0.79, 0.66, 0.64, 0.67, 0.77, 0.86, 0.90],
        wave_mult=[1.4, 0.5, 1.2, 0.4, 2.3, 1.4, 0.6, 0.5],
    ),
    dict(
        name="Battery-Conscious Minimalist", count=2000, tech=(6, 9), discover=0.89,
        download=0.68, pp=0.79, attachment=0.44, journal=0.22, content=0.70,
        dormant=0.03, sentiment=0.04, update_scale=0.90, recovery_bonus=0.0,
        survival=[0.66, 0.73, 0.73, 0.61, 0.63, 0.69, 0.79, 0.87, 0.91],
        wave_mult=[1.1, 1.5, 1.2, 0.7, 0.4, 1.7, 0.5, 0.5],
    ),
    dict(
        name="Bored Student / Scroller", count=2500, tech=(4, 8), discover=0.91,
        download=0.74, pp=0.61, attachment=0.38, journal=0.21, content=0.85,
        dormant=0.03, sentiment=0.10, update_scale=1.10, recovery_bonus=0.0,
        survival=[0.66, 0.74, 0.73, 0.59, 0.61, 0.67, 0.77, 0.86, 0.90],
        wave_mult=[0.8, 1.2, 0.4, 1.7, 2.1, 0.6, 1.2, 1.0],
    ),
    dict(
        name="Virtual-Pet Skeptic / AI-Slop Watchdog", count=1750, tech=(5, 8), discover=0.88,
        download=0.58, pp=0.70, attachment=0.26, journal=0.14, content=1.00,
        dormant=0.02, sentiment=-0.22, update_scale=0.90, recovery_bonus=0.0,
        survival=[0.50, 0.56, 0.56, 0.46, 0.51, 0.61, 0.73, 0.83, 0.88],
        wave_mult=[1.1, 1.5, 1.1, 0.6, 0.5, 1.0, 1.0, 1.2],
    ),
    dict(
        name="Parent / Digital-Detox Buyer", count=1500, tech=(2, 5), discover=0.80,
        download=0.55, pp=0.36, attachment=0.66, journal=0.31, content=0.55,
        dormant=0.04, sentiment=0.34, update_scale=1.30, recovery_bonus=0.0,
        survival=[0.73, 0.81, 0.86, 0.79, 0.79, 0.81, 0.87, 0.91, 0.93],
        wave_mult=[0.6, 0.5, 0.2, 2.7, 2.4, 0.4, 0.5, 0.7],
    ),
    dict(
        name="Retro / Indie Game Browser", count=1750, tech=(5, 8), discover=0.87,
        download=0.66, pp=0.63, attachment=0.45, journal=0.24, content=0.90,
        dormant=0.03, sentiment=0.16, update_scale=1.00, recovery_bonus=0.0,
        survival=[0.58, 0.66, 0.67, 0.56, 0.59, 0.66, 0.76, 0.85, 0.90],
        wave_mult=[0.8, 0.4, 0.4, 1.0, 3.0, 1.2, 1.5, 1.6],
    ),
]

assert sum(a["count"] for a in ARCHETYPES) == POPULATION
assert all(len(a["survival"]) == 9 and len(a["wave_mult"]) == 8 for a in ARCHETYPES)

DOWNLOAD_REASONS = [
    ("Not installing an APK from GitHub", 30),
    ("Unknown-sources / Play Protect fear before download", 26),
    ("No Play Store listing / no trust signal", 20),
    ("Not my genre after reading more", 10),
    ("26.1 MB + optional 40 MB voice feels too heavy", 7),
    ("Waiting for a store version", 7),
]

PP_FAIL_REASONS = [
    ("Play Protect warning scared me off", 42),
    ("Could not find 'Install anyway' / gave up", 26),
    ("Did not trust an unverified GitHub APK", 20),
    ("Download or package error, no retry", 12),
]

SOFT_REASONS = {
    0: [("First session did not hook", 30), ("Expected a game, got a passive toy", 25),
        ("Battery / 3D room cost", 15), ("Sideload distrust persisted", 15), ("Notifications too quiet", 15)],
    1: [("Novelty faded before Day 30", 35), ("Notifications too quiet", 30),
        ("Forgot it existed", 20), ("Expected more game", 15)],
    2: [("Novelty faded by Day 60", 40), ("Notifications too quiet", 25),
        ("Battery / 3D room cost", 20), ("Expected a game loop", 15)],
    3: [("Content thinning after six months", 35), ("No auto-updates; manual GitHub check", 25),
        ("Life got busy", 25), ("No export / backup anxiety", 15)],
    4: [("Life change / stopped checking", 35), ("Content exhausted", 30),
        ("Manual update friction", 20), ("No export / backup anxiety", 15)],
    5: [("Life change / stopped checking", 30), ("Content exhausted", 30),
        ("Manual update friction", 20), ("No export / backup anxiety", 20)],
}

UPDATE_REASONS = [
    ("APK update: uninstalled old app before installing, save wiped", 55),
    ("APK update: switched GitHub/F-Droid source, signature mismatch forced uninstall", 30),
    ("APK update: install failed after old app was already removed", 15),
]

PHONE_REASON = "Phone upgrade: no export/transfer, save left on old device"
CONTENT_REASON = "Content exhaustion: projects, tricks and festival surprises ran out"

JOURNAL_CAP = 400
MEMORY_CAP = 120
EVENT_CAP = 30
PET_MEMORY_CAP = 20
CATCH_UP_MAX_DAYS = 3
SIM_STEP_MINUTES = 40
SCHEMA = 2
MILESTONE_DAYS = [7, 30, 100, 365]
PROJECT_COUNT = 20
NIB_TRICKS = 6
NIB_STAGES = 5
FESTIVAL_COUNT = 10
MOVING_FESTIVAL_YEARS = [2026, 2027, 2028]


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def wpick(rng, pairs):
    total = sum(w for _, w in pairs)
    r = rng.random() * total
    for value, weight in pairs:
        r -= weight
        if r <= 0:
            return value
    return pairs[-1][0]


def pick_wave(rng, arch):
    pairs = [(label, base * arch["wave_mult"][i]) for i, (label, base) in enumerate(WAVE_BASE)]
    return wpick(rng, pairs)


def wave_type(label):
    if "Launch" in label:
        return "Launch"
    if "Reddit" in label:
        return "Reddit"
    if "FOSS" in label:
        return "FOSS"
    if "Seasonal" in label:
        return "Seasonal"
    if "Indie" in label:
        return "Indie"
    if "Long tail" in label:
        return "Long tail"
    return "Revival"


def wave_survival_bonus(label):
    return {
        "Launch": 0.005,
        "Reddit": -0.005,
        "FOSS": 0.020,
        "Seasonal": -0.020,
        "Indie": 0.000,
        "Long tail": 0.010,
        "Revival": 0.015,
    }[wave_type(label)]


def interval_years(i):
    prev = 0 if i == 0 else CHECKPOINTS[i - 1]
    return (CHECKPOINTS[i] - prev) / 365.0


def tenure_midpoint(i):
    prev = 0 if i == 0 else CHECKPOINTS[i - 1]
    return int((prev + CHECKPOINTS[i]) / 2)


def soft_reason(rng, i):
    return wpick(rng, SOFT_REASONS[min(i, 5)])


def update_loss_probability(arch, tech, years):
    activity = clamp(0.25 + tech * 0.025, 0.25, 0.55)
    mistake = clamp(0.20 - tech * 0.016, 0.015, 0.20)
    source_switch = 0.010 + (0.015 if "Privacy" in arch["name"] or "Open-Source" in arch["name"] else 0.0)
    annual = clamp(activity * (mistake + source_switch) * arch["update_scale"], 0.004, 0.10)
    return 1.0 - (1.0 - annual) ** years


def phone_loss_probability(arch, tech, attachment, i):
    upgrade = {5: 0.10, 6: 0.09}.get(i, 0.0)
    recovery = clamp(0.02 + tech * 0.012 + arch["recovery_bonus"], 0.02, 0.30)
    keep_old = 0.04 + (0.04 if attachment > 0.68 else 0.0)
    return upgrade * clamp(1.0 - recovery - keep_old, 0.0, 1.0)


def content_decay_probability(arch, attachment, i):
    if i < 5:
        return 0.0
    year_number = i - 3  # i=5 -> year 2
    return clamp(
        arch["content"] * 0.010 * ((year_number - 1) ** 1.15) * (1.15 - attachment),
        0.0,
        0.10,
    )


def dormant_probability(arch, attachment, i):
    if i < 5:
        return 0.0
    annual = arch["dormant"] * (0.5 + attachment)
    return clamp(1.0 - (1.0 - annual) ** interval_years(i), 0.0, 0.20)


def pick_update_reason(rng, arch):
    if "Privacy" in arch["name"] or "Open-Source" in arch["name"]:
        return wpick(rng, [
            ("APK update: uninstalled old app before installing, save wiped", 40),
            ("APK update: switched GitHub/F-Droid source, signature mismatch forced uninstall", 45),
            ("APK update: install failed after old app was already removed", 15),
        ])
    return wpick(rng, UPDATE_REASONS)


def empty_row(uid, arch, wave):
    return {
        "user_id": uid,
        "archetype": arch["name"],
        "discovery_wave": wave,
        "passes_play_protect": False,
        "day_30": False,
        "day_60": False,
        "day_180": False,
        "year_1": False,
        "year_2": False,
        "year_3": False,
        "year_4": False,
        "year_5": False,
        "churn_stage": "never_discovered",
        "primary_churn_reason": "Never reached by any discovery wave",
        "lifetime_journal_entries": 0,
        "sentiment_score": 0.0,
        "_discovered": False,
        "_downloaded": False,
        "_installed": False,
        "_day7": False,
        "_pp_triggered": False,
        "_tech": 5,
        "_attachment": 0.0,
        "_wave_type": "none",
        "_dormant_episodes": 0,
        "_dormant_reaction": "",
        "_tenure_days": 0,
        "_mode": "never_discovered",
    }


def simulate_agent(uid, arch, rng):
    tech = rng.randint(*arch["tech"])
    discovered = rng.random() < clamp(arch["discover"] + (tech - 5.5) * 0.008, 0.40, 0.99)

    if not discovered:
        row = empty_row(uid, arch, "never")
        row["_tech"] = tech
        row["sentiment_score"] = round(clamp(arch["sentiment"] - 0.05 + rng.uniform(-0.10, 0.10), -1.0, 1.0), 2)
        return row

    wave = pick_wave(rng, arch)
    wtype = wave_type(wave)

    download_p = clamp(arch["download"] + (tech - 5.5) * 0.015, 0.20, 0.95)
    downloaded = rng.random() < download_p
    if not downloaded:
        row = empty_row(uid, arch, wave)
        row.update({
            "_discovered": True,
            "_downloaded": False,
            "_pp_triggered": False,
            "_tech": tech,
            "_wave_type": wtype,
            "churn_stage": "no_download",
            "primary_churn_reason": wpick(rng, DOWNLOAD_REASONS),
            "_mode": "no_download",
        })
        row["sentiment_score"] = round(clamp(arch["sentiment"] - 0.08 + rng.uniform(-0.12, 0.12), -1.0, 1.0), 2)
        return row

    readme_reader = rng.random() < min(0.85, 0.30 + tech * 0.035)
    pp_triggered = rng.random() < 0.66
    pp_p = clamp(arch["pp"] + (tech - 5.0) * 0.030 + (0.06 if readme_reader else 0.0), 0.06, 0.97)
    passes_pp = (not pp_triggered) or (rng.random() < pp_p)

    if not passes_pp:
        row = empty_row(uid, arch, wave)
        row.update({
            "_discovered": True,
            "_downloaded": True,
            "_installed": False,
            "_pp_triggered": True,
            "_tech": tech,
            "_wave_type": wtype,
            "churn_stage": "play_protect",
            "primary_churn_reason": wpick(rng, PP_FAIL_REASONS),
            "_mode": "play_protect",
        })
        row["sentiment_score"] = round(clamp(arch["sentiment"] - 0.42 + rng.uniform(-0.12, 0.12), -1.0, 1.0), 2)
        return row

    # Installed. Everything below is the user's own five-year clock.
    attachment = clamp(
        arch["attachment"] + rng.uniform(-0.22, 0.22) + (tech - 5.5) * 0.008,
        0.05,
        0.95,
    )
    flags = {label: False for label in CSV_CHECKPOINTS}
    day7 = False
    churn_stage = "retained_5yr"
    reason = "Still checking in at Year 5"
    tenure = 1825
    mode = "retained"
    dormant_episodes = 0
    dormant_reaction = ""
    update_losses = 0
    phone_losses = 0
    content_losses = 0

    attachment_effect = [0.02, 0.04, 0.08, 0.14, 0.18, 0.22, 0.24, 0.24, 0.24]
    wbonus = wave_survival_bonus(wave)

    for i, label in enumerate(CHECKPOINT_LABELS):
        p = clamp(
            arch["survival"][i]
            + (attachment - 0.5) * attachment_effect[i]
            + wbonus,
            0.05,
            0.97,
        )
        if rng.random() >= p:
            churn_stage = label
            reason = soft_reason(rng, i)
            tenure = tenure_midpoint(i)
            mode = "soft_churn"
            break

        # Long-term Android/offline failure modes happen between checkpoints.
        if i >= 3:
            years = interval_years(i)
            if rng.random() < update_loss_probability(arch, tech, years):
                churn_stage = label
                reason = pick_update_reason(rng, arch)
                tenure = tenure_midpoint(i)
                mode = "update_save_loss"
                update_losses = 1
                break

        if i in (5, 6):
            if rng.random() < phone_loss_probability(arch, tech, attachment, i):
                churn_stage = label
                reason = PHONE_REASON
                tenure = tenure_midpoint(i)
                mode = "phone_upgrade_loss"
                phone_losses = 1
                break

        if i >= 5:
            if rng.random() < content_decay_probability(arch, attachment, i):
                churn_stage = label
                reason = CONTENT_REASON
                tenure = tenure_midpoint(i)
                mode = "content_exhaustion"
                content_losses = 1
                break

            if rng.random() < dormant_probability(arch, attachment, i):
                dormant_episodes += 1
                dormant_reaction = wpick(rng, [
                    ("charmed: he kept living", 45),
                    ("disappointed: only three days replayed", 35),
                    ("neutral: came back for Nib", 20),
                ])

        if label in flags:
            flags[label] = True
        if i == 0:
            day7 = True

    # Active days exclude six-month dormant gaps for journal accounting.
    active_days = max(0, tenure - 180 * dormant_episodes)
    journal_rate = arch["journal"] * (0.65 + attachment * 0.70)
    lifetime_journal = int(active_days * journal_rate) + 1

    sentiment = arch["sentiment"] + rng.uniform(-0.12, 0.12)
    if churn_stage in ("day_7", "day_30", "day_60"):
        sentiment -= 0.10
    elif churn_stage in ("day_180", "year_1"):
        sentiment -= 0.02
    if churn_stage == "retained_5yr":
        sentiment += 0.38
    elif flags["year_4"]:
        sentiment += 0.34
    elif flags["year_3"]:
        sentiment += 0.29
    elif flags["year_2"]:
        sentiment += 0.23
    elif flags["year_1"]:
        sentiment += 0.17
    elif flags["day_180"]:
        sentiment += 0.08
    elif flags["day_60"]:
        sentiment += 0.02
    if attachment > 0.65:
        sentiment += 0.06
    if mode == "update_save_loss":
        sentiment -= 0.80
    elif mode == "phone_upgrade_loss":
        sentiment -= 0.75
    elif mode == "content_exhaustion":
        sentiment -= 0.30
    if dormant_episodes:
        if dormant_reaction.startswith("charmed"):
            sentiment += 0.08
        elif dormant_reaction.startswith("disappointed"):
            sentiment -= 0.12

    row = {
        "user_id": uid,
        "archetype": arch["name"],
        "discovery_wave": wave,
        "passes_play_protect": True,
        "day_30": flags["day_30"],
        "day_60": flags["day_60"],
        "day_180": flags["day_180"],
        "year_1": flags["year_1"],
        "year_2": flags["year_2"],
        "year_3": flags["year_3"],
        "year_4": flags["year_4"],
        "year_5": flags["year_5"],
        "churn_stage": churn_stage,
        "primary_churn_reason": reason,
        "lifetime_journal_entries": lifetime_journal,
        "sentiment_score": round(clamp(sentiment, -1.0, 1.0), 2),
        "_discovered": True,
        "_downloaded": True,
        "_installed": True,
        "_day7": day7,
        "_pp_triggered": pp_triggered,
        "_tech": tech,
        "_attachment": attachment,
        "_wave_type": wtype,
        "_dormant_episodes": dormant_episodes,
        "_dormant_reaction": dormant_reaction,
        "_tenure_days": tenure,
        "_mode": mode,
    }
    return row


def pct(a, b):
    return round(100.0 * a / b, 1) if b else 0.0


def summary_of(agents):
    n = len(agents)
    discovered = sum(a["_discovered"] for a in agents)
    downloaded = sum(a["_downloaded"] for a in agents)
    installed = sum(a["_installed"] for a in agents)
    day7 = sum(a["_day7"] for a in agents)
    counts = {cp: sum(bool(a[cp]) for a in agents) for cp in CSV_CHECKPOINTS}
    never = n - discovered
    pp_failures = sum(1 for a in agents if a["_mode"] == "play_protect")
    pp_prompted = sum(1 for a in agents if a["_pp_triggered"])

    stage_rows = []
    prev_count = n
    post_install = {"Installed (passed Play Protect)", "Day 7", "Day 30", "Day 60", "Day 180",
                    "Year 1", "Year 2", "Year 3", "Year 4", "Year 5"}
    stage_defs = [
        ("Addressable population", n),
        ("Discovered", discovered),
        ("APK downloaded", downloaded),
        ("Installed (passed Play Protect)", installed),
        ("Day 7", day7),
        ("Day 30", counts["day_30"]),
        ("Day 60", counts["day_60"]),
        ("Day 180", counts["day_180"]),
        ("Year 1", counts["year_1"]),
        ("Year 2", counts["year_2"]),
        ("Year 3", counts["year_3"]),
        ("Year 4", counts["year_4"]),
        ("Year 5", counts["year_5"]),
    ]
    for label, count in stage_defs:
        stage_rows.append({
            "stage": label,
            "count": count,
            "stage_to_stage_pct": pct(count, prev_count),
            "of_total_pct": pct(count, n),
            "of_installed_pct": pct(count, installed) if installed and label in post_install else None,
        })
        prev_count = count

    funnel = {
        "addressable": n,
        "discovered": discovered,
        "never_discovered": never,
        "downloaded": downloaded,
        "installed": installed,
        "day7": day7,
        "day30": counts["day_30"],
        "day60": counts["day_60"],
        "day180": counts["day_180"],
        "year1": counts["year_1"],
        "year2": counts["year_2"],
        "year3": counts["year_3"],
        "year4": counts["year_4"],
        "year5": counts["year_5"],
        "pp_failures": pp_failures,
        "play_protect_prompted": pp_prompted,
        "play_protect_prompt_rate_of_downloaders_pct": pct(pp_prompted, downloaded),
        "play_protect_failure_rate_of_prompted_pct": pct(pp_failures, pp_prompted),
        "discovery_to_download_pct": pct(downloaded, discovered),
        "download_to_install_pct": pct(installed, downloaded),
        "install_to_day30_pct": pct(counts["day_30"], installed),
        "install_to_day60_pct": pct(counts["day_60"], installed),
        "install_to_year1_pct": pct(counts["year_1"], installed),
        "install_to_year5_pct": pct(counts["year_5"], installed),
        "discovery_to_year5_pct": pct(counts["year_5"], discovered),
        "end_to_end_pct": pct(counts["year_5"], n),
        "stage_rows": stage_rows,
    }

    by_arch = []
    for arch in ARCHETYPES:
        group = [a for a in agents if a["archetype"] == arch["name"]]
        g_installed = sum(a["_installed"] for a in group)
        g = {
            "archetype": arch["name"],
            "agents": len(group),
            "discovered": sum(a["_discovered"] for a in group),
            "downloaded": sum(a["_downloaded"] for a in group),
            "installed": g_installed,
            "day30": sum(bool(a["day_30"]) for a in group),
            "day60": sum(bool(a["day_60"]) for a in group),
            "day180": sum(bool(a["day_180"]) for a in group),
            "year1": sum(bool(a["year_1"]) for a in group),
            "year2": sum(bool(a["year_2"]) for a in group),
            "year3": sum(bool(a["year_3"]) for a in group),
            "year4": sum(bool(a["year_4"]) for a in group),
            "year5": sum(bool(a["year_5"]) for a in group),
            "pp_failures": sum(1 for a in group if a["_mode"] == "play_protect"),
            "pp_prompted": sum(1 for a in group if a["_pp_triggered"]),
            "update_losses": sum(1 for a in group if a["_mode"] == "update_save_loss"),
            "phone_losses": sum(1 for a in group if a["_mode"] == "phone_upgrade_loss"),
            "content_losses": sum(1 for a in group if a["_mode"] == "content_exhaustion"),
            "dormant_returners": sum(1 for a in group if a["_dormant_episodes"] > 0),
            "deep_attachment_users": sum(1 for a in group if a["_installed"] and a["_attachment"] >= 0.65),
            "avg_sentiment": round(statistics.mean(a["sentiment_score"] for a in group), 2),
            "avg_journal": round(statistics.mean(a["lifetime_journal_entries"] for a in group), 1),
            "year5_of_installed_pct": pct(sum(bool(a["year_5"]) for a in group), g_installed),
            "year5_of_all_pct": pct(sum(bool(a["year_5"]) for a in group), len(group)),
        }
        by_arch.append(g)

    by_wave = []
    for label, _ in WAVE_BASE:
        group = [a for a in agents if a["discovery_wave"] == label]
        g_installed = sum(a["_installed"] for a in group)
        by_wave.append({
            "wave": label,
            "wave_type": wave_type(label),
            "discovered": len(group),
            "installed": g_installed,
            "day30": sum(bool(a["day_30"]) for a in group),
            "year1": sum(bool(a["year_1"]) for a in group),
            "year5": sum(bool(a["year_5"]) for a in group),
            "year5_of_installed_pct": pct(sum(bool(a["year_5"]) for a in group), g_installed),
            "avg_sentiment": round(statistics.mean(a["sentiment_score"] for a in group), 2) if group else 0.0,
        })

    stage_counts = Counter(a["churn_stage"] for a in agents)
    reasons = Counter(a["primary_churn_reason"] for a in agents)
    modes = Counter(a["_mode"] for a in agents)
    dormant_reactions = Counter(a["_dormant_reaction"] for a in agents if a["_dormant_episodes"] > 0)
    dormant_returners = sum(1 for a in agents if a["_dormant_episodes"] > 0)
    dormant_episodes = sum(a["_dormant_episodes"] for a in agents)

    journals = [a["lifetime_journal_entries"] for a in agents]
    installed_journals = [a["lifetime_journal_entries"] for a in agents if a["_installed"]]
    journal_over_cap = sum(1 for j in journals if j > JOURNAL_CAP)
    journal_over_cap_installed = sum(1 for j in installed_journals if j > JOURNAL_CAP)
    saved_entries = sum(min(j, JOURNAL_CAP) for j in journals)

    update_losses = sum(1 for a in agents if a["_mode"] == "update_save_loss")
    phone_losses = sum(1 for a in agents if a["_mode"] == "phone_upgrade_loss")
    content_losses = sum(1 for a in agents if a["_mode"] == "content_exhaustion")

    summary = {
        "seed": SEED,
        "population": n,
        "checkpoints_days": CHECKPOINTS,
        "cohort_clock": "Each user is tracked for five years from their own discovery day; waves describe acquisition timing, not calendar prevalence.",
        "funnel": funnel,
        "by_archetype": by_arch,
        "by_wave": by_wave,
        "discovery": {
            "discovered": discovered,
            "never_discovered": never,
            "seasonal_wave_users": sum(1 for a in agents if a["_wave_type"] == "Seasonal"),
            "wave_counts": dict(Counter(a["discovery_wave"] for a in agents if a["_discovered"])),
            "wave_type_counts": dict(Counter(a["_wave_type"] for a in agents if a["_discovered"])),
        },
        "churn": {
            "stage_counts": dict(stage_counts.most_common()),
            "mode_counts": dict(modes.most_common()),
            "top_reasons": dict(reasons.most_common(30)),
            "update_save_losses": update_losses,
            "phone_upgrade_losses": phone_losses,
            "content_exhaustion_losses": content_losses,
            "play_protect_losses": pp_failures,
            "soft_churn_losses": modes.get("soft_churn", 0),
            "no_download_losses": modes.get("no_download", 0),
            "never_discovered_losses": modes.get("never_discovered", 0),
        },
        "dormant": {
            "returners": dormant_returners,
            "episodes": dormant_episodes,
            "avg_episodes": round(dormant_episodes / dormant_returners, 2) if dormant_returners else 0.0,
            "reactions": dict(dormant_reactions.most_common()),
        },
        "journal": {
            "cap": JOURNAL_CAP,
            "avg_lifetime_entries": round(statistics.mean(journals), 1),
            "median_lifetime_entries": statistics.median(journals),
            "max_lifetime_entries": max(journals),
            "users_over_cap": journal_over_cap,
            "pct_users_over_cap": pct(journal_over_cap, n),
            "avg_lifetime_entries_installed": round(statistics.mean(installed_journals), 1) if installed_journals else 0.0,
            "median_lifetime_entries_installed": statistics.median(installed_journals) if installed_journals else 0,
            "installed_users_over_cap": journal_over_cap_installed,
            "pct_installed_users_over_cap": pct(journal_over_cap_installed, installed),
            "total_modeled_writes": sum(journals),
            "total_persistable_entries": saved_entries,
            "entries_lost_to_cap": sum(journals) - saved_entries,
        },
        "sentiment": {
            "mean_all": round(statistics.mean(a["sentiment_score"] for a in agents), 2),
            "mean_installed": round(statistics.mean(a["sentiment_score"] for a in agents if a["_installed"]), 2),
            "mean_year5": round(statistics.mean(a["sentiment_score"] for a in agents if a["year_5"]), 2),
            "mean_update_loss": round(statistics.mean(a["sentiment_score"] for a in agents if a["_mode"] == "update_save_loss"), 2) if update_losses else 0.0,
            "mean_phone_loss": round(statistics.mean(a["sentiment_score"] for a in agents if a["_mode"] == "phone_upgrade_loss"), 2) if phone_losses else 0.0,
            "mean_content_loss": round(statistics.mean(a["sentiment_score"] for a in agents if a["_mode"] == "content_exhaustion"), 2) if content_losses else 0.0,
            "mean_play_protect_loss": round(statistics.mean(a["sentiment_score"] for a in agents if a["_mode"] == "play_protect"), 2) if pp_failures else 0.0,
        },
        "hazards": {
            "catch_up_max_days": CATCH_UP_MAX_DAYS,
            "sim_step_minutes": SIM_STEP_MINUTES,
            "update_save_loss_rate_of_installed_pct": pct(update_losses, installed),
            "phone_upgrade_loss_rate_of_installed_pct": pct(phone_losses, installed),
            "content_exhaustion_rate_of_installed_pct": pct(content_losses, installed),
            "play_protect_failure_rate_of_downloaded_pct": pct(pp_failures, downloaded),
        },
        "codebase_audit": {
            "save_schema": SCHEMA,
            "save_storage": "single local JSON in filesDir, atomic tmp+rename, pre-migration .bak, corrupt-save copy",
            "export_import_ui": False,
            "allow_backup": False,
            "cloud_and_device_transfer": "all root/file/database/sharedpref/external domains excluded",
            "journal_cap": JOURNAL_CAP,
            "memory_cap": MEMORY_CAP,
            "event_cap": EVENT_CAP,
            "pet_memory_cap": PET_MEMORY_CAP,
            "catch_up_max_days": CATCH_UP_MAX_DAYS,
            "sim_step_minutes": SIM_STEP_MINUTES,
            "milestone_days": MILESTONE_DAYS,
            "moving_festival_years": MOVING_FESTIVAL_YEARS,
            "projects_authored": PROJECT_COUNT,
            "nib_tricks": NIB_TRICKS,
            "nib_stages": NIB_STAGES,
            "festivals": FESTIVAL_COUNT,
            "trait_bounds": [0.05, 0.95],
            "trait_initial_bounds": [0.15, 0.85],
        },
    }
    return summary


def main():
    rng_master = random.Random(SEED)
    pool = []
    for arch in ARCHETYPES:
        pool.extend([arch] * arch["count"])
    rng_master.shuffle(pool)

    agents = []
    for uid, arch in enumerate(pool, start=1):
        rng = random.Random(SEED + uid * 104729)
        agents.append(simulate_agent(uid, arch, rng))

    with open(CSV_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "user_id", "archetype", "discovery_wave", "passes_play_protect",
            "day_30", "day_60", "day_180", "year_1", "year_2", "year_3",
            "year_4", "year_5", "churn_stage", "primary_churn_reason",
            "lifetime_journal_entries", "sentiment_score",
        ])
        writer.writeheader()
        for a in agents:
            writer.writerow({k: (str(a[k]).lower() if isinstance(a[k], bool) else a[k]) for k in writer.fieldnames})

    summary = summary_of(agents)

    with open(SUMMARY_PATH, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print("\n=== PIPO 25,000-USER / 5-YEAR RETENTION ===")
    print(f"{'Stage':<34}{'Count':>8}{'Stage->':>10}{'Of total':>10}{'Of installed':>14}")
    for row in summary["funnel"]["stage_rows"]:
        oi = row["of_installed_pct"]
        oi_text = f"{oi:>13.1f}%" if oi is not None else f"{'-':>14}"
        print(f"{row['stage']:<34}{row['count']:>8}{row['stage_to_stage_pct']:>9.1f}%"
              f"{row['of_total_pct']:>9.1f}%{oi_text}")

    print("\n=== YEAR-5 SURVIVAL BY ARCHETYPE ===")
    print(f"{'Archetype':<40}{'Installed':>10}{'Year 5':>8}{'% of inst.':>12}")
    for a in summary["by_archetype"]:
        print(f"{a['archetype']:<40}{a['installed']:>10}{a['year5']:>8}{a['year5_of_installed_pct']:>11.1f}%")

    print("\n=== LONG-TERM FAILURE MODES ===")
    print(f"Play Protect losses:      {summary['churn']['play_protect_losses']:>7}")
    print(f"APK update save wipes:   {summary['churn']['update_save_losses']:>7}")
    print(f"Phone upgrade losses:    {summary['churn']['phone_upgrade_losses']:>7}")
    print(f"Content exhaustion:      {summary['churn']['content_exhaustion_losses']:>7}")
    print(f"Soft churn / life:       {summary['churn']['soft_churn_losses']:>7}")
    print(f"Never downloaded:        {summary['churn']['no_download_losses']:>7}")
    print(f"Never discovered:        {summary['churn']['never_discovered_losses']:>7}")

    print("\n=== DORMANT RETURNERS ===")
    print(f"Returners: {summary['dormant']['returners']}  Episodes: {summary['dormant']['episodes']}")
    for k, v in summary["dormant"]["reactions"].items():
        print(f"  {k}: {v}")

    print("\n=== JOURNAL SATURATION ===")
    j = summary["journal"]
    print(f"Avg lifetime writes: {j['avg_lifetime_entries']}  Median: {j['median_lifetime_entries']}  Max: {j['max_lifetime_entries']}")
    print(f"Users over the {j['cap']}-entry save cap: {j['users_over_cap']} ({j['pct_users_over_cap']}%)")
    print(f"Entries lost to cap: {j['entries_lost_to_cap']}")

    print(f"\nWrote {CSV_PATH} ({len(agents)} rows) and {SUMMARY_PATH}")


if __name__ == "__main__":
    main()
