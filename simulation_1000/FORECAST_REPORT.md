# Pipo — Synthetic 1,000-User Market & Social Simulation Forecast

**Prepared:** 13 November 2026<br>
**Scope:** 1,000 synthetic agents, 10 archetypes, 5 sequential rounds across Reddit (r/Android, r/IndieGaming), Product Hunt and X/Twitter.<br>
**Inputs:** `README.md`, `app/src/main` (engine, onboarding, permissions, settings), and the repository's own launch/security documentation.<br>
**Reproduce:** `python simulation_1000/simulate.py` (stdlib only, deterministic seed `488`, no network/LLM). The script writes `agents_1000.csv` and `summary.json` locally; those generated artifacts are gitignored.

> **Synthetic projection, not user analytics.** Every count, percentage and quote below is a model output from simulated agents. None of it is measured installs, retention or market research, and this is not a forecast with confidence intervals. The priors were set from the code and honest launch constraints, then varied per agent. Use the **ranking of bottlenecks and the direction of the fixes**, not the absolute percentages. The real baseline on 10 Nov 2026 was **7 GitHub APK downloads and 1 star**; this scenario asks what happens *if 1,000 users are exposed*, not what will happen next week.

---

## 1. Executive summary

| Funnel stage | Users | Conditional conversion | Absolute (of 1,000) |
|---|---:|---:|---:|
| Exposed / discovered Pipo | **884** | 88.4% of all | 88.4% |
| APK downloaded | **671** | 75.9% of discoverers | 67.1% |
| Passed Play Protect / installed | **576** | 85.8% of downloaders | 57.6% |
| Active at Day 7 | **384** | 66.7% of installs | 38.4% |
| Retained at Day 30 | **219** | 57.0% of Day-7 users; 38.0% of installs | **21.9%** |

**Three conclusions**

1. **The install funnel, not the concept, is the biggest leak.** 329 of 1,000 users never download; 244 of the 781 total losses (31.2%) are trust/friction losses before first launch (149 bounced at the download decision, 95 abandoned at Play Protect). The product's first impression is a security warning, not Nib.
2. **The people who install mostly like it, then run out of reasons to return.** Installed users average **+0.37 sentiment**; retained users average **+0.66**. But 192 installs churn before Day 7 and another 165 between Day 7 and Day 30. The top reasons are novelty fade (111 users across both churn stages), notifications too quiet/forgotten (70), and no auto-updates or save backup (51).
3. **Nib and the journal are the shareable product; the feature list is not.** Among the 576 installed users' posts, **Nib is mentioned by 337 (58.5%)** and the journal/away log by **236 (41.0%)** — ahead of "no ads/analytics" (181), offline-first (160), traits/moods (137) and 26.1 MB (126). Lead with the tiny menace and the morning journal, then use privacy as the trust proof.

A realistic 30-day outcome for this population is **~22% retained**, concentrated in Android power users (45.0%), privacy advocates (35.5%) and cozy pet fans (29.3%). Casual Play Protect-wary users (5.4%) and virtual-pet skeptics (2.9%) are the weakest segments.

---

## 2. Method and agent model

Each agent has an independent seeded RNG and moves through five rounds:

| Round | What is simulated | Fields affected |
|---|---|---|
| 1. Discovery | Channel exposure and first-impression reaction | `discovers_app` |
| 2. Install funnel | Download decision, Play Protect prompt, willingness to proceed | `clicks_download`, `passes_play_protect` |
| 3. First 72 hours | First sessions, Nib beat, journal, battery/3D experience | `day_7_active` |
| 4. Social debate | Privacy vs. no-Play-Store arguments; positive/negative amplification | `sentiment_score`, `sample_post` |
| 5. Day 30 | Retention, churn reason, referral decision | `day_30_retained` |

**Archetypes and calibrated priors** (counts sum to 1,000):

