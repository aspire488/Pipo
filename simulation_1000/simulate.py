#!/usr/bin/env python3
"""
Pipo 1,000-user market simulation (deterministic).

Each agent is an independent profile with a seeded RNG. The five rounds are modelled
as a funnel plus stage-specific community output:

  Round 1 Discovery      -> discovers_app
  Round 2 Install funnel -> clicks_download, passes_play_protect
  Round 3 First 72 hours -> day_7_active
  Round 4 Social debate  -> reflected in sample_post / sentiment
  Round 5 Day-30 outcome -> day_30_retained

No network, no LLM, no personal data. Re-running with the same seed reproduces the
same 1,000 rows exactly.
"""

import csv
import json
import os
import random
import statistics
import sys
from collections import Counter, defaultdict

# Windows consoles default to cp1252; the report text contains arrows and curly quotes.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SEED = 488
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(BASE_DIR, "agents_1000.csv")
SUMMARY_PATH = os.path.join(BASE_DIR, "summary.json")

# ---------------------------------------------------------------------------
# 10 archetypes, exact counts sum to 1,000.
# tech: inclusive range (1-10). probabilities are calibrated priors, not measurements.
# ---------------------------------------------------------------------------
ARCHETYPES = [
    dict(name="Android Power User", count=140, tech=(8, 10), discover=0.94, download=0.82,
         pp=0.92, d7=0.68, d30=0.52, sentiment=0.28,
         channels={"r/Android": 45, "X/Twitter": 25, "Product Hunt": 15, "r/IndieGaming": 15}),
    dict(name="Cozy Virtual-Pet Fan", count=150, tech=(3, 7), discover=0.88, download=0.78,
         pp=0.55, d7=0.80, d30=0.67, sentiment=0.45,
         channels={"r/IndieGaming": 35, "Product Hunt": 30, "X/Twitter": 25, "r/Android": 10}),
    dict(name="Open-Source / Privacy Advocate", count=110, tech=(7, 10), discover=0.90, download=0.85,
         pp=0.95, d7=0.72, d30=0.58, sentiment=0.36,
         channels={"r/Android": 40, "X/Twitter": 35, "Product Hunt": 15, "r/IndieGaming": 10}),
    dict(name="Casual Play Protect-Wary User", count=130, tech=(2, 5), discover=0.78, download=0.62,
         pp=0.30, d7=0.55, d30=0.33, sentiment=-0.05,
         channels={"r/Android": 30, "Product Hunt": 30, "X/Twitter": 25, "r/IndieGaming": 15}),
    dict(name="Indie Developer / Maker", count=90, tech=(7, 10), discover=0.92, download=0.88,
         pp=0.90, d7=0.65, d30=0.45, sentiment=0.30,
         channels={"Product Hunt": 35, "r/Android": 25, "X/Twitter": 25, "r/IndieGaming": 15}),
    dict(name="Battery-Conscious Minimalist", count=80, tech=(6, 9), discover=0.82, download=0.66,
         pp=0.78, d7=0.58, d30=0.36, sentiment=0.05,
         channels={"r/Android": 55, "X/Twitter": 20, "Product Hunt": 15, "r/IndieGaming": 10}),
    dict(name="Bored Student / Scroller", count=100, tech=(4, 8), discover=0.86, download=0.72,
         pp=0.60, d7=0.62, d30=0.34, sentiment=0.10,
         channels={"r/IndieGaming": 30, "X/Twitter": 35, "Product Hunt": 20, "r/Android": 15}),
    dict(name="Virtual-Pet Skeptic / AI-Slop Watchdog", count=70, tech=(5, 8), discover=0.84, download=0.58,
         pp=0.70, d7=0.40, d30=0.18, sentiment=-0.20,
         channels={"r/Android": 35, "X/Twitter": 35, "Product Hunt": 15, "r/IndieGaming": 15}),
    dict(name="Parent / Digital-Detox Buyer", count=60, tech=(2, 5), discover=0.70, download=0.55,
         pp=0.35, d7=0.70, d30=0.55, sentiment=0.32,
         channels={"Product Hunt": 40, "r/IndieGaming": 30, "X/Twitter": 20, "r/Android": 10}),
    dict(name="Retro / Indie Game Browser", count=70, tech=(5, 8), discover=0.80, download=0.65,
         pp=0.62, d7=0.60, d30=0.38, sentiment=0.16,
         channels={"r/IndieGaming": 45, "Product Hunt": 25, "X/Twitter": 20, "r/Android": 10}),
]

