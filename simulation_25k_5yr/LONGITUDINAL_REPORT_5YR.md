# Pipo — Synthetic Five-Year Longitudinal Forecast (25,000 Agents)

**Prepared:** 13 November 2026<br>
**Model:** deterministic seed `488`, 25,000 independent agents, 10 archetypes, five acquisition years, nine lifecycle checkpoints per user.<br>
**Reproduce:** `python simulation_25k_5yr/simulate_5yr.py` (stdlib only). The script writes `agents_25k_5yr.csv`, `summary_5yr.json` and `run_output.txt` locally; those generated artifacts are gitignored.<br>
**Constraint:** 100% Python standard library. No network, LLM, Ollama, Docker, database, or external API.

> **Synthetic projection, not user analytics.** Every cohort count, percentage and quote below is a model output from simulated agents. None of it is measured installs, retention or market research. The priors come from Pipo's actual code and launch constraints, then vary per agent. Treat the **shape of the decay, the ranking of bottlenecks, and the code-level causes** as the useful output. The absolute counts are a scenario, not a promise. A real baseline on 10 Nov 2026 was 7 GitHub APK downloads and 1 star.

**Cohort clock:** every user is tracked for five years from **their own Day 1**. A user acquired in the Year-5 revival wave is therefore observed for five years after that point. `discovery_wave` describes acquisition timing; the checkpoint columns are individual cohort retention.

---

## 1. Executive summary

| Stage | Users | Stage-to-stage | Of all 25,000 | Of installed |
|---|---:|---:|---:|---:|
| Addressable population | 25,000 | — | 100.0% | — |
| Discovered Pipo | 22,842 | 91.4% | 91.4% | — |
| APK downloaded | 17,477 | 76.5% | 69.9% | — |
| Installed / passed Play Protect | 14,733 | 84.3% | 58.9% | 100.0% |
| Day 7 | 10,337 | 70.2% | 41.3% | 70.2% |
| Day 30 | 8,270 | 80.0% | 33.1% | 56.1% |
| Day 60 | 6,899 | 83.4% | 27.6% | 46.8% |
| Day 180 | 5,210 | 75.5% | 20.8% | 35.4% |
| Year 1 | 4,010 | 77.0% | 16.0% | 27.2% |
| Year 2 | 2,908 | 72.5% | 11.6% | 19.7% |
| Year 3 | 2,317 | 79.7% | 9.3% | 15.7% |
| Year 4 | 2,078 | 89.7% | 8.3% | 14.1% |
| **Year 5** | **1,881** | **90.5%** | **7.5%** | **12.8%** |

**Five conclusions**

1. **A durable core exists.** 1,881 users reach Year 5 — **7.5% of the addressable population, 8.2% of discoverers, and 12.8% of installs**. Year 3→4 and Year 4→5 survival are 89.7% and 90.5%: the people who stay become a stable, high-affection cohort rather than a slowly leaking bucket.
2. **The core is three archetypes.** Android power users (21.8% of installs reach Year 5), cozy virtual-pet fans (22.3%) and open-source/privacy advocates (21.1%) account for **1,601 of the 1,881 Year-5 users (85.1%)**. Casual Play Protect-wary users and skeptics both sit at **0.5%**.
3. **Install trust is still the largest early leak, but it is not the long-term story.** 5,365 users never download and 2,744 abandon at Play Protect — **8,109 users (32.4% of the population, 35.5% of discoverers)** lost before first launch. Fixing the warning path is a Year-1 requirement, not a growth experiment.
4. **The most damaging long-term failure is save loss, not boredom.** 466 APK-update save wipes and 388 phone-migration losses total **854 catastrophic save losses**, equal to **5.8% of installs and 21.3% of the 4,010 users who reach Year 1**. For a five-year product, export/transfer is a retention feature, not a utility.
5. **Content exhaustion is real but quieter than novelty and notifications.** 1,431 users leave for content-related reasons (81 hard content walls + 1,350 gradual “content thinning/exhausted”), while **3,985** leave on novelty/expectation and **2,058** on notification/forgetting. Content-exhausted users still average **+0.31 sentiment**: they leave fondly, which makes a late-year “more to do” roadmap credible.