| # | Archetype | Agents | Tech (1–10) | Discover | Download | Play Protect pass | D7 | D30 | Base sentiment |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | Android Power User | 140 | 8–10 | 94% | 82% | 92% | 68% | 52% | +0.28 |
| 2 | Cozy Virtual-Pet Fan | 150 | 3–7 | 88% | 78% | 55% | 80% | 67% | +0.45 |
| 3 | Open-Source / Privacy Advocate | 110 | 7–10 | 90% | 85% | 95% | 72% | 58% | +0.36 |
| 4 | Casual Play Protect-Wary User | 130 | 2–5 | 78% | 62% | 30% | 55% | 33% | −0.05 |
| 5 | Indie Developer / Maker | 90 | 7–10 | 92% | 88% | 90% | 65% | 45% | +0.30 |
| 6 | Battery-Conscious Minimalist | 80 | 6–9 | 82% | 66% | 78% | 58% | 36% | +0.05 |
| 7 | Bored Student / Scroller | 100 | 4–8 | 86% | 72% | 60% | 62% | 34% | +0.10 |
| 8 | Virtual-Pet Skeptic / AI-Slop Watchdog | 70 | 5–8 | 84% | 58% | 70% | 40% | 18% | −0.20 |
| 9 | Parent / Digital-Detox Buyer | 60 | 2–5 | 70% | 55% | 35% | 70% | 55% | +0.32 |
| 10 | Retro / Indie Game Browser | 70 | 5–8 | 80% | 65% | 62% | 60% | 38% | +0.16 |

Per agent, tech savviness shifts discovery/download/Play-Protect odds slightly. A README reader gets a further **+7 percentage points** on Play Protect pass, because the repository already documents the warning and the appeal path. Play Protect is modeled as triggering for **66% of downloaders** (429 users); when it does not trigger, the install proceeds.

**Funnel invariants checked:** every download implies discovery, every pass implies a download, every Day-7 user installed, and every Day-30 user was Day-7 active. All 1,000 IDs are unique; no empty posts.

---

## 3. Round-by-round findings

### Round 1 — Discovery (884 / 1,000)

Exposure is not the problem. The simulated channels split X/Twitter 296, r/Android 273, Product Hunt 232, r/IndieGaming 199. The 116 unseen users are a useful reminder that GitHub-only launches depend on the algorithm; nothing in the repo creates a discovery loop by itself.

The concept reads clearly in one line — *a small robot with his own life, plus a pet* — but the first comment often asks the wrong question: "Is this an AI chatbot?" and "Why isn't it on the Play Store?" Those two questions consume attention before the room, the journal or Nib can.

> "The concept is genuinely different: a small robot with his own day, not a chat window." — modeled power-user reaction

### Round 2 — Install funnel (671 downloaded → 576 installed)

This is the decisive round.

- **213 discoverers never download.** Reasons: 57 "not installing an APK from GitHub", 52 "unknown-sources / Play Protect fear", 40 "no Play Store listing / no trust signal", 25 "not my genre", 20 "waiting for a store version", 19 "26.1 MB + optional 40 MB voice is too much".
- **429 downloaders hit Play Protect.** 95 abandoned — 48 scared by the warning, 25 could not find or did not trust "Install anyway", 16 did not trust an unverified GitHub APK, 6 hit a package/download error and did not retry.
- Cozy pet fans and casual users each lost **25** users to Play Protect; parents lost **9 of only 22 downloaders (41%)**. Power users lost **1 of 121**.

> "Google says unverified, my phone says don't. I listened to my phone." — casual Play Protect-wary user, `blocked_pp`

> "Play Protect blocked it and I couldn't find 'Install anyway'. Gave up. Fix the install docs." — parent, `blocked_pp`

The README already contains correct, honest troubleshooting. The failure is **placement and promise**: the hero says "Install it in one tap", while the real path is download → open → warning → More details → Install anyway. Users who bounce never reach the collapsed troubleshooting block.

### Round 3 — First 72 hours (384 / 576 installs active at Day 7)

First-session sentiment is strong, especially for the right audience. The people who stay describe the journal, the away log, Nib's thefts and the small authored details. The people who leave describe a **passive loop** and **forgetting the app exists**.

- 192 installs churn before Day 7: 61 novelty faded / nothing pulled them back, 46 forgot it existed / notifications too quiet, 30 battery or 3D room cost, 22 expected a game and got a diary, 14 sideload distrust, 14 no store updates, 5 screen-time caps.
- The product's real "aha" is not the first minute; it is **opening the app after being away** and finding a life that continued. The simulation suggests that beat is underexposed in onboarding.

