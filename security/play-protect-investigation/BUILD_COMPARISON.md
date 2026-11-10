# Build Comparison — Pipo

**Goal:** rebuild the project, compare against the publicly distributed APK, and explain every difference
without over-claiming reproducibility.

---

## 1. Artifacts compared

| Label | Path | What it is |
|---|---|---|
| **PUBLIC** | `dist/Pipo.apk` | The published `v1.0` asset (hash-verified against GitHub) |
| **REBUILT** | `app/build/outputs/apk/release/app-release.apk` | `assembleRelease` from the **current working tree** |
| **DEBUG** | `app/build/outputs/apk/debug/app-debug.apk` | Local `assembleDebug` from 2026-10-25 |
| **CI** | GitHub Actions artifact | **Not downloaded** — artifacts require auth and expire |

The original `app-release.apk` (before this investigation) was **byte-identical** to `dist/Pipo.apk`
(both `911B61A5…3526`), confirming `dist/` is a copy of a real Gradle release build. Rebuilding overwrote it;
the original hash is preserved below.

---

## 2. Side-by-side

| Property | PUBLIC (v1.0) | REBUILT (current tree) | DEBUG (local) |
|---|---|---|---|
| SHA-256 | `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526` | `f2899557a604a3181098b6c534370767e5a26104f910542144f8c23da4a218a7` | `064c3b55b20ce311cd4be40961f056820d7f09748f774798c63afd4087cff29b` |
| Size | 27,351,753 B (26.08 MiB) | 27,899,875 B (26.61 MiB) | 53,702,954 B (51.21 MiB) |
| Package | `com.pipo.robot` | `com.pipo.robot` | `com.pipo.robot` |
| Version name / code | `1.0` / `1` | `1.0` / `1` | `1.0` / `1` |
| minSdk / targetSdk | 26 / 35 | 26 / 35 | 26 / 35 |
| **Signer DN** | `CN=Pipo, O=Pipo` | `CN=Pipo, O=Pipo` | `C=US, O=Android, CN=Android Debug` |
| **Signer SHA-256** | `6ff4c9b3…adafaf7` | `6ff4c9b3…adafaf7` | `ee8a0f4a…e9394b` |
| Signature schemes | v2 | v2 | v2 |
| `debuggable` | **false** | **false** | **true** |
| ABIs | arm64-v8a, armeabi-v7a | arm64-v8a, armeabi-v7a | 7 ABIs (arm64, armv7, armeabi, x86, x86_64, mips, mips64) |
| ZIP entries | 161 | 181 | 199 |
| `assets/stage/**` | **absent** | **20 files present** | 20 files present |
| Native libs | 6 (vosk, jna, androidx.path ×2 ABIs) | 6 — **identical names and sizes** | more (extra ABIs) |
| Permissions | 13 | **13 — identical set** | 13 |

---

## 3. What is identical, and what that proves

**Identical:**
- Signing certificate (SHA-256 and DN) — the release key was used for both builds.
- Package name, version name, version code, minSdk, targetSdk.
- The full permission set (`Compare-Object` on `aapt2 dump permissions` returned no differences).
- All six native libraries, byte-for-byte in name and size.

**Proves:**
- The release signing configuration **works and is stable across rebuilds**.
- The distributed APK was produced by this project's build, not by a third party.
- The manifest/permission surface did not drift.

---

## 4. Why the hashes differ — every cause accounted for

### 4.1 Source drift (primary cause)

The working tree is **not** the released commit. `git status` shows **26 modified files** plus `settings.gradle.kts`,
and the APK gained **20 new `assets/stage/**` entries**:

```
assets/stage/index.html
assets/stage/stage.css
assets/stage/models/pipo.glb
assets/stage/models/lab.glb
assets/stage/src/{camera,character,face,forest,fx,pipo,preview-host,scene,sky,stage,textures,util}.js
assets/stage/utils/BufferGeometryUtils.js
assets/stage/vendor/{GLTFLoader,RoundedBoxGeometry,three.module.min}.js
```

That is the **Three.js 3D room** added after `v1.0` (commits `e69a76d` / `e7d76a8` and later uncommitted work).
Size delta: +548,122 bytes, consistent with those assets.

`settings.gradle.kts` was also changed to optionally include `:unityLibrary`. **The Unity export directory does
not exist**, so `:unityLibrary` was **not** included — confirmed by the absence of `libunity.so`/`libil2cpp.so`
and by the unchanged native library list.

### 4.2 Non-reproducible build mechanics (secondary cause)

Even from an identical tree, two `assembleRelease` runs normally produce different hashes. Contributing factors:

- ZIP entry timestamps and ordering.
- The v2 signature block (contains a random-ish signature over the digest).
- DEX layout and Kotlin compilation ordering.
- Resource table ordering / `resources.arsc` packing.
- `buildConfigField` timestamp/ordering effects are minimal, but AGP does not guarantee bit-identical output.

**No claim of reproducibility is made.** The project has not established reproducible-build conditions
(no pinned `SOURCE_DATE_EPOCH`, no `--no-build-cache` determinism check, no dependency verification lockfiles).
This is expected and is **not** a security finding.

---

## 5. The CI APK is a *different kind of artifact*

`.github/workflows/build-apk.yml`:

```yaml
- name: Build debug APK
  run: ./gradlew assembleDebug --stacktrace
- name: Unit tests
  run: ./gradlew testDebugUnitTest
- uses: actions/upload-artifact@v4
  with:
    name: pipo-debug-apk
    path: app/build/outputs/apk/debug/app-debug.apk
```

