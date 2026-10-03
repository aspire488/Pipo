# Pipo — Privacy Policy

_Last updated: 2 October 2026. Contact: https://github.com/aspire488/Pipo/issues_

Pipo is a small robot who lives on your phone. This policy explains what the app does with
your information. In short: Pipo's world is stored on your phone, and the only things that leave
it are the requests needed for features you use, described below.

## What stays on your phone

- **Pipo's whole life**: his memories, journal, things he owns, projects, photos he "took" (drawn
  pictures, not your photos), Nib, games and settings. They're saved in the app's private storage and
  are never uploaded. They're deleted when you uninstall the app or reset Pipo in Settings.
- **A random install ID**, used only to rate-limit AI requests (see below). It isn't linked to you.

## What is sent off your phone, and why

| Feature | What is sent | To whom | Kept? |
|---|---|---|---|
| Talking to Pipo (typed or spoken) | What you said, the last few lines of conversation, and a short summary of Pipo's state (his mood, what he did today, his things) | Our AI proxy, then Google (Gemini) or, as a backup, Groq | Not stored by us. The providers process it under their terms. |
| "Pipo, look!" / showing Pipo a photo | One photo **you take or choose**, shrunk to small size | Our AI proxy, then Google (Gemini) or Groq | Not stored by us. The photo is deleted from the phone right after Pipo looks. |
| "Find out…" / "ask Gemini…" | Your question | Our AI proxy, then Google or Groq | Not stored by us |
| "Ask ChatGPT/Claude/… X" | Your question, opened in your browser | The service you named, directly in your browser | Under that service's terms |
| Real weather | Your network address (to estimate your city), then that city's coordinates | ipwho.is, then Open-Meteo | Not stored by us |
| Voice input (tap the mic) | Your speech | Your phone's speech recognition service (usually Google), asked to recognise **on the phone** (offline) | Under that service's terms |
| Pipo Voice (the optional "Pipo, ..." listener) | Nothing. Recognition runs **inside Pipo, on your phone**, with an offline speech model; audio and words never leave the phone and are dropped as soon as they've been checked for a call to Pipo | Nobody | Nothing is kept |
| Pipo Voice's speech model (one-time download when you switch Pipo Voice on) | Nothing about you: Pipo downloads a public file (Vosk small English model, ~40 MB) and checks its fingerprint before using it | alphacephei.com (the model's publisher) | Not applicable |
| "Play X" / "search X" | Your search | YouTube / Google / the app you named | Under their terms |

Pipo never sends your contacts, your files, your exact location, or your other apps' content.

## Permissions

- **Microphone**: only while you tap the mic to talk to Pipo — and, only if you switch it on in
  Settings → "Pipo Voice", while that feature's foreground service runs. It announces itself with a
  notification you can turn it off from. It listens only while your screen is on, the phone is
  unlocked, Pipo is closed and you're not on a call. It never pauses, lowers or interrupts your
  music or videos (it takes no audio focus and makes no sound). Audio is processed in memory on the
  phone and never recorded, stored or sent: anything that isn't a call to Pipo is dropped at once.
  Off by default; having the microphone permission does not turn it on.
- **Display over other apps (optional, Android's own switch)**: only so that, when you call
  "Pipo, ..." from another app, he can briefly show his face and come on screen. Without it you get
  a notification to tap instead. The bubble can't be touched and sees nothing.
- **Device admin (the "Lock helper")**: only if you switch it on yourself on Android's own
  activation screen. It requests exactly one thing — lock the screen, like the power button — so
  "Pipo, lock" works. It cannot read your screen, apps or typing, and Pipo uses no accessibility
  service. Unlocking works the normal way: fingerprint, face or PIN.
- **Notifications**: so Pipo can send you an occasional message (you choose how often, or turn it off).
- **Notification access (optional, you turn it on in Android settings)**: lets Pipo notice that
  you got a message or reel ("Your phone went ding!"). The app sees only **which app** and a **coarse
  kind** (message, photo, reel, call). The notification text is used only on the phone to decide
  that kind, then discarded. Nothing is stored, logged or sent anywhere, and Pipo never opens,
  replies to or dismisses your notifications.
- **Modify system settings / Do Not Disturb access (optional)**: only to do what you ask
  ("brightness down", "do not disturb on").
- **Set alarms**: only when you ask Pipo to set one.
- **Camera**: Pipo doesn't have camera permission. "Pipo, look!" opens **your own camera app**, and you take the photo.

## People in photos

When you show Pipo a photo, he's instructed never to identify anyone, guess names, ages or who
someone is, or comment on anyone's appearance. He can only say a general hello. Please don't show
him photos of people who wouldn't want that.

## Children

Pipo is intended for users aged 13 and over. (If you publish Pipo through Google Play's "Designed
for Families" program, replace this sentence with your declared Families Policy compliance.)

## Your choices

- Don't use the AI features, and Pipo talks with his own built-in words. Everything else still works.
- Turn off notifications or notification access at any time in Android settings.
- Reset Pipo in Settings, or uninstall, to delete all his data from your phone.

## Changes

We'll update this page if anything changes and change the date at the top.