> "The offline catch-up is magic — open it after three days and he's had a whole life. Found a pebble, burnt noodles, Nib napped in his bed." — student, `retained`

> "The first session is lovely — Canvas art, little moods, a handwritten journal. Then the loop is just... waiting." — cozy fan, `churn_early`

### Round 4 — Social debate (privacy vs. store trust)

The debate splits along archetype lines rather than product features:

- **Privacy / power-user side:** MIT, no account, no ads, no analytics, local JSON save, optional online AI. They defend "unverified" as a reputation signal, not malware. Mentions: MIT in 124 posts, offline in 238, no ads/analytics/account in 230.
- **Casual / parent side:** "Why is this not on the Play Store?", "I don't know what a signing certificate is", "my phone said it might be harmful". Play Protect appears in **355 posts** (330 among the 884 exposed users).
- **Skeptic side:** correctly identifies that Pipo is *not* an LLM wrapper — authored dialogue plus a deterministic engine — and respects that, but questions day-30 depth.

> "MIT license, no analytics, no ads, no account — and I read the Play Protect audit. Unverified is not malicious." — privacy advocate

> "Still wish this was on the Play Store. GitHub links and scary warnings are a lot to ask." — casual user

The reputation of the app is being decided by **install mechanics**, not by the simulation's quality.

### Round 5 — Day 30 (219 / 1,000 retained)

Retained users are genuine advocates: average sentiment **+0.66**, and their posts name concrete moments (Nib learning high-five, the Diwali room, a burnt noodle story, a note on the door). Churn between Day 7 and Day 30 is mostly structural:

- 50 novelty faded after the first milestones
- 27 no auto-updates / manual GitHub check
- 24 lost save or reinstalled; no cloud backup
- 24 notifications too rare to remember it
- 23 wanted deeper agency or games
- 17 battery/performance on older phones

> "Day 30: still checking Pipo's journal every morning. Nib learned high-five, stole a gear and hid it under the bed. 26.1 MB well spent." — cozy fan, `retained`

> "Loved the Diwali room and the journal. Manual sideload updates are why I stopped checking." — privacy advocate, `churn_month`

---

## 4. Archetype outcomes

| Archetype | Installed | Day 7 | Day 30 | Install → Day 30 | Day 30 of all | Avg sentiment |
|---|---:|---:|---:|---:|---:|---:|
| Android Power User | 120 | 86 | **63** | 52.5% | **45.0%** | +0.44 |
| Cozy Virtual-Pet Fan | 77 | 60 | **44** | 57.1% | 29.3% | +0.46 |
| Open-Source / Privacy Advocate | 91 | 65 | **39** | 42.9% | 35.5% | **+0.50** |
| Indie Developer / Maker | 78 | 50 | 23 | 29.5% | 25.6% | +0.38 |
| Battery-Conscious Minimalist | 48 | 30 | 15 | 31.2% | 18.8% | +0.04 |
| Bored Student / Scroller | 54 | 39 | 16 | 29.6% | 16.0% | +0.09 |
| Parent / Digital-Detox Buyer | 13 | 8 | 5 | 38.5% | 8.3% | +0.27 |
| Retro / Indie Game Browser | 32 | 15 | 5 | 15.6% | 7.1% | +0.14 |
| Casual Play Protect-Wary User | 32 | 19 | 7 | 21.9% | 5.4% | −0.16 |
| Virtual-Pet Skeptic / AI-Slop Watchdog | 31 | 12 | 2 | 6.5% | 2.9% | −0.25 |

**What this says**

- **Protect the power-user and privacy segments.** They install easily and retain at 35–45%; they are also the people who defend the app in public. Do not add friction for them (no mandatory account, no analytics, no cloud requirement).
- **Cozy fans are the growth prize.** 44 retained of 150, highest sentiment alongside privacy, but 25 lost to Play Protect. An install flow that works for a non-technical cozy user is the single highest-leverage product change.
- **Casual users are not a lost cause; they are a trust problem.** 95 discovered, 57 downloaded, only 32 installed, 7 retained. Better warning copy and a screenshot of the exact Android dialog would recover a meaningful share.
- **Indie developers over-index on install and under-index on retention.** They praise the deterministic engine, then leave when the loop becomes passive. Their feedback is useful for depth, not acquisition.
- **Parents are small but high-intent.** Only 42 discovered and 22 downloaded, but 38.5% of installs reach Day 30. A dedicated "no ads, no purchases, no account" landing page for parents could grow this segment cheaply.