assert sum(a["count"] for a in ARCHETYPES) == 1000

CHURN_EARLY = [
    ("novelty faded; nothing pulled me back", 30),
    ("battery / 3D room cost", 12),
    ("forgot it existed; notifications too quiet", 22),
    ("wanted a game loop, got a diary", 14),
    ("sideload distrust after the warning", 10),
    ("no Play Store updates", 8),
    ("screen-time caps felt limiting", 4),
]
CHURN_MONTH = [
    ("novelty faded after the first milestones", 26),
    ("no auto-updates / manual GitHub check", 20),
    ("lost save or reinstalled; no cloud backup", 12),
    ("battery / performance on older phone", 10),
    ("wanted deeper agency or games", 16),
    ("notifications too rare to remember it", 16),
]
BOUNCE_REASONS = [
    ("not installing an APK from GitHub", 30),
    ("unknown-sources / Play Protect fear", 28),
    ("no Play Store listing / no trust signal", 18),
    ("not my genre after reading more", 10),
    ("26.1 MB + optional 40 MB voice is too much", 6),
    ("waiting for a store version", 8),
]
PP_FAIL_REASONS = [
    ("Play Protect warning scared me off", 42),
    ("couldn't find 'Install anyway' / gave up", 26),
    ("didn't trust an unverified GitHub APK", 20),
    ("download or package error, no retry", 12),
]

