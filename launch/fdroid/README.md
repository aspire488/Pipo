# F-Droid submission — com.pipo.robot

**Status:** metadata prepared, **merge request NOT filed**. Filing needs a GitLab account
(`gitlab.com`), which does not exist in this environment and cannot be created without the owner
completing email verification.

The file next to this one, `com.pipo.robot.yml`, is the exact metadata file F-Droid's `fdroiddata`
repository expects at `metadata/com.pipo.robot.yml`.

## 1. Eligibility — assessed against the current inclusion policy

| Requirement | Pipo | Verdict |
|---|---|---|
| Public source repository | `https://github.com/aspire488/Pipo` | ✅ |
| FOSS licence | MIT (`LICENSE`) | ✅ |
| FOSS-only dependencies | AndroidX, Compose, WorkManager, kotlinx.serialization/coroutines, Vosk `com.alphacephei:vosk-android:0.3.75` (Apache-2.0), JNA (Apache-2.0/LGPL) | ✅ no Firebase, no GMS, no crash/analytics/ads SDK |
| Buildable from source with FOSS tooling | Gradle wrapper, Android SDK, no proprietary IDE, no Unity source required (`settings.gradle.kts` includes the Unity module only if it is present) | ✅ expected |
| Tagged releases | `v1.0` exists | ✅ |
| Upstream Fastlane metadata | Added: `fastlane/metadata/android/en-US/` (title, short + full description, icon, changelog) | ✅ |
| No embedded credentials | Provider keys are empty in every build type since `0d29bcc` | ✅ |
| Network services | Optional weather via `ipwho.is` + `api.open-meteo.com`; one-time Vosk model download from `alphacephei.com`; optional user-hosted AI proxy | ⚠️ reviewer decides whether this warrants a `NonFreeNet` anti-feature |

**Judgement: eligible.** The only open question is the anti-feature classification of the optional
network services, which is a reviewer decision, not a blocker. Nothing needs to change in the code for
a first inclusion attempt.

## 2. One caveat that must be disclosed to users

F-Droid builds and signs the APK **with F-Droid's key**. That APK therefore has a **different signing
identity** from the GitHub release APK (`CN=Pipo`, SHA-256 `6ff4c9b3…adafaf7`). On Android this means:

- a user on the GitHub APK **cannot** update to the F-Droid build in place, and vice versa;
- switching sources requires uninstall + reinstall, which **loses the on-device save**.

This is normal for F-Droid, but it should be said plainly wherever both are offered.

## 3. Exact steps once GitLab is authenticated

```bash
# 1. Fork + clone fdroiddata
git clone https://gitlab.com/<your-username>/fdroiddata.git
cd fdroiddata

# 2. Copy the prepared file in
cp D:/pipo/Pipo/launch/fdroid/com.pipo.robot.yml metadata/com.pipo.robot.yml

# 3. Validate (this is the step that catches metadata mistakes)
#    Requires fdroidserver: pip install fdroidserver
fdroid lint com.pipo.robot
fdroid rewritemeta com.pipo.robot

# 4. Branch, commit, push
git checkout -b com.pipo.robot
git add metadata/com.pipo.robot.yml
git commit -m "New App: com.pipo.robot"
git push origin com.pipo.robot

# 5. Open the merge request against fdroiddata:master
```

The merge request should state: the app is MIT, the maintainer is the upstream author
(`aspire488`), and the optional network calls are documented in
`https://aspire488.github.io/Pipo/privacy/`.

## 4. Outstanding reviewer requirements to expect

- **BLOCKER — the release-signing guard would fail an F-Droid build.** `app/build.gradle.kts` throws
  `GradleException("Release signing is not configured…")` whenever a release artifact task
  (`assembleRelease`, `bundleRelease`, `installRelease`, `package*Release`) is in the task graph and
  `keystore.properties` is absent. F-Droid's build server has no keystore and does build a release
  APK, so the build would abort before producing anything. **Minimum legitimate fix:** let the guard
  be skipped explicitly, e.g. `if (!buildingReleaseArtifact || releaseSigningConfigured ||
  System.getenv("PIPO_UNSIGNED_RELEASE") == "1") { … }`, and have the F-Droid recipe set
  `PIPO_UNSIGNED_RELEASE=1`. This changes release security configuration, so it was deliberately
  **not** applied in this sprint — it needs the owner's decision.

- A build-server run of `v1.0` — the `prebuild`/`gradle` lines in the YAML assume the standard
  Android build with no NDK work beyond what Gradle pulls.
- A decision on the `NonFreeNet` anti-feature for the weather and Vosk-model hosts.
- Possible request for reproducible-build support (Pipo does not currently ship a
  reproducible-build configuration — this is a "nice to have", not a blocker for first inclusion).
- `CurrentVersionCode: 1` matches the `v1.0` tag. **After v1.1 is published**, bump `Builds` and
  `CurrentVersion` to the new tag; F-Droid's `UpdateCheckMode: Tags` will then track it automatically.

## 5. Do not claim

F-Droid inclusion has **not** been requested, is **not** pending review, and is **not** accepted.