---

## 5. Where users are lost (all 781 losses)

| Loss point | Users | Share of losses | Dominant reasons |
|---|---:|---:|---|
| Never discovered | 116 | 14.9% | feed/algorithm only; no store surface |
| Discovered, never downloaded | 213 | 27.3% | GitHub APK distrust (57), Play Protect fear (52), no store/trust signal (40) |
| Play Protect abandoned | 95 | 12.2% | scared (48), couldn't find "Install anyway" (25), distrust (16) |
| Churn before Day 7 | 192 | 24.6% | novelty fade (61), forgot/too quiet (46), battery/3D (30), wanted a game (22) |
| Churn Day 7 → Day 30 | 165 | 21.1% | novelty (50), no auto-updates (27), lost save (24), notifications rare (24), depth (23), battery (17) |

**Three bottlenecks, ranked by modeled impact**

1. **Install trust (244 users, 31.2% of all losses).** This is not a copy tweak; it is the product's front door. The README has the right information in the wrong place, and the hero promises a one-tap install that the OS does not allow.
2. **Passive loop / memory (≈226 users, 28.9%).** Users do not churn because Pipo is bad; they churn because nothing reminds them to open the app, and the journal is not the home surface. This is fixable without adding features.
3. **Update/save anxiety (65 users, 8.3%).** Manual GitHub updates and local-only saves are acceptable to power users and fatal to casual ones. A backup/export and an Obtainium/F-Droid path need to be explained in one screen.

**Directional projection, not a promise:** recovering half of the 95 Play Protect losses at the observed 38.0% install→Day-30 rate adds **≈18 retained users (+8% relative)**. Recovering 25% of early churn (48 users) at the same downstream rate adds **≈27 (+12%)**. Together that is ~26% Day-30 retention instead of 21.9%.

---

## 6. What actually travels

Mentions among the 576 installed users' posts (keyword counts in `sample_post`; a post can mention several hooks). These are **not causal lift estimates**.

| Hook | Mentions | Share of installed users | Read |
|---|---:|---:|---|
| Nib the pet | 337 | 58.5% | The strongest identifiable character hook. Lead creative with Nib, not the feature grid. |
| Journal / away log | 236 | 41.0% | The retention surface. It is currently a separate screen, not the first thing a returning user sees. |
| No ads / analytics / account | 181 | 31.4% | Strong trust proof, especially for privacy/parent segments. |
| Offline-first / on-device | 160 | 27.8% | Believable and differentiating; pair with the local JSON save story. |
| 10 traits + 12 moods | 137 | 23.8% | Respected by power users and indie devs; needs an accurate count (see §7). |
| Play Protect friction | 133 | 23.1% | A negative driver, not a hook. It is discussed as often as the feature list. |
| Compose Canvas art | 129 | 22.4% | Strong aesthetic signal; the current single screenshot undersells motion and Nib. |
| Only 26.1 MB | 126 | 21.9% | Good counter-positioning against 100 MB+ ad-supported apps. |
| Battery / 3D room | 88 | 15.3% | Mixed: a benefit for minimalists, a cost for low-end phones. |
| MIT / open source | 75 | 13.0% | Niche but high-conviction; it converts privacy advocates into defenders. |

**Best-performing message shape:** a concrete moment + a trust fact. Example: *"Nib stole a gear and Pipo blamed the plant. No ads, no account, 26.1 MB."* Abstract feature lists ("10 traits, 12 moods, autonomous simulation") appear mostly in developer posts and rarely in referral posts.

---

## 7. Copy and UX fixes

### 7.1 `README.md` — prioritized

**P0 — fix the install promise (recover 95 Play Protect losses + part of 213 bounces)**