---

## 2. What the real codebase does at five years

This model is only useful if it respects the actual long-term mechanics. The audit below is from `app/src/main` and the repository docs.

### Save persistence and migration

- `PipoRepository` stores the entire world in **one local JSON file** (`pipo_state.json`) in `filesDir`, written atomically via a temp file and rename. This is simple and robust for normal use.
- A corrupt save is copied to `pipo_state.corrupt.json` and a fresh Pipo is created. A pre-migration save is copied to `pipo_state.v<schema>.bak.json`.
- `Migrations` is currently at **schema 2** and only contains a v1→v2 migration (old “drawings” counter → real drawings; pre-Nib Pipos meet Nib later). There is no migration for a future schema 3 yet.
- `sanitize()` repairs bad values and caps collections (drawings 60, photos 80, pantry 24, stolen-item references, trip duration). It preserves history where it can.
- **There is no export/import UI.** The save never leaves the phone by design.

### Backup and phone transfer are deliberately disabled

`AndroidManifest.xml` declares `android:allowBackup="false"`. `res/xml/data_extraction_rules.xml` excludes `root`, `file`, `database`, `sharedpref` and `external` from **both cloud backup and device-to-device transfer**.

This is a strong privacy decision and a direct retention liability. A normal phone upgrade, Smart Switch-style transfer, or “restore from backup” cannot carry Pipo. The model’s 388 phone-migration losses are therefore not an edge case; they are the predicted consequence of the current manifest.

### APK updates and source switching

- A normal in-place GitHub update with the **same signing key** preserves the save. The danger is the manual path: uninstalling the old app before installing the new APK (the README explicitly warns that uninstalling deletes the save), a failed update after removal, or switching between GitHub and F-Droid — F-Droid re-signs with its own key, so it cannot upgrade over a GitHub install and requires an uninstall.
- The security documentation recommends failing loudly on missing release signing, and the README tells users not to install over a differently signed copy. Those are correct. They do not remove the user-level failure mode.
- The model separates safe updates from the uninstall-first/source-switch/failed-update path. **466 users hit that path**; that is 11.6% of Year-1 survivors, not 466 of all 25,000.

### Offline time reconciliation after months away

- `PipoWorker` wakes roughly hourly. `Simulator.catchUp()` replays the elapsed time in **40-minute steps**, but `MAX_CATCH_UP = 3 * DAY`.
- After a six-month absence, only the **last three days** are replayed. `lastSimulatedAt` moves forward; the intervening months do not generate a full life. This is a deliberate cost/consistency trade-off, not a crash.
- Relationship never decreases because of absence (`relationship` only grows with shared time). Loneliness is capped at 0.7, and the design explicitly avoids guilt. This is why dormant returners can come back without a punishing meter.
- Milestones exist only at **7, 30, 100 and 365 days**. There is no 2-, 3-, 4- or 5-year milestone event.

The model’s 452 dormant returners capture both sides: 197 were charmed that Pipo had a life waiting, 157 were disappointed that only three days had been replayed, and 98 returned mainly for Nib.

### Memory, journal and long-term history

- `Chronicle` caps **journal entries at 400**, **memories at 120**, and events at 30. `PetEngine` trims Nib’s memories to 20. Drawings cap at 60 and photos at 80 in `Migrations.sanitize()`.
- Old journal entries are silently dropped from the front once the 400 cap is crossed. For a daily five-year user, the first year of the journal is the first history to disappear.
- Memory relevance decays by type (for example, `MOMENT` and `PROJECT` have a 120-day half-life; `CONVERSATION` has 7 days). Important early memories can survive, but the archive is not a complete five-year record.
- The model finds **2,135 users (14.5% of installs)** crossing the 400-entry cap. Their modeled lifetime writes total **468,421 entries that the live save cannot retain**.

