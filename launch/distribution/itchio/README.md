# itch.io upload package — Pipo

Everything needed to create the itch.io project page. **No account exists here**, so this has not been
uploaded; it is prepared and verified.

## Contents

| File | What it is |
|---|---|
| `Pipo.apk` | **The official v1.0 release APK, byte-identical.** Present on disk, git-ignored (27 MB binary; not committed). |
| `description.md` | Project page body, ready to paste. |
| `INSTALL.md` | Upload steps and the troubleshooting text to paste. |
| `tags.txt` | Tags and category. |

## Integrity

`Pipo.apk` in this folder must always hash to:

```
911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526
```

Verified against the live GitHub release asset digest (`sha256:911b61a5…3526`) on 2026-11-10.
To recreate it if it is missing:

```bash
cp -p dist/Pipo.apk launch/distribution/itchio/Pipo.apk
sha256sum launch/distribution/itchio/Pipo.apk   # must match the hash above
```

## Do not

- Do not put the `v1.1` build here. It is unpublished and unvalidated.
- Do not edit, re-zip or re-sign the APK. itch.io must receive the same bytes GitHub serves.
- Do not set a price. The project is free with no payments enabled.
- Do not claim Play Store availability.