1. **Remove "Install it in one tap."** It is factually wrong for a GitHub APK and primes users to abandon at the first warning. Replace with an honest two-minute path.
2. **Put the Play Protect fix next to the download button, not in a collapsed `<details>`.** Suggested block:

   > **Installing from GitHub? Android will warn you.**
   > Pipo is signed by an independent developer, not Google. If you see **"unverified app"** or **"app may be harmful"**, tap **More details → Install anyway**. This is a reputation warning, not a malware detection — [read the audit](security/play-protect-investigation/EXECUTIVE_SUMMARY.md).
   > Do **not** turn off Play Protect. If there is no "Install anyway", allow installs for your browser or Files app in Settings → Apps → Install unknown apps.

3. **Add one real screenshot or 8-second GIF of the exact Android warning and the "Install anyway" target.** The simulation's blocked users repeatedly say they could not find it. Text alone is not enough.
4. **Keep the SHA-256 and the security audit visible.** It is a genuine trust asset for the privacy segment; move it from a collapsed block to a small "Verify" line beside the CTA.

**P0 — sell the actual loop (address 111 novelty churn + 70 "forgot it existed")**

5. **Replace the hero's feature language with the check-in promise.** Suggested subhead:

   > A small robot lives in your phone. He sleeps, builds, eats, goes out and writes it down — whether the app is open or not. You drop in and find out what happened. **26.1 MB · Android 8+ · no account · no ads · no analytics · MIT.**

6. **Add a "Your first 72 hours" timeline above the fold:**
   - **Day 1:** Pipo wakes up, asks your name, introduces Nib.
   - **Day 2:** open the journal; he did something while you were gone.
   - **Day 7:** the "7 days together" milestone, and Nib starts learning tricks.
   This sets the correct genre expectation (a check-in toy, not a session game) and filters the 22 users who churned wanting a game loop.

7. **Make Nib a first-class section with motion.** Nib appears in 58.5% of installed-user posts. A 15-second silent clip of Nib stealing something, Pipo reacting, and the journal entry appearing is stronger than the current still screenshot. `Pipo-Intro-Vertical.mp4` already exists; embed a compressed GIF/clip near the hero.

8. **Show the journal and away log as product surfaces.** One screenshot of the handwritten journal + one of "while you were away" communicates the retention loop better than the engine bullet list.

**P1 — remove contradictions and support the privacy claim**

9. **Say "12 core moods + 3 quieter shades"** (or "15 mood states"). The code's `Mood` enum has 12 named moods plus `WORRIED`, `THOUGHTFUL`, `PLAYFUL`; the current "twelve moods" claim is vulnerable to a code-reviewer's screenshot. Ten traits is accurate.
10. **Be precise about rendering:** the classic room is Compose Canvas; the default `world3d = true` uses the 3D stage, with a classic fallback. Say "Compose Canvas classic room + real-time 3D room" rather than implying everything is Canvas.
11. **Disclose defaults honestly.** `onlineAi` and `realWeather` default to **true** in `PipoSettings`. The README's privacy story is strong, but "offline-first" must not read as "nothing leaves the phone by default". Either change the defaults to opt-in (recommended) or state: "Online AI and real weather are optional and can be switched off in Settings."
12. **Explain updates and saves.** Add a short section: local-only save, uninstall loses Pipo, GitHub updates are manual, Obtainium is supported, F-Droid re-signs and cannot upgrade over a GitHub install. This addresses 27 update churn + 24 save-loss churn and prevents a bad review later.
13. **Mention the optional 40 MB voice download** next to the 26.1 MB base size. 19 bounces cited size/extra download; do not let users discover it after install.
14. **Add a TTS check.** The README already has a "He is silent" troubleshooting entry. Promote it to a one-line onboarding tip: "If he's silent, check Settings → Accessibility → Text-to-speech."

### 7.2 Onboarding — prioritized

**P0 — explain the loop in the first 60 seconds**

1. **After `firstWake` (the "Hi. I'm Pipo" sequence), add a three-card first-run sheet:**
   - *He lives here.* "Pipo sleeps, builds, eats, goes out and writes it down. You don't steer him."
   - *Come back and see.* "The journal and the 'while you were away' card show what happened while the app was closed."
   - *This is Nib.* "Nib lives here too. Nib steals things. Pipo blames Nib."

   The existing first wake is charming but teaches only tapping and naming. It does not teach the return loop, which is the actual product.

