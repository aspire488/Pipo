<!-- Title: New app: Pipo
     Open: https://gitlab.com/aspire488/fdroiddata/-/merge_requests/new?merge_request%5Bsource_branch%5D=com.pipo.robot&merge_request%5Btarget_project_id%5D=36528&merge_request%5Btarget_branch%5D=master
     Tick "Allow commits from members who can merge to the target branch". Paste everything below this comment. -->

## Abstract for new app inclusion

### Please introduce the functions and usage of your app in summary, and also attach some screenshots.

Pipo is a small robot who lives in a room on your phone and runs his own day. He has ten traits, twelve moods and decaying memories, and a scoring engine picks his next activity (building things at his workbench, going out to shops and leaving a note on the door, playing with his robot pet Nib). He writes a journal, keeps a map of places he has visited, and there are eight small games to play with him. Chat is offline and rule-based. One JSON save file on the device; no account, ads or analytics.

Screenshots (real device captures): https://aspire488.itch.io/pipo · Source: https://github.com/aspire488/Pipo

### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.

It is a virtual companion that is not an assistant or a tamagotchi chore loop: the point is that he lives on without you and you find out what happened. Everything (character, room, weather, parallax) is drawn procedurally with Jetpack Compose Canvas; there are no image assets and no game engine.

## Checklist

### Policy

* [x] The app complies with the inclusion policy. Dependencies are FLOSS from Maven Central/Google only. Vosk (`com.alphacephei:vosk-android:0.3.75`, Apache-2.0) and JNA 5.18.1 aar (LGPL-2.1-or-later/Apache-2.0) ship prebuilt native libs from Maven Central — the same pattern Dicio (`org.stypox.dicio`, vosk-android 0.3.70 + jna 5.18.1) uses here. If you'd prefer libvosk built from source for this app, please say so.
* [x] The original app author has been notified — I am the author.
* [ ] Fastlane metadata: present upstream on `main` (`fastlane/metadata/android/en-US/`), **but the only tagged release (v1.0, `73afc91`) predates it**. That is why Summary/Description are inline in this file for now; they will be removed once the next tagged release carries fastlane upstream.

### Docs

* [x] Read the contributing guide, templates, Build Metadata Reference and Quick Start Guide.

### Merge Request Setup

* [x] Title follows "New app: app name".
* [x] Fork is public; branch `com.pipo.robot` is not protected.
* [x] No related fdroiddata/RFP issues exist.
* [x] One app in this MR.

### Metadata

* [x] `metadata/com.pipo.robot.yml`, valid YAML, LF line endings, formatted with `fdroid rewritemeta`; `fdroid lint` passes locally (fdroidserver 2.4.5).
* [x] Summary/Description are inline in the metadata because v1.0 predates the upstream Fastlane files; they can move to Fastlane once a later tagged release carries it.
* [x] Releases are tagged; AutoUpdateMode: Version / UpdateCheckMode: Tags. Note: from the next release on, an unsigned build needs `gradleprops: pipo.unsignedRelease=true` (explained in MaintainerNotes).
* [x] Issue tracker and AuthorName present.
* [x] No external repos/srclibs.
* [ ] Reproducible builds: not enabled for v1.0. Please sign with the F-Droid key. (The upstream GitHub APK uses a different key, so users can't cross-update between channels — that is understood.)
* [ ] ABI split not set up yet (APK ~27 MB, mostly libvosk for 4 ABIs). Happy to add splits if you'd like.
* [x] Only the latest version, no disabled versions, full commit hash.

**v1.0 build note:** v1.0's release build falls back to the debug signingConfig when no keystore exists, and `remove_signing_keys()` drops the `signingConfigs` block but not that line, so the `prebuild` sed removes it. I reproduced the F-Droid-style build locally (git archive of `73afc91` + remove_signing_keys + the sed + `gradle assembleRelease`): BUILD SUCCESSFUL, `app-release-unsigned.apk`.

**Anti-feature:** NonFreeNet — optional weather uses ipwho.is to estimate the city (weather data from open-meteo.com).

### Pipeline

* [ ] Pipelines: will check and fix/explain anything reported.

/label ~"New App"
