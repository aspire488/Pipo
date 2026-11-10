# Pipo — screenshot capture guide

**Why this file exists:** no usable store screenshots exist. `docs/preview/*.png` are from the old
design (different robot, "Suit off (Mk I)" HUD, low-poly forest) and the 412×916
`docs/preview/walkthrough.mp4` is silent and outdated. Using them would misrepresent the build. One
capture session on a real phone unblocks the itch.io, Product Hunt, Samsung and Play listings.

Screen names below are the real composables: `HomeScreen`, `NibScreen`, `JournalScreen`, `MapScreen`,
`CollectionScreen`, `SettingsScreen`, plus the games.

---

## 1. Before you start

- Install the **v1.0 release APK** (`911b61a5…3526`), or your v1.1 build if you are capturing for the
  future listing. **Do not upload these as v1.1 evidence until v1.1 is released.**
- Play for **at least 5 minutes first**, so his journal has entries, he has a mood, and nothing reads
  as a fresh install with an empty world.
- Clean the status bar: dismiss notifications, or use a phone with no personal accounts. **Never
  capture a screen showing a real message, sender name or notification text** — Pipo's notification
  listener is exactly the feature people are wary of, and a screenshot leaking someone's message
  would be a privacy failure.
- Set a consistent time of day. He has day/night lighting; mixed captures look accidental.
- Brightness ~70%, dark mode off if it changes his colours, screen rotation locked to portrait.

## 2. Format

| Property | Value |
|---|---|
| Orientation | **Portrait** (Pipo is portrait-only) |
| Resolution | Native 1080×1920 or higher; never upscale |
| Format | PNG |
| Device frame | **None** — no phone mockups, no rounded-corner masks |
| Added text | **None** — no captions, arrows or marketing copy baked in |
| Count | 6–8. Fewer, better ones beat a padded set |

Play requires 2–8 phone screenshots, min 320 px, max 3840 px, 16:9 to 9:16. Samsung asks for similar.
Product Hunt uses 1270×760 for gallery images, so crop or letterbox from these without altering the
game UI itself.

## 3. The shots, in order

| # | Screen | How to get there | What it must communicate |
|---|---|---|---|
| 1 | **Home room, Pipo present, daylight** | Launch the app | *This is the product.* The hero shot: the room, him in it, the window light. Must read in 2 seconds at thumbnail size. |
| 2 | **Pipo mid-reply, speech bubble + chat bar** | Tap chat (the chat icon on the home screen), type `hello pipo`, send | He is a character you talk to, not a dashboard. Include the real input text and his reply. |
| 3 | **Nib** | `NibScreen` (or Nib visible in the room) | The pet is a real, secondary character with its own moods. |
| 4 | **Journal** | `JournalScreen` | Persistent memory: "found / built / played / went out" entries in his own voice. This is the differentiator vs a toy. |
| 5 | **Map / trips** | `MapScreen` | He has somewhere to go and a world beyond one room. |
| 6 | **A game in progress** | Games (the games icon), pick one — reaction, memory or football | There is actual play, 8 games deep. |
| 7 | **Collection / objects** | `CollectionScreen` | Things he found and kept. Shows continuity and hoarding-as-personality. |
| 8 | **Evening lighting, different activity** | Leave the app, change the device clock to evening, reopen | The room changes with time. Proves it is a simulation, not a static scene. |

If you only do four: **1, 2, 4, 5.**

## 4. Screens to avoid

| Avoid | Why |
|---|---|
| `SettingsScreen` | Reads as an admin panel, and exposes toggles that invite "why does it need that?" before the user cares. |
| `docs/preview/*.png` | Superseded UI. Using them is misrepresentation. |
| Any Play Protect / "unsafe app" dialog | That is appeal evidence, not marketing. It belongs in `security/play-protect-investigation/`. |
| Developer logs / `GameLog` overlays | Exposes internals and looks unfinished. |
| Empty states | Fresh-install "no memories yet" screenshots sell nothing. |
| Anything containing a real notification, name, phone number or message | Privacy failure, and it undermines the notification-access story. |
| Two near-identical room shots | Wastes a slot; each shot must add a distinct claim. |

## 5. After capturing

```bash
# Store them here (Play/F-Droid both read this path)
mkdir -p fastlane/metadata/android/en-US/images/phoneScreenshots
# name them 01.png .. 08.png, portrait, no spaces
```

Then update `launch/STORE_DISTRIBUTION_MATRIX.md` — the itch.io, Product Hunt, Samsung and Play rows
all become "ready" rather than blocked on assets.

**Do not** add captions in an image editor, and do not run them through a filter. Store listings that
misrepresent the UI get rejected, and the whole point here is that the real thing is good enough.
