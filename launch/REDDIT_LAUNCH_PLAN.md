# Pipo — Reddit launch plan

**Prepared:** 2026-11-10 · **Status:** research + drafts only. **Nothing has been posted.**
**Product:** Pipo — a little robot who lives inside your Android phone (`com.pipo.robot`, MIT, Android 8+).

---

## 1. How this research was done (and its limits)

Reddit blocks automated access from this machine: `www.reddit.com/r/<sub>/about/rules.json`,
`old.reddit.com/.../about/rules` and `r.jina.ai` all return **403 / "you've been blocked by network
security"**. So the rules below come from four verifiable sources, each with a date:

1. **The subreddit's own public rule list**, read from Internet Archive snapshots of
   `/about/rules` where one exists — r/androiddev (2024-11-17), r/developersIndia (2024-11-25).
   These give the rule *names*; descriptions were collapsed in the archive.
2. **The subreddit's live public RSS feed**, fetched today (2026-11-10). RSS carries current post
   titles, bodies and moderator announcements — this is how the "no self-promotion except the monthly
   thread" rule and the r/developersIndia policy announcement were confirmed as current, not historical.
3. **Moderator announcements** quoted in that RSS: r/developersIndia 2026-08-02, r/androiddev
   2026-07-02 (monthly showcase) and a 2026-10-28 user thread about the showcase stopping.
4. **Third-party rule trackers** (subriff, rankhog, mochiscout, threadotter) whose snapshots are dated
   2026-08 to 2026-10. Used only where the live page could not be read, and labelled as such.

**Therefore:** treat every rule below as *checked, dated, and still to be re-read on posting day*.
The one thing this plan will not do is claim certainty it does not have.

---

## 2. The four communities

### r/SideProject — ~210k members — **primary target**

| Item | Finding |
|---|---|
| Purpose | "A subreddit for sharing and receiving constructive feedback on side projects." Showing your own work is the point of the room. |
| Self-promotion | Allowed and expected. |
| Published rules | The public rules page displayed **no subreddit-specific rules** when a tracker verified it on 2026-09-29. The binding statement is the sidebar one-liner above. |
| Account age / karma | **No published minimum.** |
| Links | No published link policy; links are normal in this sub. |
| Flair / format | No flair requirement reported. Sidebar asks for a title of the form **"Project name - short description"**. |
| Video | Yes — native video/image posts are the norm and get the most engagement. |
| What gets removed | Link-only "check this out" posts, ads with no build story, reposts within days, affiliate/referral links, vote requests. Moderation is active: of the newest 100 posts in the week to 2026-11-04, **67 were removed**. |
| Verdict | Post here first, as a maker telling a story with one concrete feedback question. |

### r/androiddev — **do not post a launch post**

| Item | Finding |
|---|---|
| Rule 2 (official, "Must be Related to Native Android Development") | "Sharing applications or recruiting testers is **not allowed**." |
| Rule 4 | Posts must be generally applicable, not about one person's project. |
| Rule 5 | Demonstrate effort; posts must be in your own voice; "content indicative of LLM output, including excessive flourish, gushing or emojis, will be removed." |
| The sanctioned exception | A monthly **"Interesting Android Apps: \<Month\> \<Year\> Showcase"** thread by mod u/3dom: *"although we typically do not allow self promotion, we wanted to create a space where you can share your latest Android-native projects…"* Mods ask you to state whether the app is free, paid or subscription. |
| Status of that exception | The series ran monthly (Sep 2025 → Jul 2026). A 2026-10-28 post asks "Interesting Android Apps Showcase posts, are they gone for good?" — the announced October thread did not appear, and no Aug/Sep/Oct/Nov 2026 thread is indexed. **No open showcase slot exists right now.** |
| Verdict | **No promotional post.** Either (a) reply in the next showcase thread when one appears, using the comment draft in `REDDIT_POSTS.md`, or (b) publish a genuine technique post about the Compose Canvas 2.5D renderer with no download links — and accept that mods may still read it as promotion. Default: wait for the thread. |

### r/developersIndia — **engineering post, not a launch**

| Item | Finding |
|---|---|
| Current policy (mod announcement, 2026-08-02, "A quick note regarding the direction of r/developersIndia") | Stricter enforcement of low-effort, repetitive, self-promotional and AI-generated content. |
| Project sharing | Use the **"I Made This" flair**. The purpose must be "to share your work with the community, not to use the community to find users". The post must explain: what problem you solved, why that stack, what challenges you hit, what trade-offs you made, what you learned. |
| Explicit red line (quoted) | "Posts that are primarily meant to drive traffic to a website/application (asking people to install your app, join your waitlist, star your repo, etc.) will be considered self-promotional and dealt with as such." |
| Other published rules | Search before posting · correct flair · no low-quality posts · no generic questions · no NSFW · no politics · respectful and inclusive language · follow moderation · no memes or rants · no meta drama · code of conduct · no direct job/freelance posts. |
| Account age / karma | No published minimum found. |
| AI-generated text | Explicitly removed as low-effort. Write it yourself, in your own voice, with real specifics. |
| Verdict | One engineering write-up under "I Made This": offline-first deterministic simulation + a procedural renderer in Compose Canvas. One link line at the end. **No install CTA.** |

### r/droidappshowcase — **launch post, template required**