### Personality saturation

- New traits start in **0.15..0.85**. `Personality.nudge()` applies half the delta and clamps to **0.05..0.95**.
- This prevents runaway caricatures, but there is no renormalization across the ten traits. A repeatedly reinforced trait can sit at a bound for years, reducing personality plasticity. The simulation treats this as a slow texture change, not a churn cause, because the code itself never fails or throws.

### Seasonal content

- `Festivals` has 10 festivals. Fixed-date festivals (New Year, Pongal, Vishu, Halloween, Christmas) work every year.
- Moving festival windows (Holi, Eid, Onam, Diwali) are a hand-written table covering **2026, 2027 and 2028 only**. After 2028, those festivals return `null` unless the table is extended.
- The model still assumes seasonal acquisition spikes in Years 3–5 because demand is external to the app. The in-app seasonal content, however, would be missing unless the table is extended. This is a concrete five-year bug, not a modeling assumption.

### Finite authored content

- 11 base projects in `Catalog` plus 9 Inventor blueprints = **20 projects**.
- **6 Nib tricks**, **5 Nib stages**, 10 festivals, a bounded catalog of items/places and a fixed set of games.
- A completionist can finish the authored project/trick ladder in roughly 1–3 years; after that, the simulation continues through moods, journal entries, trips, cooking, Nib’s small events and seasonal repeats. That is enough for the core, not enough for content-led retention.

---

## 3. Funnel and retention decay

```text
25,000 addressable
  → 22,842 discovered (91.4%)
    → 17,477 downloaded (76.5% of discovered)
      → 14,733 installed (84.3% of downloads)
        → 10,337 Day 7 (70.2% of installs)
          → 8,270 Day 30 (56.1%)
            → 6,899 Day 60 (46.8%)
              → 5,210 Day 180 (35.4%)
                → 4,010 Year 1 (27.2%)
                  → 2,908 Year 2 (19.7%)
                    → 2,317 Year 3 (15.7%)
                      → 2,078 Year 4 (14.1%)
                        → 1,881 Year 5 (12.8%)
```

**End-to-end:** 1,881 / 25,000 = **7.5%**; 1,881 / 22,842 discoverers = **8.2%**.

**Where the curve bends**

- **Day 7:** 4,396 installs fail to reach the first week. This is the largest post-install drop (29.8% of installs).
- **Day 30 → Day 60:** 1,371 users leave; the “novelty” phase is not over at Day 30.
- **Day 180 → Year 1:** 1,200 users leave; manual updates, save anxiety and life changes start to matter.
- **Year 1 → Year 2:** 1,102 users leave, the largest single yearly drop (27.5%).
- **Year 3 onward:** conditional survival rises to 79.7%, 89.7% and 90.5%. The surviving cohort is no longer “leaking”; it is a stable core with high sentiment.

---

## 4. Archetype survival curves

Percentages are **of installed users in that archetype**; counts are shown in `summary_5yr.json` and the CSV.

