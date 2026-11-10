# Pipo — user acquisition tracker

**Rule:** only measured numbers. A blank means "not measured", never zero and never estimated.
GitHub traffic windows are 14 days and are not historical, so every reading carries its date.

---

## 1. Measurement log

| Date read | APK downloads (lifetime) | Views (14d) | Unique viewers | Clones (14d) | Unique cloners | Stars | Forks | Referrers |
|---|---|---|---|---|---|---|---|---|
| 2026-11-10 (session start) | 4 | 14 | 3 | 126 | 47 | 1 | 0 | none |
| **2026-11-10 (end of sprint)** | **5** | 14 | 3 | 126 | 47 | 1 | 0 | none |

Read via the GitHub REST API. The only movement is **+1 APK download**. Views, clones, stars and forks
are unchanged, and no referrer has ever been recorded.

**Interpretation — read this before drawing conclusions:** clones (47 unique) exceed views (3 unique)
by an order of magnitude with zero referrers. That is the signature of automated mirroring and
crawling, not human interest. Do not treat unique cloners as users. The **APK download count is the
only trustworthy conversion number** on this project right now.

## 2. GitHub Discussions engagement

| Metric | Value | Date |
|---|---|---|
| Discussions open | 1 (`#14`, Announcements) | 2026-11-10 |
| Comments | 0 | 2026-11-10 |
| Reactions | 0 | 2026-11-10 |
| Watchers (subscribers) | 0 | 2026-11-10 |
| Open issues | 10 | 2026-11-10 |

Zero engagement is expected: the repository has no audience yet. It becomes meaningful only after
posts point at it.

## 3. Publication ledger

One row per **actually live** post. Drafts and pending submissions belong in `PUBLICATION_LOG.md`.

| Platform | Publication URL | Date | Views / upvotes | Referred downloads | Feedback | Install issues | Follow-up |
|---|---|---|---|---|---|---|---|
| GitHub Discussions (Announcements) | https://github.com/aspire488/Pipo/discussions/14 | 2026-11-10 | 0 comments, 0 reactions | not measurable | — | — | — |

## 4. Comment and feedback log

| Date | Platform | What they said | Category | Action taken |
|---|---|---|---|---|
| — | — | — | — | — |

Categories: `bug` · `confusion` · `feature request` · `play protect` · `hostile`.

## 5. Link conventions for future posts

**The canonical APK URL never changes.** Always give people this, so the hash in the README and release
notes keeps matching what they download:

```
https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk
```

UTM parameters are tracked in the *landing* link only:

```
https://aspire488.github.io/Pipo/?utm_source=<platform>&utm_medium=social&utm_campaign=pipo-v1-launch&utm_content=<post-slug>
```

| Platform | `utm_source` | `utm_content` |
|---|---|---|
| Reddit r/SideProject | `reddit` | `sideproject` |
| Reddit r/droidappshowcase | `reddit` | `droidappshowcase` |
| Reddit r/developersIndia | `reddit` | `developersindia` |
| Hacker News | `hackernews` | `showhn` |
| itch.io | `itchio` | `project-page` |
| Product Hunt | `producthunt` | `listing` |
| Indie Hackers | `indiehackers` | `post` |

**Honest limitation:** UTM parameters currently measure **nothing**. The GitHub Pages site has no
analytics, and GitHub's own referrer report lists the referring *domain* only, ignoring UTM strings. So
UTM is forward-looking bookkeeping, not a live measurement. Do not add analytics to the site just for
this — the APK download delta plus GitHub referrers are enough at this scale.

Never put UTM parameters on the GitHub release or APK URL itself: it looks untrustworthy, and GitHub
strips them from its referrer reporting anyway.

## 6. How to measure after publishing

```bash
gh api repos/aspire488/Pipo/traffic/views --jq '{count, uniques}'
gh api repos/aspire488/Pipo/traffic/clones --jq '{count, uniques}'
gh api repos/aspire488/Pipo/traffic/popular/referrers
gh api repos/aspire488/Pipo/traffic/popular/paths --jq '.[] | "\(.path) \(.count)"'
gh release view v1.0 --json assets --jq '.assets[] | .downloadCount'
gh api graphql -f query='query{repository(owner:"aspire488",name:"Pipo"){discussions(first:10){nodes{number title comments{totalCount} reactions{totalCount}}}}}'
```

Record the reading with its **date** in §1. A download appearing the day after a post is weak evidence;
two posts on different days giving different deltas is real signal.

## 7. What would actually move the number

Ranked by expected effect per hour of work, given the current state:

1. **Get the APK in front of a community with the same problem** — r/SideProject and a Show HN post are
   the highest-variance bets; one front-page Show HN is worth more than every store listing combined.
2. **Screenshots** — the listing assets block four channels at once and are the cheapest unlock.
3. **A 15-second silent clip** for people who scroll with sound off — `dist\Pipo-Intro-Vertical.mp4`
   already exists for this; no new video needed.
4. **itch.io**, for the "indie game" audience that browses by genre rather than by feed.
5. **F-Droid**, which has a small but highly committed audience and permanent, compounding discovery.

Store listings (Samsung, Play) are **not** an acquisition strategy at this size — they are credibility
and update plumbing. Sequence them after the community posts.