# ---------------------------------------------------------------------------
# Voice pools. Stage bodies are shared; archetype extras add the specific hooks.
# ---------------------------------------------------------------------------
STAGE_BODIES = {
    "unseen": [
        "Didn't see it in my feed. The algorithm owes me one.",
        "No idea this existed. GitHub-only apps are invisible to me.",
        "Missed the launch entirely. Link?",
        "Saw a screenshot once, then lost the thread. Never found the APK.",
        "Never surfaced on my timeline. Sideloaded Android apps rarely do.",
    ],
    "bounce_download": {
        "neg": [
            "Cute concept, but I'm not enabling unknown sources for a pet app. {extra}",
            "Watched the demo twice. Then remembered my bank apps live on this phone. Skipping the sideload.",
            "26.1 MB is small, but 'download APK from GitHub' is where I get off. No Play Store, no install.",
            "The Play Protect warning in the comments scared me off before I even tapped download.",
            "I like the idea of an offline robot with no ads. I don't like it enough to override my phone's security warning.",
        ],
        "pos": [
            "Concept is great and 26.1 MB is nothing, but the install dance (unknown sources + warning) lost me at step two.",
            "I would have installed this tonight if it were on the Play Store. Not brave enough for a GitHub APK.",
            "Love that it's offline, MIT and ad-free. Still not sideloading an app I found on Reddit.",
        ],
    },
    "blocked_pp": {
        "neg": [
            "Downloaded 26.1 MB, opened it, Play Protect said 'unsafe'. Deleted the APK. Not worth the risk.",
            "Play Protect blocked it and I couldn't find 'Install anyway'. Gave up. Fix the install docs.",
            "Google says unverified, my phone says don't. I listened to my phone.",
            "'App may be harmful' is all I saw. No way I'm overriding that for a robot game.",
            "The warning is doing its job: unknown developer, notification access, brand-new cert. I'm out.",
        ],
        "pos": [
            "Play Protect flagged it — that's a reputation warning for a new sideloaded certificate, not a malware hit. Still, most people won't dig through More details for a virtual pet.",
            "I know unverified isn't malicious, but the prompt arrives before any context. Bad funnel.",
        ],
    },
    "churn_early": {
        "pos": [
            "Installed fine. Pipo is charming and Nib stole my marble on day one. But I opened it twice and forgot it existed.",
            "The first session is lovely — Canvas art, little moods, a handwritten journal. Then the loop is just... waiting.",
            "No ads, no account, 26.1 MB. I wanted to love it, but nothing pulled me back on day three.",
            "Pipo's away-log is genuinely delightful. I just need a reason to open it before day seven.",
        ],
        "neg": [
            "Uninstalled after two days. Cute art, but the 'autonomous life' is mostly a sleeping robot and a journal I have to go find.",
            "The 3D room default drained my battery and there are no store updates. Back to my usual pet game.",
            "Expected a game, got a screensaver with a diary. Free, so I lost nothing but the install.",
            "Nib is funny once. After that the app asks for time without offering a session.",
        ],
    },
    "churn_month": {
        "pos": [
            "Played for two weeks. Nib learning tricks was the highlight. Then the milestones slowed down and I drifted.",
            "Genuinely novel for a fortnight. Ten traits and twelve moods are real, not marketing. Day 30 just needs a reason to return.",
            "Loved the Diwali room and the journal. Manual sideload updates are why I stopped checking.",
            "It earned its place for a month. I left because the phone got full, not because Pipo did anything wrong.",
        ],
        "neg": [
            "A month later: no updates, no cloud save, no reason to reinstall after a reset. The save-loss warning is real.",
            "The no-Play-Store thing killed it. I don't want to manually check GitHub for a pet app.",
            "Nib is great. Everything around Nib is too quiet — notifications are so rare I forgot it was installed.",
            "The simulation is impressive, but the loop is passive. I want to do things with Pipo, not read what he did.",
        ],
    },
    "retained": {
        "pos": [
            "Day 30: still checking Pipo's journal every morning. Nib learned high-five, stole a gear and hid it under the bed. 26.1 MB well spent.",
            "This is the only 'AI pet' I've kept. No ads, no analytics, no account. Just a robot with ten traits, twelve moods and a tiny menace.",
            "Privacy win: MIT, offline-first, state on-device. Play Protect warned once, the README explained it, Install anyway worked. Zero regrets.",
            "The offline catch-up is magic — open it after three days and he's had a whole life. Found a pebble, burnt noodles, Nib napped in his bed.",
            "Day 30 and Pipo still surprises me. He rearranged the room, recorded a sound and wrote a note about it. This is the cozy game I wanted.",
        ],
        "neg": [
            "Still installed at day 30, but only because Nib is funny. The 3D room is heavy and updates are manual. Please ship on a store.",
            "Kept it for the journal. I'd happily pay for a Play Store version with auto-updates. Not for ads, though.",
            "Pipo is a nice object. I'm not recommending it to friends until installing stops feeling like a security decision.",
        ],
    },
}