| Archetype | Agents | Installed | D30 | D60 | D180 | Y1 | Y2 | Y3 | Y4 | Y5 | Y5 % of installs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Android Power User | 3,500 | 3,080 | 68.3% | 61.9% | 51.8% | 42.1% | 32.8% | 26.5% | 24.2% | 21.8% | **21.8%** |
| Cozy Virtual-Pet Fan | 3,750 | 1,939 | 65.3% | 60.3% | 51.8% | 44.1% | 32.8% | 27.2% | 24.4% | 22.3% | **22.3%** |
| Open-Source / Privacy Advocate | 2,750 | 2,356 | 64.1% | 58.0% | 47.7% | 39.5% | 29.8% | 24.6% | 22.7% | 21.1% | **21.1%** |
| Parent / Digital-Detox Buyer | 1,500 | 344 | 58.7% | 52.0% | 40.1% | 31.7% | 22.4% | 18.9% | 16.0% | 13.4% | 13.4% |
| Indie Developer / Maker | 2,250 | 1,993 | 55.7% | 42.8% | 27.9% | 17.6% | 10.3% | 7.0% | 5.8% | 5.4% | 5.4% |
| Battery-Conscious Minimalist | 2,000 | 1,225 | 49.8% | 37.7% | 23.7% | 14.7% | 9.6% | 7.0% | 6.2% | 5.1% | 5.1% |
| Bored Student / Scroller | 2,500 | 1,361 | 47.5% | 33.0% | 19.1% | 11.5% | 6.2% | 4.3% | 3.2% | 2.6% | 2.6% |
| Retro / Indie Game Browser | 1,750 | 811 | 39.1% | 27.4% | 15.3% | 10.0% | 6.3% | 4.2% | 3.2% | 2.7% | 2.7% |
| Casual Play Protect-Wary User | 3,250 | 833 | 32.9% | 20.4% | 9.1% | 4.0% | 1.6% | 0.7% | 0.6% | 0.5% | **0.5%** |
| Virtual-Pet Skeptic / AI-Slop Watchdog | 1,750 | 791 | 28.7% | 15.3% | 5.4% | 1.8% | 1.1% | 0.6% | 0.5% | 0.5% | **0.5%** |

**Who stays for 3–5 years**

- **Power users, cozy fans, privacy advocates.** They combine high install conversion with 21–22% five-year survival and the strongest sentiment (roughly +0.34 to +0.40 overall). They are the product's actual long-term audience.
- **Parents are small but unusually loyal.** Only 344 installs, but 13.4% reach Year 5. The binding constraint is discovery and install trust, not the product: 280 of 1,500 parents abandon at Play Protect.
- **Indie developers install readily and leave early.** 1,993 installs, but only 5.4% reach Year 5. They praise the deterministic engine, then hit the passive loop and finite content.
- **Casual users and skeptics are effectively day-60 products.** 32.9%/28.7% reach Day 30, then 20.4%/15.3% reach Day 60 and 0.5% reach Year 5. They are not a retention segment under the current design; they are a trial segment.
- **Battery-conscious users** retain better than students/retro but still only 5.1% at Year 5; the 3D-room default is a measurable early tax.

---

## 5. Acquisition waves

| Wave | Discovered | Installed | Year 1 | Year 5 | Y5 % of installs | Avg sentiment |
|---|---:|---:|---:|---:|---:|---:|
| Y1 Launch: GitHub + Product Hunt | 5,780 | 3,932 | 1,117 | 527 | 13.4% | +0.16 |
| Y1 Reddit: r/Android word of mouth | 4,699 | 2,920 | 709 | 314 | 10.8% | +0.07 |
| Y2 FOSS: privacy forums | 2,570 | 2,026 | 716 | 363 | **17.9%** | **+0.25** |
| Y2 Seasonal: Diwali / New Year | 3,695 | 1,981 | 464 | 217 | 11.0% | +0.13 |
| Y3 Indie: itch.io browse | 2,390 | 1,438 | 354 | 140 | 9.7% | +0.16 |
| Y3 Seasonal: Diwali / New Year | 1,936 | 1,346 | 359 | 174 | 12.9% | +0.20 |
| Y4 Long tail: Obtainium / F-Droid | 1,059 | 666 | 176 | 90 | 13.5% | +0.14 |
| Y5 Revival: old threads & search | 713 | 424 | 115 | 56 | 13.2% | +0.12 |

- **Seasonal waves are real acquisition spikes but mid-retention.** The two seasonal waves contribute **5,631 discoveries (24.7% of all discoverers)** and **391 Year-5 users (20.8% of the core)**. They are worth running, but they do not replace the FOSS/launch channels.
- **FOSS is the highest-quality channel.** 2,570 discoveries, 2,026 installs (78.8% install rate) and 17.9% Year-5 survival. Privacy positioning is not a niche footnote; it is the most durable acquisition story.
- **Launch is the volume channel.** 5,780 discoveries and 527 Year-5 users, but 13.4% survival. It gets the app known; it does not by itself build the core.
- **Reddit r/Android is the weakest large wave** at 10.8% Year-5 survival. It brings volume and skepticism in equal measure.
- **Long-tail and revival users are small but high-intent.** 13.2–13.5% Year-5 survival from tiny bases; they are cheap to keep reachable through Obtainium, F-Droid and old threads.