**CI only ever builds `assembleDebug`.** It never runs `assembleRelease`, and CI has no `keystore.properties`
(by design — the key is not in the repo). Consequently:

- The CI artifact is **debug-signed** with `CN=Android Debug`.
- It is `debuggable=true`.
- It is ~2× larger (7 ABIs + debug tooling).
- It **contains live API keys** if built where `local.properties` has them (not the case in CI, where they are
  empty — but the workflow is debug-only, so the point is moot for CI).

**Therefore the CI artifact can never be "the same APK" as the distributed one.** That is a release-process
limitation, not tampering. If CI is meant to produce release candidates, it needs a securely-injected signing
key (GitHub Actions secret → temporary `keystore.properties`, never committed).

CI health: **70 runs, latest run #70 on `main` (2026-11-09) — success.** Latest CI commit is `c2fc5ab`, far
ahead of the `v1.0` release (`2026-10-04`).

---

## 6. Tests and checks run

| Check | Command | Result |
|---|---|---|
| Unit tests | `gradlew testDebugUnitTest` | **PASS** — 266 tests, 39 classes, 0 failures, 0 errors, 0 skipped |
| Release build | `gradlew assembleRelease` | **PASS** — APK produced (27,899,875 B) |
| Release lint | `gradlew lintRelease` | **FAIL** — 1 error, 16 warnings (see below) |

> The `DEVICE_TEST_REPORT.md` records "68 / 68" tests. The current tree has **266** — the suite grew
> substantially after that report. The report is stale on this number.

### Lint: 1 error + 16 warnings

**Error — `JavascriptInterface` (`ui/home/Stage3D.kt:68`)**

```
Error: None of the methods in the added interface (T) have been annotated with
@android.webkit.JavascriptInterface ... [JavascriptInterface]
    addJavascriptInterface(bridge, "PipoAndroid")
```

**This is a false positive.** All four bridge methods *are* annotated
(`Stage3D.kt:135-139`). Lint reports the type as `T` because `val bridge = remember { StageBridge(...) }`
resolves to `remember`'s generic return type, which lint's UAST cannot see through. The app is correct at
runtime; lint simply fails to resolve the type.

**Minimal, behaviour-neutral fix** (do not apply without approval):

```kotlin
val bridge: StageBridge = remember { StageBridge(...) }
```

This gives lint a concrete type. No runtime change.

**Warnings (16)** — all pre-existing and non-security:

| Count | Issue | Note |
|---|---|---|
| 9 | `GradleDependency` — newer versions available | Deliberate pinning; see `DEPENDENCY_AUDIT.md` |
| 2 | `LockedOrientationActivity`, `DiscouragedApi` | Intentional portrait lock for the room |
| 1 | `ChromeOsAbiSupport` | No x86_64 — intentional (ARM phones only) |
| 1 | `DataExtractionRules` | Moot: `allowBackup="false"` already covers pre-Android-12 |
| 1 | `ObsoleteSdkInt` (`mipmap-anydpi-v26`) | Required by `aapt2` for adaptive icons (per `DEVICE_TEST_REPORT.md`) |
| 2 | `StaticFieldLeak` (`unity/UnityStage.kt`) | In the **optional Unity module**, which is not in the build |

**No security-relevant lint finding.** `lintRelease` failing on a false positive is a CI hygiene issue: it
means a release pipeline that runs lint would be red. Worth fixing the annotation line so lint is meaningful.

---

## 7. Answer: does the APK differ materially from the latest CI build?

**Yes — but only in ways explained by build type and source drift, none of them security-relevant.**

| Difference | Cause | Security impact |
|---|---|---|
| Different signer (Pipo vs Android Debug) | CI builds debug only | Expected; the *public* APK is correctly release-signed |
| `debuggable=true` in CI artifact | debug build type | Expected |
| 53.7 MB vs 27.4 MB | 7 ABIs vs 2 | Expected |
| +20 `assets/stage/**` files | Source drift after v1.0 | None — sandboxed WebView assets |
| Different hash | Source drift + non-reproducible build | Expected |

**Nothing suggests the public APK was built from different or tampered source.** The certificate, package,
version, permission set, and native libraries all match.

---

## 8. Release-engineering observations (not security findings)

1. **`versionCode` is still `1`** while `main` has advanced far past `v1.0`. Any future release must bump it,
   or users cannot be offered a meaningful update and stores will reject the upload.
2. **CI cannot produce a release APK** (no signing key) — release builds are manual and machine-dependent.
   That is exactly the condition under which the debug-key fallback (`SIGNING_AUDIT.md` §5) could silently
   bite.
3. **`lintRelease` is red** on a false positive — a release gate that always fails gets ignored.
4. **No dependency verification / lockfiles** — dependency resolution is not pinned against tampering.

---

## 9. Limitations

- The CI artifact itself was **not downloaded** (auth + expiry). Its properties are inferred from the workflow
  definition and from the equivalent local debug build, and are labelled as such.
- The rebuild used the **current working tree**, not the released commit, because the released commit's exact
  tree state includes uncommitted-at-release changes that are not recoverable. Differences are therefore
  expected and are not attributable to tampering.
- No byte-level DEX diff was performed (no `jadx`/`baksmali`); comparison is at manifest, permission, native
  library, and archive-entry level.
