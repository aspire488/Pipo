# Signing Audit — Pipo

**Scope:** the APK publicly distributed as `Pipo.apk` on GitHub Release `v1.0`, plus the release/debug
signing configuration in the repository.
**Method:** read-only inspection of `app/build.gradle.kts`, `keystore.properties` (keys only, values never
printed), the keystore at `D:/pipo/keys/pipo-release.jks`, and `apksigner verify --print-certs`.
**Secrets:** no keystore password, key password, or private key material appears in this report.

---

## 1. Verdict

| Question | Answer | Confidence |
|---|---|---|
| Is the distributed APK debug-signed? | **No.** It is signed with a project-owned release key. | High |
| Is the signing identity project-owned and stable? | **Yes** — one key, alias `pipo`, valid to 2054. Only one release exists, so cross-release stability is unproven but the key is designed for it. | High |
| Is the keystore outside the repository? | **Yes** — repo root is `D:\pipo\Pipo`; keystore is `D:\pipo\keys\pipo-release.jks`. | High |
| Are signing credentials committed to git? | **No** — `keystore.properties` is git-ignored and no `.jks`/`.p12`/`.pem` is tracked. | High |
| Is there a release-configuration footgun? | **Yes** — silent fallback to the debug key. See §5. | High |

**The distributed APK is not debug-signed. This rules out "debug signing" as the cause of the Play Protect block.**

---

## 2. Evidence — distributed APK certificate

Command:

```
apksigner verify --print-certs --verbose "D:\pipo\Pipo\dist\Pipo.apk"
```

Output (summarised):

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=Pipo, O=Pipo
Signer #1 certificate SHA-256 digest: 6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7
Signer #1 certificate SHA-1 digest: b8651e6bc018bf06c72017948a6c7afe863c22da
Signer #1 certificate MD5 digest: 7150467304c3f8abfaa54cc0651dc7f9
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
Signer #1 public key SHA-256 digest: 2ad161c5969f84fc9e424caa39dca83fa4e5402444d1820b93a4c122af977ffb
```

`CN=Pipo, O=Pipo` is **not** the Android debug certificate (`C=US, O=Android, CN=Android Debug`, RSA-2048).

---

## 3. Evidence — keystore identity match

The keystore password was passed to `keytool` via an environment variable (`-storepass:env`) so it was
never echoed to the console, written to disk, or captured in this report.

Command shape:

```powershell
$props = <parse keystore.properties>
$env:PIPO_SP = $props['storePassword']
keytool -list -v -keystore $props['storeFile'] -storepass:env PIPO_SP -alias $props['keyAlias']
Remove-Item Env:PIPO_SP
```

Output (summarised):

```
Alias name: pipo
Creation date: 3 Oct 2026
Entry type: PrivateKeyEntry
Owner: CN=Pipo, O=Pipo
Issuer: CN=Pipo, O=Pipo
Valid from: Sat Oct 03 20:59:29 IST 2026 until: Wed Feb 18 20:59:29 IST 2054
SHA256: 6F:F4:C9:B3:60:27:40:8E:7F:4D:E4:7E:2E:98:B1:F7:F1:EC:66:A0:B8:4A:BF:52:B0:7F:33:01:AA:DA:FA:F7
Signature algorithm name: SHA384withRSA
Subject Public Key Algorithm: 4096-bit RSA key
```

The keystore certificate SHA-256 (`6F:F4:C9:…:FA:F7`) is **identical** to the APK signer certificate
SHA-256 (`6ff4c9b3…adafaf7`). The distributed APK was signed by this keystore, alias `pipo`.

---

## 4. Evidence — debug APK uses a different identity

```
apksigner verify --print-certs app\build\outputs\apk\debug\app-debug.apk
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
Signer #1 certificate SHA-256 digest: ee8a0f4aed94ca0768051d127d28dadb483661b9e79ca9df4298f241a7e9394b
```

This is the standard AOSP debug certificate. **The CI artifact and the distributed APK therefore have
different signing identities.**

---

## 5. Release-configuration finding (P2) — silent debug-key fallback

`app/build.gradle.kts`:

```kotlin
val releaseKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f -> ... }

signingConfigs {
    if (releaseKey != null) create("release") { ... }
}

buildTypes {
    release {
        ...
        signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
}
```

**Behaviour:** if `keystore.properties` is absent, `assembleRelease` still succeeds and silently produces
a **debug-signed** APK that looks like a release build (release manifest flags, release BuildConfig, ARM-only
native libraries).

**Did it happen for v1.0?** No — verified above; `dist/Pipo.apk` is release-key-signed.

**Why it matters:** it is a one-missing-file step away from shipping a debug-signed APK, which is a genuine
Play Protect / install-reputation hazard and also breaks upgrade continuity (the debug key is per-machine;
a different machine's debug key cannot install over it). This is the single highest-risk release-configuration
issue found, and it is latent rather than active.

**Minimal fix:** fail the build instead of falling back:

```kotlin
signingConfig = signingConfigs.findByName("release")
    ?: error("Release signing key missing: add keystore.properties next to settings.gradle.kts")
```

---

## 6. Signature scheme observations (P4)

- **v2 only.** v1 is disabled and v3 is not present.
- `minSdk = 26`, so v2-only is *correct and sufficient* — Android 7.0+ (API 24) verifies v2, and no
  device in the supported range needs v1. This is normal AGP behaviour and **not** a Play Protect trigger.
- No v3 means no **proof-of-rotation** lineage and no key-rotation capability. Enabling v3 costs nothing
  and future-proofs the app:
  ```kotlin
  signingConfigs { create("release") { enableV3Signing = true } }
  ```
  (or leave AGP defaults, which already enable v3 when `minSdk >= 28`).
- No v4 (irrelevant for sideloaded APK files).
- No SourceStamp (only used by Play App Signing / Play Store distribution).

---

## 7. Stability across releases

- Only **one** release exists (`v1.0`, published 2026-10-04T01:17Z). The signing certificate was created
  **2026-10-03 20:59 IST**, roughly 15 minutes before the APK was built.
- Therefore cross-release identity stability **cannot be measured** — there is no prior release to compare
  against. The configuration (key outside repo, documented backup instruction in `README.md`, 30-year
  validity, RSA-4096) is consistent with a key intended to be stable.
- **Risk to flag:** if the keystore or `keystore.properties` is lost, every future update breaks in-place
  upgrade. `README.md` already warns about this. A verified offline backup is the mitigation.

---

## 8. What this means for the incident

Signing is **not** the cause of the Play Protect block:

- The APK is release-signed with a stable project key.
- It is not debug-signed, not test-only, not debuggable.
- The signature verifies cleanly (v2).
- The only signing-adjacent weakness (silent debug fallback) did not trigger for this artifact.

What signing *does* contribute is **reputation**, not integrity: a certificate created the same day, on an
app with no install history, has no reputation for Play Protect to draw on. See `PLAY_PROTECT_ANALYSIS.md`.