---

## 6. Multi-year bottleneck ranking

### 6.1 Exact losses by modeled cause

| Bottleneck | Users | Base for rate | Rate |
|---|---:|---|---:|
| Never discovered | 2,158 | 25,000 addressable | 8.6% |
| Discovered, never downloaded | 5,365 | 22,842 discoverers | 23.5% |
| Play Protect abandoned | 2,744 | 17,477 downloads | 15.7% |
| Play Protect abandoned | 2,744 | 11,546 prompted | 23.8% |
| Soft churn (novelty, notifications, life, content, battery, etc.) | 11,917 | 14,733 installs | 80.9% |
| **APK update save wipe** | **466** | 14,733 installs / 4,010 Year-1 | **3.2% / 11.6%** |
| **Phone migration save loss** | **388** | 14,733 installs / 4,010 Year-1 | **2.6% / 9.7%** |
| **Catastrophic save losses combined** | **854** | 14,733 installs / 4,010 Year-1 | **5.8% / 21.3%** |
| Hard content wall (explicit mode) | 81 | 14,733 installs | 0.5% |
| Content-related churn (explicit + soft) | 1,431 | 14,733 installs | 9.7% |

### 6.2 Why users actually leave

| Reason family | Users | Share of 25,000 | What it means |
|---|---:|---:|---|
| Trust before first launch | 8,109 | 32.4% | 5,365 no-download + 2,744 Play Protect. The front door is the biggest product problem. |
| Novelty / wrong expectation | 3,985 | 15.9% | First session 1,297; expected a game 1,112; novelty at Day 30 728; at Day 60 532; expected more game 316. |
| Never discovered | 2,158 | 8.6% | Wave reach, not conversion. |
| Notifications / forgetting | 2,058 | 8.2% | “Notifications too quiet” 1,625; “forgot it existed” 433. |
| Content-related | 1,431 | 5.7% | Content exhausted 739; thinning at six months 611; hard wall 81. |
| Update / transfer anxiety | 1,522 | 6.1% | No export/backup anxiety 673; manual update friction 480; no auto-updates 369. Separate from actual wipes. |
| Battery / 3D room | 956 | 3.8% | Mostly early churn; a settings/defaults problem, not a content problem. |
| Play Protect alone | 2,744 | 11.0% | 1,188 scared; 712 could not find “Install anyway”; 529 did not trust an unverified APK; 315 package/download error. |

**The ranking changes with time horizon**

- **Year 1:** Play Protect + download distrust dominate; then first-session mismatch and notification silence.
- **Years 1–2:** update/save anxiety and phone migrations become the highest-consequence losses because they destroy accumulated attachment, not just a session.
- **Years 2–5:** content thinning and life change dominate volume; the remaining users are highly attached and leave fondly.

**The save-loss number deserves emphasis.** 854 users is only 5.8% of installs, but it is **21.3% of the users who reach Year 1** — the exact cohort that has the most to lose. A user who loses a 14-month Pipo does not churn because the loop is thin; they churn because the relationship was erased by an Android chore.

---

## 7. Dormant returners and the three-day absence

- **452 users** had at least one 6+ month dormant gap and returned; **485 episodes** total.
- Reactions: **197 charmed** (“he kept living”), **157 disappointed** (“only three days replayed”), **98 neutral** (“came back for Nib”).
- That 35% disappointment rate is a direct consequence of `MAX_CATCH_UP = 3 * DAY`. The product currently promises a world that keeps moving, but after six months it can only show the last three days.
- The relationship model is kind: `relationship` never decreases from absence, and loneliness is capped. Returning users are not punished. The missing piece is **honest framing** and a compact absence summary, not a longer replay.