2. **Surface the journal on the home screen after the first away period**, with a small "new entries" badge. Today it is reached through the menu; 41% of installed-user posts cite the journal, so it should be one tap away, not a destination users must remember.

3. **Guarantee one Nib beat in the first 24 hours and make it the first notification.** The engine's `PetEngine.offlineStep` is probabilistic (10-hour cooldown, 12% + mischief chance). A scripted first theft ("Nib took your blue marble. It's under the bed.") gives every new user the hook that 58.5% of retained users talk about.

**P1 — reduce the two biggest churn reasons**

4. **Add one "while you were away" notification on Day 2 and the Day-7 milestone**, respecting quiet hours. 46 early + 24 late churners forgot the app existed because notifications were too quiet. Do not add streaks or guilt; one high-signal message is enough.
   Suggested copy: *"While you were away: Nib stole a gear. Pipo is looking for it. (Tap to see.)"*

5. **Add a "Lite mode" choice during onboarding**: classic room (lighter on battery) vs. 3D room. 30 early + 17 late churners cited battery/3D cost; the code already supports `world3d = false` and `calmMotion`. Detecting low-RAM devices and defaulting them to classic would protect the battery-conscious segment.

6. **Make the two online features an explicit choice, not a default.** A single first-run card: *"Two optional online features: AI chat and real weather. Both send data. Keep them off for a fully offline Pipo?"* This turns a privacy inconsistency into a differentiator and protects the 35.5% Day-30 privacy segment.

7. **Add a "Hear Pipo" test button** (a beep + one line) in first-run or Settings. TTS failures currently surface as "he is silent" after install; catching them at setup avoids a bad first impression.

**P2 — polish**

8. **Explain the notification permission before Android's system dialog.** The existing ask at 6 interactions with "Only when something actually happens" is excellent; add a one-sentence rationale screen first so the OS prompt is not the first explanation.
9. **Show the actual first journal entry as a reveal, not a toast.** When the first autonomous event lands, open the journal page with it highlighted. This teaches the surface by using it.
10. **Add a visible "What's new" card on resume** fed by `awayLog`. The data already exists; it is the cheapest retention surface in the app.

---

## 8. What to measure after shipping the fixes

Pipo has no analytics by design; keep it that way. Measure at the edges:

- **GitHub release asset downloads** before/after each README or install-flow change (`gh release view v1.0 --json assets`). This is the only trustworthy real conversion number today.
- **Referrer domains** in GitHub traffic after each post; attach UTM only to the landing link, never the APK.
- **Install-friction comments** tagged `play protect` / `install` in GitHub Discussions and issues. Track the count of "couldn't find Install anyway" reports as the primary install-UX metric.
- **itch.io views → external-file downloads** and **Product Hunt** visits as secondary top-of-funnel signals.
- **Day-30 retention cannot be measured without analytics.** Use opt-in public replies ("still checking in after a month?") and discussion threads; do not add silent telemetry to a privacy-first app.

**Success criteria for the next iteration:** fewer install-friction comments, not a higher simulated retention number. The simulation says the concept is strong enough; the front door and the return loop are what need work.

---

## 9. Limitations

- Synthetic priors, not measured user behavior. Absolute funnel numbers should be treated as a scenario, not a forecast.
- The simulation models archetype-level tendencies; it does not model real network effects, influencer amplification, or store algorithms.
- `sample_post` is a representative quote per agent, not a transcript of a real thread. Repeated phrasings are expected.
- No local LLM, Ollama, Docker, or external API was used. The engine is rule-based, seeded and reproducible: `python simulation_1000/simulate.py`.
- The real repository baseline (7 APK downloads, 1 star on 10 Nov 2026) is far below this scenario's 1,000 exposed users; acquisition, not conversion, is still the first problem in production.

---

*Generated from the actual Pipo repository at the `v1.0`/`v1.1` code state. All percentages are model outputs; all product claims above were checked against `app/src/main`.*