ARCH_EXTRAS = {
    "Android Power User": [
        "Sideloaded on a Pixel in under a minute: Play Protect → More details → Install anyway. 26.1 MB and no trackers is how Android should ship.",
        "Per-app battery check: light in the classic room, heavier in 3D. No always-on server is the right architecture.",
        "Obtainium handles updates from the GitHub release. The APK hash matched. Clean install.",
    ],
    "Cozy Virtual-Pet Fan": [
        "Nib's little beeps and the notebook journal got me. I check what they did while I was at work.",
        "Pipo had a grumpy day and then Nib sat on his project. I've never been so invested in two rectangles.",
        "The handwriting journal, the doodles in the margin, the tiny naps. It feels handmade in the best way.",
    ],
    "Open-Source / Privacy Advocate": [
        "MIT license, no analytics, no ads, no account, and I read the Play Protect audit. Unverified is not malicious.",
        "The save is one local JSON file. No cloud, no login, no telemetry. This is the default I want for pet apps.",
        "Optional AI is off unless you configure a proxy. The core sim never needs the network. Good architecture.",
    ],
    "Casual Play Protect-Wary User": [
        "Still wish this was on the Play Store. GitHub links and scary warnings are a lot to ask.",
        "The README helped, but I had to find it after the warning. Put the fix on the download page.",
        "I don't know what a signing certificate is. I just know my phone said the app might be harmful.",
    ],
    "Indie Developer / Maker": [
        "The deterministic engine is the interesting part: ten traits with bounded drift, twelve derived moods, 40-minute offline reconciliation.",
        "No account, no backend, one JSON save and a Compose Canvas room. This is a masterclass in shipping small.",
        "Nib's state machine is a better companion design than most LLM wrappers. Authored, testable, cheap to run.",
    ],
    "Battery-Conscious Minimalist": [
        "Turned off the 3D room; the classic room is much lighter. 26.1 MB base and no background server.",
        "Idle drain was noticeable until I switched to the painted room. More apps should ship that toggle.",
        "No ads means no wake-locks for ad SDKs. I can leave it installed without resenting it.",
    ],
    "Bored Student / Scroller": [
        "Pipo's journal is better than most feeds. Nib stealing shiny things is the funniest thing on my phone.",
        "Opened it between lectures. He was out at the bakery, so the room was empty and there was a note on the door. Actual personality.",
        "The notification where Nib knocks a gear off the shelf made me laugh out loud in a quiet library.",
    ],
    "Virtual-Pet Skeptic / AI-Slop Watchdog": [
        "It's not an LLM pet — all authored and deterministic. Refreshing, though the day-30 loop needs more agency.",
        "No ads is the bare minimum, but the offline claim checks out. I verified with airplane mode; Pipo still had a day.",
        "The writing is good enough that I stopped looking for the trick. Still not convinced the sim has enough to do.",
    ],
    "Parent / Digital-Detox Buyer": [
        "No ads, no purchases, no account. My kid asks what Nib did today instead of asking for a store card.",
        "I don't have to worry about a chat window or strangers. It's just a little robot in a room.",
        "The install warning was scary, but the app itself is the calmest thing on the tablet.",
    ],
    "Retro / Indie Game Browser": [
        "The Canvas art and tiny idle animations are gorgeous. Nib's three-beep song is perfect.",
        "Found it on itch and installed the APK. It has the energy of a 90s virtual pet with modern rendering.",
        "The pixel-adjacent room, the handwritten journal, the little synth beeps. Strong aesthetic identity.",
    ],
}

MOODS = ["HAPPY", "EXCITED", "CURIOUS", "SLEEPY", "BORED", "GRUMPY", "LONELY",
         "NERVOUS", "PROUD", "EMBARRASSED", "MISCHIEVOUS", "RELAXED",
         "WORRIED", "THOUGHTFUL", "PLAYFUL"]
TRAITS = ["curiosity", "playfulness", "mischief", "affection", "confidence",
          "sociability", "patience", "laziness", "adventurousness", "stubbornness"]
TRAIT_BEHAVIOR = {
    "curiosity": "keeps examining things",
    "playfulness": "keeps starting games",
    "mischief": "keeps hiding things",
    "affection": "keeps asking for pats",
    "confidence": "keeps announcing his plans",
    "sociability": "keeps going out",
    "patience": "takes his time with things",
    "laziness": "keeps napping",
    "adventurousness": "keeps exploring",
    "stubbornness": "keeps refusing suggestions",
}
NIBS = ["stole a blue marble", "learned high-five", "knocked a gear off the shelf",
        "napped in Pipo's bed", "chased a squirrel and lost", "hid a button under the bed",
        "sang three beeps and one extra beep", "sat on the project", "stared at the window for an hour"]


def weighted_pick(rng, pairs):
    total = sum(w for _, w in pairs)
    r = rng.random() * total
    for value, weight in pairs:
        r -= weight
        if r <= 0:
            return value
    return pairs[-1][0]


def scored(prob, rng, floor=0.0, ceil=0.98):
    return max(floor, min(ceil, prob))