**Design implication:** do not simulate six months synchronously. Show a short “while you were gone” digest that is explicitly a digest, plus a few live days. The emotional promise (“he kept living”) and the technical truth (“the last three days are fully replayed”) need to be reconciled in the UI.

---

## 8. Journal and memory saturation

- **2,135 users (14.5% of installs)** exceed the 400-entry journal cap over their lifetime.
- Modeled lifetime journal writes: **1,935,289**. Persistable entries at any moment: **1,466,868**. Entries that would be dropped by the cap: **468,421**.
- Average lifetime entries per installed user: **131.4**; median: **14**; maximum: **904**. The low median reflects the many early churners; the long tail is the five-year core.
- Memory cap 120, event cap 30, Nib memory cap 20. A five-year user's early history is compressed into a relevance-ranked subset, not a complete archive.

This is a quiet long-term trust issue. The first “we met” entry, the first Nib theft and the first burnt noodles are exactly the artifacts that make a five-year relationship legible — and they are the first to be dropped. A local “Memory Book” export/archive at each anniversary would preserve the emotional payoff without changing the live simulation caps.

---

## 9. Prioritized five-year roadmap

### Year 1 — fix the front door and the first 60 days

**P0: Install trust path (targets 2,744 Play Protect losses + 5,365 no-download losses)**

1. Remove the README's “Install it in one tap.” Replace it with the real path and a screenshot of the exact Android warning: **More details → Install anyway**. Put it beside the download button, not in a collapsed block.
2. Add the Play Protect explanation and the security-audit link at the point of download; state “unverified is a reputation warning, not a malware report,” and never tell users to disable Play Protect.
3. Give casual users a store-shaped trust signal (Obtainium/F-Droid instructions, SHA-256, release notes) without pretending there is a Play Store listing.

**P0: First-session and notification loop (targets 3,985 novelty/expectation + 2,058 notification losses)**

4. Add a three-card first-run explanation: he lives while you are gone; the journal is the return surface; Nib is a roommate, not a feature.
5. Guarantee one Nib beat in the first 24 hours and make it the first notification. Do not leave the strongest hook to a probabilistic 10-hour cooldown.
6. Add a Day-2 “while you were away” notification and a Day-7 milestone notification, respecting quiet hours. No streaks, no guilt.
7. Add a Lite mode/classic-room choice at onboarding and detect low-RAM devices. 956 users cite battery/3D; it is the cheapest early churn fix.

**P0: Copy accuracy**

8. Say “12 core moods + 3 quieter shades” (the enum has 15 states), and distinguish the Compose Canvas classic room from the default 3D stage. Feature claims should survive a code review.
9. Make online AI and real weather explicit opt-ins rather than true-by-default, or disclose the current defaults plainly.

### Year 2 — make the save portable and updates safe

**P1: Export / transfer (targets 854 catastrophic save losses + 1,522 update/transfer anxiety reasons)**

10. Ship a **manual, local, encrypted export/import**: user-initiated file via Android's document picker, no cloud, no account. This preserves the privacy promise while making phone migration survivable.
11. Add an “Move Pipo to a new phone” flow with a one-time code/QR and a checksum, or a documented local-file path that non-technical users can follow.
12. Add a pre-update local snapshot and a clear “update without uninstalling” guide. Detect a signature mismatch and explain the GitHub/F-Droid save-loss boundary before the user commits.
13. Never enable cloud backup silently. The right trade-off is explicit local export, not abandoning `allowBackup=false` for everyone.

**P1: Year-2 retention**

14. Add 2-year and 3-year milestones, plus a “year in Pipo's life” journal recap built from existing entries. The code currently stops at 365 days.
15. Preserve a compact anniversary archive so the 400-entry cap does not erase the first year of a five-year relationship.

### Year 3–5 — endgame depth for the 1,881 core

**P2: Content and seasonal depth (targets 1,431 content-related losses)**

