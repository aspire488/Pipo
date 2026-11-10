# Pipo — user acquisition tracker

**Created:** 2026-11-10. **Rule:** only measured numbers. Blank means "not measured", never estimated.

---

## 1. Baseline (measured before any publication)

Source: GitHub REST API, `repos/aspire488/Pipo/traffic/*` and the releases endpoint, read 2026-11-10.

| Metric | Value | Window |
|---|---|---|
| APK downloads (`Pipo.apk`, v1.0) | **4** | lifetime |
| Repository views | 14 (3 unique) | last 14 days |
| Repository clones | 126 (47 unique) | last 14 days |
| Stars / forks / watchers | 1 / 0 / 0 | lifetime |
| Open issues | 10 | current |
| Traffic referrers | **none recorded** | last 14 days |
| Top paths | `/aspire488/Pipo` (11 views), `/releases/tag/v1.0` (2), `/blob/main/Pipo-Intro.mp4` (1) | last 14 days |

The clone count is far above the view count and there are no referrers, which is the signature of
automated mirroring/crawling rather than human interest. Do not read 47 unique cloners as 47 users.

---

## 2. Publication ledger

Fill one row per **actually live** post. A draft, a scheduled launch or a submission under review is
not a row here — it goes in `PUBLICATION_LOG.md` instead.

| Platform | Publication URL | Date | Views / upvotes | Referred downloads | Feedback received | Install issues | Follow-up |
|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — |

**Nothing is published yet.** This table is empty on purpose.

---

## 3. Comment and feedback log

Use this for real replies worth acting on. One line each, with the platform and what it implies.

| Date | Platform | What they said | Category | Action taken |
|---|---|---|---|---|
| — | — | — | — | — |

Categories: `bug` · `confusion` (copy/onboarding problem) · `feature request` · `play protect` ·
`hostile` (answer once, then stop).

---

## 4. Known confusion risks to watch for

Predicted from the build, not from users — verify against real replies and correct this list.

| Risk | Why | Where the answer lives |
|---|---|---|
| "Why does Play Protect warn me?" | Sideloaded, unverified, requests notification access | `REDDIT_FAQ.md`; `security/play-protect-investigation/` |
| "Is the chat AI?" | v1.0 ships an offline rule-based brain; online AI needs a self-hosted proxy | `REDDIT_FAQ.md`, privacy policy §2 |
| "Is it a Tamagotchi clone?" | It is not task/streak driven; activities are scored, not scheduled by the user | `REDDIT_POSTS.md` §1 story |
| "26 MB for a 2D app?" | Vosk wake-word native libraries dominate the size | `VIDEO_INVENTORY.md`, README |

---

## 5. How to measure after posting

```bash
gh api repos/aspire488/Pipo/traffic/views --jq '.count, .uniques'
gh api repos/aspire488/Pipo/traffic/popular/referrers
gh release view v1.0 --json assets --jq '.assets[] | "\(.downloadCount)"'
```

- GitHub traffic windows are **14 days** and not historical; record numbers on the day you check.
- Referrers only appear once GitHub has seen real traffic from them, so a fresh Reddit post may take
  a day to show up.
- The only trustworthy conversion number is the **APK download count delta**, not views or upvotes.