| Item | Finding |
|---|---|
| Purpose | "The premier showcase for Android developers to share apps and users to find exclusive deals and promo codes." |
| Rule 1 | Android content only. |
| Rule 3 | Civil, specific feedback. |
| Rule 4 | Use the suggested template / only high-quality posts. "Posts that are just a link with no description may be removed." |
| Rule 5 | No spam or excessive self-promotion: **1 app post per week per account**, no cross-sub link dumping, no affiliate links, no promo-only accounts. |
| Rule 6 | Every showcase must include a **direct link from a reputable source** — Google Play, Galaxy Store, F-Droid, **GitHub**, etc. Unapproved/low-reputation domains are not allowed. |
| Rule 7 | SFW only. |
| Rule 8 | Account age **24 h+** and **2+ combined karma**; brand-new accounts are auto-removed — a real person with a new account can message the mods for approval. |
| Template (from the mod removal message) | Descriptive title, e.g. `"Mycoolappname - easy cooltimer - free"`; body = app name + link · detailed description + key features · screenshots/GIF demo · pricing + privacy info. |
| Flair | Promo, Giveaway or Feedback. |
| Observed today (RSS, 2026-11-10) | Dozens of developer posts per day in exactly that shape; `[Promo]`, `{FREE}`, `App Name / What it does / Key Features` are house style. Removals cite Rule 4 (template) and Rule 5 (one post per 7 days). |
| Verdict | One templated post, native video, GitHub link, honest note that it is not on Play Store yet. |

---

## 3. Positioning (the same story everywhere, in different shapes)

**Say:** a character-first Android app about a small robot who has his own moods, memories, projects,
trips, a pet called Nib, and a world that keeps moving while you are away. Everything is drawn
procedurally in code; the simulation is deterministic Kotlin; there is no account, no analytics, no ads.

**Do not say:** "AI companion", "AI chatbot", "your AI friend", "ChatGPT in a robot", "virtual pet with
GPT". Pipo's chat *wording* can come from an LLM if you run your own proxy — that is a footnote, not the
product. The product is the life simulation.

**The honest line about online vs offline — this matters and must not be fudged:**

- The **public v1.0 APK contains no API keys and no proxy URL**. Verified: `dist\Pipo.apk` has zero
  `workers.dev` strings and an empty `PIPO_PROXY_URL`, so **in the APK people download today, chat is
  fully offline** — he answers in his own words, from the rule-based `LocalBrain`.
- Online things that *do* work in that APK: optional **real weather** (approximate city from your IP →
  ipwho.is + Open-Meteo), and the links he opens for you (YouTube, Google, apps, maps). All optional.
- Online AI (chat wording, "Pipo, look!", "find out") requires **your own proxy** (`server/`, a
  Cloudflare Worker holding the keys) or a local build with a proxy URL. It is not in the download.
- The **3D web world** (`aspire488.github.io/Pipo/`) is a separate, self-contained Three.js build. No
  account, no keys, no AI service. It runs on its own clock.

**Never claim:** a Play Store listing, a user count, reviews, testimonials, "thousands of downloads",
iOS support, "no permissions" (it declares 13, all optional-in-practice except INTERNET/network state),
or that Play Protect has cleared the app.

**Do say, plainly:** it is a signed APK from GitHub, Play Protect may ask you to confirm the install
because it is not distributed by Play, and that is expected for any sideloaded app.

---

## 4. Account and timing plan

| When | What | Where |
|---|---|---|
| Before anything | Use the real developer account. Spend a few days commenting genuinely in the target subs (not "nice app!"). Reddit's spam filter and mods both read account history. | all |
| Day 0 (Tue–Thu, 08:00–11:00 US Eastern = 17:30–20:30 IST) | r/SideProject post + native `Pipo-Intro.mp4` | r/SideProject |
| Day 0 + 2–3 days | r/droidappshowcase templated post + native video, flair **Promo** | r/droidappshowcase |
| Day 0 + 4–6 days | r/developersIndia "I Made This" engineering post, no video needed (a still or short clip is fine) | r/developersIndia |
| Whenever a showcase thread appears | One comment in the r/androiddev monthly thread | r/androiddev |
| After each post, 24–48 h | Answer every comment, including the hostile ones, with specifics | all |

Rules that keep this from becoming a spam pattern:

- **Never the same text twice.** The four drafts in `REDDIT_POSTS.md` share facts, not sentences.
- **Never the same day.** Spread at least two days apart; droidappshowcase enforces 7 days between
  app posts per account.
- **One post per community.** No reposting the same project a week later "for visibility". A real
  update (v1.1, a new feature, a fix someone asked for) is the only reason to post again.
- **No vote requests, no alt accounts, no "my friend made this".** Sitewide violation, and it would
  burn the account that owns the repo.

---

## 5. Baseline and measurement

Set expectations before posting: the GitHub release has **4 downloads total** since 2026-10-04. A good
outcome from this launch is tens of installs and a handful of real bug reports — not thousands. Track:

- GitHub release download count (public API) before/after each post,
- which post produced comments that name a real problem (that is the only signal worth acting on),
- installs that turn into issues on the repo.

Do not ask for stars, upvotes or installs in the post body. The link is there; that is enough.

---

## 6. Risk table

| Risk | Likelihood | Mitigation |
|---|---|---|
| r/SideProject removes it as low-effort promo | Medium (67/100 posts removed in a sampled week) | Lead with the build story and a real question; native video; reply to every comment |
| r/androiddev removes a promo post | **Certain** — Rule 2 forbids it | Do not post one. Wait for the monthly showcase thread |
| r/developersIndia removes it as traffic-driving | High if it reads like a launch | Frame as engineering; "I Made This" flair; no install CTA; one link line |
| r/droidappshowcase removes it for template/flair/frequency | Medium | Use the exact template, correct flair, one post, account older than 24 h with 2+ karma |
| Someone calls it "just another AI chatbot" | Medium | The posts define it as a life simulation first; the LLM is a footnote |
| Someone reports it as malicious after the Play Protect prompt | Low–medium | Be upfront about the sideload prompt in the post and the FAQ; link the security report |
| Reddit flags the account for cross-posting | Medium | Different text, different days, genuine comments in between |
