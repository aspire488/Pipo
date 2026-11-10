# Pipo — posting order

The order matters more than the volume. One room at a time, two days apart, each with a different
angle, each with the video that fits that room. **Nothing has been posted.**

All videos are used **as they are**. No cropping, no re-encoding, no captions burned in, no trimming.
Where a platform needs vertical, use the vertical file that already exists.

---

## Pre-flight (do this before the first post)

- [ ] Log in as the real developer account that owns `github.com/aspire488/Pipo`.
- [ ] Account is older than 24 h and has 2+ combined karma (hard requirement in r/droidappshowcase).
- [ ] Spend 2–3 days leaving genuine comments in r/SideProject and r/developersIndia first. Not "nice
      app" — actual answers. Both rooms notice accounts that only appear to post.
- [ ] Have the files ready locally, not re-downloaded:
      `dist\Pipo-Intro.mp4`, `dist\Pipo-Intro-Vertical.mp4`, `dist\pipo-39s-bgm.mp4`,
      `dist\pipo-promo.mp4`, `dist\pipo-promo-vertical.mp4`.
- [ ] Check the links resolve: release tag, APK asset, `aspire488.github.io/Pipo/`, `/classic/`.
- [ ] Note the GitHub download count before you start (currently **4 total** on v1.0), so the effect is
      measurable rather than imagined.
- [ ] Re-read each subreddit's live rules page on the day. The research in `REDDIT_LAUNCH_PLAN.md` is
      dated 2026-11-10 and Reddit rules change.

---

## Order

| # | Day | Where | What | Video | Why this order |
|---|---|---|---|---|---|
| 1 | Day 0 (Tue–Thu, 08:00–11:00 US Eastern) | **r/SideProject** | Story post, one feedback question | `dist\Pipo-Intro.mp4` (26 s, landscape) | The friendliest room, the one where a maker's story is the point. Start where the post cannot be "off-topic". The real-app recording is the most honest proof of what the download is. |
| 2 | Day 2–3 | **r/droidappshowcase** | Templated showcase, flair **Promo** | `dist\Pipo-Intro-Vertical.mp4` (26 s, vertical) | A dedicated Android showcase, so the angle changes from "here's my project" to "here's an app you can install". Portrait media matches the house style. |
| 3 | Day 4–6 | **r/developersIndia** | Engineering write-up, flair **I Made This** | optional still, or the 26 s clip | This room removes launch posts. It goes third, after you have two threads' worth of real questions to fold into the engineering story. |
| 4 | Whenever a showcase thread appears | **r/androiddev** | Comment in the monthly "Interesting Android Apps" thread | none | There is no open thread right now (last one: July 2026). Do not create a post; wait, and use the comment draft. |
| 5 | Day 7–10 | **Instagram Reels** | The official reel already exists — reuse it, do not re-upload a duplicate on day one | `https://www.instagram.com/reel/DeEN_MExPP8/?cplk=aGZtdDE4eWNwdDk3` | The reel is the existing public artefact. Cross-link it from the Reddit posts instead of competing with it. If a second reel is wanted later, `dist\pipo-promo-vertical.mp4` is the file (98 s, vertical) — posted as its own piece, not as an edit of the first. |
| 6 | Day 10–14 | **YouTube** | The full film + the app recording as two separate uploads | `dist\pipo-promo.mp4` (98 s) and `dist\Pipo-Intro.mp4` (26 s) | Long-form home for the film. The 98 s piece has nowhere better to live; YouTube tolerates length that a feed does not. |
| 7 | Day 14+ | **X / Bluesky / LinkedIn** | One post: the 39 s cut + the web world link | `dist\pipo-39s-bgm.mp4` (39 s) | Short, silent-autoplay-friendly, and the web world needs no install, which suits a scroll-past audience. |
| — | ongoing | **GitHub release notes** | Keep the v1.0 release page as the canonical download page; put the film and the web world in the notes | link only | Anyone arriving from a Reddit comment lands here. It should not be a bare asset list. |

### Why not all four subreddits on one day

- Identical text across rooms is the single most reliable way to get every post removed and the
  account flagged.
- r/droidappshowcase enforces **one app post per 7 days**; burning it on day 0 means no second attempt
  if the first is removed for a template detail.
- r/developersIndia's moderators are explicitly enforcing against "traffic-driving" posts as of
  2026-08-02. A launch-shaped post there is a removal, and a removal in a sub you plan to use again
  costs more than the post was worth.

---

## Per-post checklist

**r/SideProject**
- [ ] Title: `Pipo - a little robot who lives in your phone and gets on with his own day`
- [ ] Video uploaded natively (not a link to a file host)
- [ ] Body from `REDDIT_POSTS.md` §1, unchanged except for anything you have learned since
- [ ] One question at the end, and you actually answer the replies
- [ ] No "please upvote", no cross-post note, no edit begging for attention

**r/droidappshowcase**
- [ ] Flair: **Promo**
- [ ] Title: `Pipo - a robot companion who lives his own life - free`
- [ ] All five template parts present: name, direct link, description, features, pricing + privacy
- [ ] Video uploaded natively (vertical)
- [ ] Sideload/Play Protect caveat included — it prevents the "why does it warn me" thread

**r/developersIndia**
- [ ] Flair: **I Made This**
- [ ] Reads as engineering, not as a launch: problem, stack, challenges, trade-offs, lessons
- [ ] Exactly one link line, no install CTA
- [ ] Written by hand; no LLM-flavoured phrasing (explicitly removed there)

**r/androiddev**
- [ ] There is an open monthly showcase thread
- [ ] Comment, not a post
- [ ] States free/no ads/no IAP, as the mods ask
- [ ] No emoji, no gushing (Rule 5)

---

## What to do with the answers

- **Comments naming a real bug** → fix it, then reply with the commit. That reply is worth more than
  the launch post.
- **"It's just an AI chatbot"** → answer with one concrete behaviour (he left for the shop and left a
  note; the note is still on the door). Do not argue taxonomy.
- **"Play Protect blocked it"** → the FAQ answer, plus the SHA-256 and the audit folder. Never tell
  someone to disable Play Protect.
- **"Why not Play Store"** → the honest answer: not ready to maintain a listing, and the compliance
  work (data safety, notification listener, AI disclosure) is real.
- **Hostile or dismissive** → one short factual reply, then stop. Arguing in a launch thread is the
  fastest way to make the thread about you.

---

## Do not

- Do not post the same text, or the same first paragraph, in two rooms.
- Do not post to r/androiddev as a post.
- Do not post to r/androidapps — its Rule 2 sends all self-promotion to r/droidappshowcase.
- Do not upload the videos anywhere else (no streamable/imgur mirrors) as part of this launch.
- Do not modify, trim or re-encode a video to fit a platform. Use the existing landscape or vertical
  file.
- Do not buy upvotes, use alt accounts, or ask friends to comment. Sitewide violation.
- Do not claim a Play Store listing, a user count, reviews or a Play Protect clearance.