16. Extend the moving-festival table beyond 2028. As written, Diwali/Holi/Eid/Onam disappearance after 2028 is a five-year product bug.
17. Add a second Nib arc after the current 5 stages / 6 tricks: aging, new habitats, Nib's own projects, a Nib-led mystery thread. 22.3% of cozy installs reach Year 5; they need a companion who also changes.
18. Add post-Year-1 blueprints and long-arc projects beyond the current 20 authored projects. The core is completionist; 20 projects and 6 tricks are a two-year ladder, not a five-year one.
19. Add long-term rituals and “our history” callbacks: first meeting, first theft, first burnt dinner, the year the room changed. The engine already stores enough history; the UI needs to resurface it.
20. Replace the three-day replay surprise with an honest absence digest and a “Pipo kept these days for you” framing. 157 of 452 returners were disappointed; a copy fix plus a short digest can convert most of that into charm.

### Directional impact (not a promise)

- Recovering half of the 2,744 Play Protect losses at the observed install→Year-5 rate (12.8%) adds **≈176 Year-5 users**.
- Preventing half of the 854 catastrophic save losses and retaining them at the observed Year-1→Year-5 rate (46.9%) adds **≈200 Year-5 users**.
- Together: **≈2,257 Year-5 users (9.0% of 25,000)** instead of 1,881 (7.5%). The point is not the exact number; it is that install friction and save portability are worth more than another content pack at the margin.

---

## 10. Measurement plan

Pipo has no analytics by design; keep it that way.

- **GitHub release asset downloads** before/after each install/README change (`gh release view v1.0 --json assets`). Downloads are not installs, but the delta is the only trustworthy real top-of-funnel signal.
- **Install-friction comments** tagged `play protect` / `install` in GitHub Discussions and issues. The count of “couldn't find Install anyway” is the primary install-UX metric.
- **Obtainium/F-Droid/itch.io** views and external downloads as channel-quality proxies.
- **Save-loss reports** after each release: “updated and lost my Pipo” is a P0 support signal and should be tracked separately from ordinary churn.
- **Anniversary replies**: an opt-in discussion thread asking “still checking in after a year?” is the only ethical long-term retention proxy without telemetry.

---

## 11. Verification and limitations

**Verification performed**

- 25,000 rows, 16 exact requested columns, 25,000 unique `user_id`s, all 10 archetypes with exact counts summing to 25,000.
- Monotonic retention verified for every row: `day_30 >= day_60 >= day_180 >= year_1 >= year_2 >= year_3 >= year_4 >= year_5`.
- All sentiment scores in `[-1, 1]`; all journal counts non-negative.
- Deterministic seed `488`; rerunning produces the same rows.
- The generated CSV, summary and run log are gitignored; `git check-ignore` confirms they do not appear in `git status`. Only the deterministic source and this report are tracked.

**Limitations**

- Synthetic priors, not observed cohorts. Absolute counts are directional and depend on the calibrated survival and hazard parameters.
- Each user has a five-year individual clock. Late discovery waves are therefore observed for five years after acquisition; this is cohort retention, not calendar-year prevalence.
- The model treats “retained at checkpoint” as active/returning at that checkpoint. Dormant gaps are modeled between checkpoints and do not produce false-then-true flags, preserving the required monotonicity.
- The APK update hazard models the uninstall-first/source-switch/failed-update path, not every update. A normal same-key in-place update is safe.
- The festival content audit is based on the current table (2026–2028). External seasonal demand is modeled through Year 5 even though in-app moving festival content would need extension.
- No real users were contacted, no APK was uploaded anywhere, and no external service was used.

---

*Generated from Pipo's actual repository state: one local JSON save, schema 2, backup and device transfer disabled, 3-day catch-up cap, 400-entry journal, 120 memories, 3-day/40-minute offline replay, 20 authored projects, 6 Nib tricks, 5 Nib stages, and moving festival dates through 2028. All cohort counts and percentages are synthetic model outputs, not user analytics.*