def compose_post(agent, stage, rng):
    if stage == "unseen":
        return rng.choice(STAGE_BODIES["unseen"])

    if stage == "bounce_download":
        pool = STAGE_BODIES["bounce_download"]["pos" if agent["sentiment"] >= 0 else "neg"]
    elif stage == "blocked_pp":
        pool = STAGE_BODIES["blocked_pp"]["pos" if agent["sentiment"] >= -0.15 else "neg"]
    else:
        pool = STAGE_BODIES[stage]["pos" if agent["sentiment"] >= 0 else "neg"]

    body = rng.choice(pool)
    extra = ""
    if stage in {"bounce_download", "churn_early", "churn_month", "retained"}:
        extra = rng.choice(ARCH_EXTRAS[agent["archetype"]])

    text = body.replace("{extra}", extra).strip()
    if extra and "{extra}" not in body:
        text = f"{text} {extra}"

    # Sprinkle concrete engine details so the quote reads like a real user observation.
    if stage in {"churn_early", "churn_month", "retained"} and rng.random() < 0.55:
        kind = rng.choice(["mood", "trait", "nib"])
        if kind == "mood":
            detail = f"He was {rng.choice(MOODS).lower()} when I opened it."
        elif kind == "trait":
            trait = rng.choice(TRAITS)
            detail = f"His {trait} is clearly high; he {TRAIT_BEHAVIOR[trait]}."
        else:
            detail = f"Nib {rng.choice(NIBS)}."
        text = f"{text} {detail}"
    return " ".join(text.split())


def simulate_agent(user_id, arch, rng):
    tech = rng.randint(*arch["tech"])

    # Round 1 — discovery.
    discover_p = scored(arch["discover"] + (tech - 5.5) * 0.012, rng, 0.35, 0.98)
    discovers = rng.random() < discover_p
    channel = weighted_pick(rng, list(arch["channels"].items()))

    # Round 2 — download + Play Protect.
    download_p = scored(arch["download"] + (tech - 5.5) * 0.020, rng, 0.20, 0.95)
    clicks = discovers and rng.random() < download_p

    readme_reader = rng.random() < min(0.85, 0.30 + tech * 0.035)
    pp_triggered = clicks and rng.random() < 0.66
    pp_p = scored(arch["pp"] + (tech - 5.0) * 0.035 + (0.07 if readme_reader else 0.0), rng, 0.08, 0.97)
    passes_pp = clicks and ((not pp_triggered) or rng.random() < pp_p)
    installed = passes_pp

    # Round 3 — first 72 hours.
    d7_p = scored(arch["d7"] + (tech - 5.5) * 0.010, rng, 0.15, 0.92)
    day7 = installed and rng.random() < d7_p

    # Round 5 — day 30.
    d30_p = scored(arch["d30"] + (tech - 5.5) * 0.008, rng, 0.05, 0.85)
    day30 = day7 and rng.random() < d30_p

    aha_nib = installed and rng.random() < 0.46

    # Sentiment: concept vs. actual experience.
    sentiment = arch["sentiment"] + rng.uniform(-0.14, 0.14)
    if not discovers:
        sentiment -= 0.03
    elif clicks and not installed:
        sentiment -= 0.55
    elif installed and not day7:
        sentiment -= 0.12
    elif day7 and not day30:
        sentiment += 0.03
    elif day30:
        sentiment += 0.34
    if aha_nib and installed:
        sentiment += 0.09
    if not readme_reader and pp_triggered and not installed:
        sentiment -= 0.05
    sentiment = round(max(-1.0, min(1.0, sentiment)), 2)

    # Stage + internal reasons.
    if not discovers:
        stage, reason = "unseen", "never reached by the launch"
    elif not clicks:
        stage = "bounce_download"
        reason = weighted_pick(rng, BOUNCE_REASONS)
    elif not installed:
        stage = "blocked_pp"
        reason = weighted_pick(rng, PP_FAIL_REASONS)
    elif not day7:
        stage = "churn_early"
        reason = weighted_pick(rng, CHURN_EARLY)
    elif not day30:
        stage = "churn_month"
        reason = weighted_pick(rng, CHURN_MONTH)
    else:
        stage, reason = "retained", "still checking in"

    agent = dict(
        user_id=user_id,
        archetype=arch["name"],
        tech=tech,
        discovers=discovers,
        clicks=clicks,
        passes_pp=passes_pp,
        day7=day7,
        day30=day30,
        sentiment=sentiment,
        channel=channel,
        stage=stage,
        reason=reason,
        pp_triggered=pp_triggered,
        readme_reader=readme_reader,
        aha_nib=aha_nib,
    )
    agent["sample_post"] = compose_post(agent, stage, rng)
    return agent


