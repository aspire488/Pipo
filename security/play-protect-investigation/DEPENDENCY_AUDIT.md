# Dependency Audit — Pipo

**Configuration analysed:** `releaseRuntimeClasspath` (what actually ships in the public APK).
**Command:** `gradlew :app:dependencies --configuration releaseRuntimeClasspath`
**Artifact:** `com.pipo.robot` v1.0, SHA-256 `911b61a5…3526`.

---

## 1. Summary

| Metric | Value |
|---|---|
| Direct dependencies | 16 |
| Distinct resolved artifacts (requested + overridden) | 177 |
| Distinct Maven groups | 33 |
| **Third-party SDKs (non-AndroidX/JetBrains)** | **2** — `com.alphacephei:vosk-android`, `net.java.dev.jna:jna` |
| Analytics / ads / attribution / crash-reporting SDKs | **0** |
| Network HTTP client libraries (OkHttp/Volley/Retrofit) | **0** |
| Known-vulnerable dependency identified | **None found** (see §5 limitation) |

This is an unusually **lean** dependency graph for a modern Android app. There is no tracking, advertising,
crash-reporting, or social SDK of any kind — which is itself relevant to the Play Protect question, because
those SDKs are common sources of both privacy findings and reputation flags.

---

## 2. Direct dependencies

| Dependency | Version | Purpose | Assessment |
|---|---|---|---|
| `androidx.core:core-ktx` | 1.13.1 | Core Kotlin extensions | Current at build time |
| `androidx.activity:activity-compose` | 1.9.3 | Compose activity host | Current at build time |
| `androidx.lifecycle:*` (runtime, viewmodel-compose, process, runtime-compose) | 2.8.7 | Lifecycle/process foreground tracking | Current at build time |
| `androidx.compose:compose-bom` | 2024.10.01 | Compose version alignment | Pins Compose 1.7.5 / Material3 1.3.1 |
| `androidx.compose.ui:*`, `foundation`, `material3` | via BOM | UI | — |
| `androidx.work:work-runtime-ktx` | 2.9.1 | Hourly `PipoWorker` | Older (2.12.0 available) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | JSON | Safe config (§4) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | Coroutines | — |
| `com.alphacephei:vosk-android` | 0.3.75 | Offline speech recognition (Apache-2.0) | Legitimate, well-known; ships `libvosk.so` |
| `net.java.dev.jna:jna` | 5.18.1 | JNI bridge used by Vosk | Recent version |
| `junit` / `androidx.test.*` | — | **Test only** — not in the release APK | — |

---

## 3. Transitive surface

All transitive groups are first-party platform libraries:

```
androidx.activity, androidx.annotation, androidx.arch.core, androidx.autofill,
androidx.collection, androidx.compose.*, androidx.concurrent, androidx.core,
androidx.customview, androidx.emoji2, androidx.graphics, androidx.interpolator,
androidx.lifecycle, androidx.profileinstaller, androidx.room, androidx.savedstate,
androidx.sqlite, androidx.startup, androidx.tracing, androidx.versionedparcelable,
androidx.work, com.google.guava (listenablefuture), org.jetbrains.*
```

- **`androidx.room` 2.5.0** and **`androidx.sqlite` 2.3.0** arrive transitively via **`androidx.work`**
  (WorkManager's `WorkDatabase`). They are not used directly by app code and no app data is stored in Room.
- **`com.google.guava`** is `listenablefuture` (a single-interface artifact required by AndroidX), not the full
  Guava library.

**No unexpected or unexplained library was found.** Every artifact traces to a declared dependency.

---

## 4. Data-handling libraries and unsafe deserialization

The app parses JSON from two untrusted sources (AI provider responses, weather APIs) and from its own
persisted state file.

All `Json` instances are configured as:

```kotlin
Json { ignoreUnknownKeys = true }
```

(`ChatBrain.kt:94`, `Vision.kt:27`, `RealWeather.kt:22`, `UnityBridge.kt:25`, `Migrations.kt:6`)

- **No polymorphic serialization** — no `polymorphic { }`, no `classDiscriminator`, no `serializersModule`.
  This is the important point: kotlinx.serialization's unsafe-deserialization class of bug requires polymorphic
  configuration, which is absent.
- Network payloads are largely handled with `parseToJsonElement(...)` (DOM traversal, no object instantiation
  from attacker-controlled type names).
- `decodeFromString` is used for the app's **own** state file, not for network input.

**Assessment:** no unsafe deserialization risk identified.

---

## 5. Vulnerability assessment — what was and was not done

**Performed:**
- Full resolved dependency tree extracted and reviewed by group/artifact.
- Manual review for known-risky categories (HTTP clients, deserialization, native bridges, WebView helpers).
- Android Lint `GradleDependency` check (reports 9 dependencies behind current stable).

**Not performed (limitation):**
- **No automated software-composition analysis (SCA) was run.** No OWASP Dependency-Check, Snyk, Trivy, or
  GitHub Dependabot database was queried, and no offline CVE database is available in this environment.
- **Therefore this report does not assert "no CVEs".** It asserts that no vulnerability was *identified* by the
  checks that were run.

**This report deliberately does not invent CVE identifiers.** Assigning a CVE to a library version without a
database lookup would be fabrication.

### Versions flagged as behind current stable by lint

| Dependency | In use | Latest per lint |
|---|---|---|
| `androidx.core:core-ktx` | 1.13.1 | 1.19.1 |
| `androidx.activity:activity-compose` | 1.9.3 | 1.13.0 |
| `androidx.lifecycle:*` | 2.8.7 | 2.11.0 |
| `androidx.work:work-runtime-ktx` | 2.9.1 | 2.12.0 |
| `androidx.test.ext:junit` | 1.2.1 | 1.3.0 |
| `androidx.test:runner` | 1.6.2 | 1.7.0 |

The stack is roughly two years behind current stable. **This is a maintenance/hygiene observation, not a
security finding** — no known-exploitable issue was demonstrated, and upgrading a Compose/AndroidX stack is a
non-trivial change that should not be done during a security investigation.

### Recommended follow-up (not applied)

1. Enable **GitHub Dependabot** (`.github/dependabot.yml`) for Gradle — free, continuous, and gives a real
   vulnerability feed instead of a manual review.
2. Add **Gradle dependency verification** (`gradle/verification-metadata.xml`) to pin artifact checksums and
   defend against repository tampering.
3. Schedule a controlled AndroidX/Compose upgrade outside this investigation.

---

## 6. Relevance to the Play Protect incident

**Dependencies are not a plausible trigger.** Specifically:

- No analytics, ads, attribution, or crash SDK — the categories most often flagged for data collection.
- No third-party network stack that could phone home unexpectedly; the app uses `HttpURLConnection` directly
  to a short, explicit endpoint list.
- The two non-AndroidX libraries (Vosk, JNA) are widely used, open-source, and explain the native libraries in
  the APK. They are not packers or droppers.
- No library here performs dynamic code loading or reflection on untrusted input.

**Confidence: high** that the dependency graph does not explain the block.

---

## 7. Reproducing this audit

```powershell
cd D:\pipo\Pipo
.\gradlew.bat :app:dependencies --configuration releaseRuntimeClasspath
```

To add real vulnerability data:

```powershell
# GitHub-native (recommended): add .github/dependabot.yml, then check the Security tab.
# Or, locally, with a tool you trust installed:
#   trivy fs --scanners vuln .
#   (not run in this investigation; no such tool was available/authorised)
```