def keyword_counts(posts):
    hooks = {
        "Nib the pet": ["nib"],
        "Offline-first / on-device": ["offline", "on-device", "local json", "no server"],
        "No ads / analytics / account": ["no ads", "no analytics", "no account", "no trackers"],
        "Only 26.1 MB": ["26.1", "26 mb"],
        "Compose Canvas art": ["canvas", "art", "animation", "handmade"],
        "10 traits + 12 moods": ["ten traits", "10 traits", "twelve moods", "12 moods", "traits", "moods"],
        "Journal / away log": ["journal", "diary", "away-log", "away log"],
        "MIT / open source": ["mit", "open-source", "open source"],
        "Play Protect friction": ["play protect", "unverified", "unknown sources", "sideload"],
        "Battery / 3D room": ["battery", "3d room", "painted room", "heavy"],
    }
    lowered = [p.lower() for p in posts]
    return {
        label: sum(1 for p in lowered if any(k in p for k in keys))
        for label, keys in hooks.items()
    }


def main():
    rng_master = random.Random(SEED)
    agents = []
    uid = 0
    for arch in ARCHETYPES:
        for _ in range(arch["count"]):
            uid += 1
            agent_rng = random.Random(SEED + uid * 104729)
            agents.append(simulate_agent(uid, arch, agent_rng))

    # Deterministic but "social" ordering: nobody is numbered by archetype blocks in the CSV.
    rng_master.shuffle(agents)
    # Keep stable user_id order for readability and reproducibility.
    agents.sort(key=lambda a: a["user_id"])

    with open(CSV_PATH, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=[
            "user_id", "archetype", "tech_savviness", "discovers_app", "clicks_download",
            "passes_play_protect", "day_7_active", "day_30_retained", "sentiment_score",
            "sample_post",
        ])
        writer.writeheader()
        for a in agents:
            writer.writerow({
                "user_id": a["user_id"],
                "archetype": a["archetype"],
                "tech_savviness": a["tech"],
                "discovers_app": str(a["discovers"]).lower(),
                "clicks_download": str(a["clicks"]).lower(),
                "passes_play_protect": str(a["passes_pp"]).lower(),
                "day_7_active": str(a["day7"]).lower(),
                "day_30_retained": str(a["day30"]).lower(),
                "sentiment_score": f'{a["sentiment"]:.2f}',
                "sample_post": a["sample_post"],
            })

    n = len(agents)
    discovered = sum(a["discovers"] for a in agents)
    clicks = sum(a["clicks"] for a in agents)
    installs = sum(a["passes_pp"] for a in agents)
    d7 = sum(a["day7"] for a in agents)
    d30 = sum(a["day30"] for a in agents)
    pp_triggered = sum(a["pp_triggered"] for a in agents)
    pp_failed = sum(1 for a in agents if a["pp_triggered"] and not a["passes_pp"])

    def rate(a, b):
        return round(100.0 * a / b, 1) if b else 0.0

    funnel = {
        "agents": n,
        "discovered": discovered,
        "downloaded": clicks,
        "installed": installs,
        "day7": d7,
        "day30": d30,
        "discovery_rate_pct": rate(discovered, n),
        "download_rate_of_discovered_pct": rate(clicks, discovered),
        "discovery_to_download_pct": rate(clicks, n),
        "play_protect_prompted": pp_triggered,
        "play_protect_prompt_rate_of_downloaders_pct": rate(pp_triggered, clicks),
        "play_protect_failed": pp_failed,
        "play_protect_bypass_rate_pct": rate(clicks - pp_failed, clicks),
        "play_protect_fail_rate_of_prompted_pct": rate(pp_failed, pp_triggered),
        "install_rate_of_download_pct": rate(installs, clicks),
        "install_rate_of_all_pct": rate(installs, n),
        "day7_rate_of_installed_pct": rate(d7, installs),
        "day30_rate_of_day7_pct": rate(d30, d7),
        "day30_rate_of_installed_pct": rate(d30, installs),
        "day30_rate_of_all_pct": rate(d30, n),
        "end_to_end_pct": rate(d30, n),
        "avg_sentiment": round(statistics.mean(a["sentiment"] for a in agents), 2),
        "avg_sentiment_installed": round(statistics.mean(a["sentiment"] for a in agents if a["passes_pp"]), 2),
        "avg_sentiment_retained": round(statistics.mean(a["sentiment"] for a in agents if a["day30"]), 2),
    }

    by_arch = []
    for arch in ARCHETYPES:
        group = [a for a in agents if a["archetype"] == arch["name"]]
        by_arch.append({
            "archetype": arch["name"],
            "agents": len(group),
            "discovered": sum(a["discovers"] for a in group),
            "downloaded": sum(a["clicks"] for a in group),
            "installed": sum(a["passes_pp"] for a in group),
            "day7": sum(a["day7"] for a in group),
            "day30": sum(a["day30"] for a in group),
            "discovery_to_install_pct": rate(sum(a["passes_pp"] for a in group), len(group)),
            "install_to_day30_pct": rate(sum(a["day30"] for a in group), sum(a["passes_pp"] for a in group)),
            "day30_of_all_pct": rate(sum(a["day30"] for a in group), len(group)),
            "avg_sentiment": round(statistics.mean(a["sentiment"] for a in group), 2),
            "pp_failures": sum(1 for a in group if a["pp_triggered"] and not a["passes_pp"]),
            "avg_tech": round(statistics.mean(a["tech"] for a in group), 1),
        })

    stage_counts = Counter(a["stage"] for a in agents)
    channel_counts = Counter(a["channel"] for a in agents)
    bounce_reasons = Counter(a["reason"] for a in agents if a["stage"] == "bounce_download")
    pp_reasons = Counter(a["reason"] for a in agents if a["stage"] == "blocked_pp")
    churn_early = Counter(a["reason"] for a in agents if a["stage"] == "churn_early")
    churn_month = Counter(a["reason"] for a in agents if a["stage"] == "churn_month")

    engaged_posts = [a["sample_post"] for a in agents if a["discovers"]]
    installed_posts = [a["sample_post"] for a in agents if a["passes_pp"]]
    hooks_all = keyword_counts([a["sample_post"] for a in agents])
    hooks_engaged = keyword_counts(engaged_posts)
    hooks_installed = keyword_counts(installed_posts)

    quotes = {}
    for arch in ARCHETYPES:
        name = arch["name"]
        retained = next((a["sample_post"] for a in agents if a["archetype"] == name and a["day30"]), None)
        blocked = next((a["sample_post"] for a in agents if a["archetype"] == name and a["stage"] == "blocked_pp"), None)
        churned = next((a["sample_post"] for a in agents if a["archetype"] == name and a["stage"] == "churn_month"), None)
        quotes[name] = {"retained": retained, "blocked_play_protect": blocked, "churned_month": churned}

    summary = {
        "seed": SEED,
        "funnel": funnel,
        "by_archetype": by_arch,
        "stage_counts": dict(stage_counts),
        "channel_counts": dict(channel_counts),
        "bounce_reasons": dict(bounce_reasons.most_common()),
        "pp_fail_reasons": dict(pp_reasons.most_common()),
        "churn_early_reasons": dict(churn_early.most_common()),
        "churn_month_reasons": dict(churn_month.most_common()),
        "hooks_all_posts": hooks_all,
        "hooks_engaged_posts": hooks_engaged,
        "hooks_installed_posts": hooks_installed,
        "quotes": quotes,
    }
    with open(SUMMARY_PATH, "w", encoding="utf-8") as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)

    print(json.dumps(summary, indent=2, ensure_ascii=False))
    print(f"\nWrote {CSV_PATH} ({n} rows) and {SUMMARY_PATH}")


if __name__ == "__main__":
    main()
