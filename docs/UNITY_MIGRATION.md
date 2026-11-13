# Pipo — Unity world migration & games audit

Status date: 2026-10-25. Statuses below are only claimed when verified; anything not yet run says so.

## Architecture (staged migration, the app stays the brain)

```
Blender (art/blender/build_pipo3d.py)
   └─ pipo.glb / lab.glb  ── one asset set, used by every renderer
         ├─ WebView stage (app/src/main/assets/stage, Three.js)   ← current 3D room on the phone
         └─ Unity (unity/PipoWorld, glTFast import)               ← the new world, beta
Android app (Kotlin): HomeViewModel = source of truth (state, memory, trips, AI, games)
   ├─ streams the same state frame (stageState()) to whichever renderer is active
   └─ receives only validated events back (UnityBridge.parse)
Fallbacks: Unity → WebView 3D stage → classic 2.5D painted room (Settings → Motion)
```

Why this shape:
- **Unity as a Library** inside the existing app (not a separate app, not a rewrite): the Compose
  chrome (chat, menu, games, Journal, Map, Settings) stays on top of the Unity view, and all of
  Pipo's behaviour/AI/persistence stays in Kotlin where it is tested (268 unit tests).
- The library is **optional at build time**: `settings.gradle.kts` includes `:unityLibrary` only
  when `unity/export/android/unityLibrary` exists, and the app talks to Unity by reflection, so the
  app builds and runs exactly as before without it.
- **One asset pipeline**: Unity imports the same `pipo.glb` the WebView stage uses (glTFast 6.20.0).
- **One bridge contract, versioned** (`v: 1`): app → Unity `{"v":1,"type":"state"|"goto",…}`;
  Unity → app `ready | loading | locationEnter | locationExit | navComplete | navRequest | interaction | error`.
  Unknown versions/types and malformed JSON are rejected on both sides (tests: `UnityBridgeTest`,
  `WorldPlayTests.BadMessagesAreReportedNotObeyed`).
- Unity never decides where Pipo is: the app's trip state (`away` + `place`) chooses the location.

## World inventory (generated from the app's catalog — `WorldExportTest` keeps Unity's copy in sync)

20 locations: Pipo's room + 19 catalog places (6 shops, 2 indoor, 9 outdoor incl. Forest Hills, 2 hidden).

| id | Name | Kind | Unity builder | NPC | Sells | Wildlife |
|---|---|---|---|---|---|---|
| home | Pipo's room | HOME | home | — | — | — (17 room objects from the app: bed, plant, charger, window, corkboard, desk, drawings, shelf, workbench, arcade, toys, clock, console, kitchen, door…) |
| garden | The garden | OUTDOOR | greenspace | — | — | sparrow, butterfly, bee, frog, firefly, cat |
| park | The park | OUTDOOR | greenspace | — | — | squirrel, sparrow, cat, butterfly |
| field | The football field | OUTDOOR | sportsground | kip | — | sparrow |
| cricket | The cricket ground | OUTDOOR | sportsground | raj | — | sparrow |
| sports_hall | Riverside Sports Hall | INDOOR | hall | lin | — | — |
| court | The badminton court | OUTDOOR | sportsground | bea | — | butterfly |
| hardware | Grumble's Hardware | SHOP | shop | grumble | screws, wire, sticks, magnet, springs | — |
| electronics | Juniper Electronics | SHOP | shop | juniper | motor, battery_pack, led, propeller | — |
| market | Pim's Market | SHOP | shop | pim | noodles, egg, tomato, rice, honey, milk, apple, banana, juice, cheese, chocolate, umbrella | — |
| bakery | Mo's Bakery | SHOP | shop | mo | bread, croissant, cookie, cake_slice | — |
| cafe | The Corner Café | SHOP | shop | ada | hot_chocolate, pancakes, pizza_slice | — |
| secondhand | Old Rook's Things | SHOP | shop | rook | magnifier, cardboard_tube, felt, rubber_duck | — |
| repair | Fennel Fix-It | SHOP | shop | fennel | — | — |
| library | The Library | INDOOR | library | tess | — | — |
| lake | The lake | OUTDOOR | lake | — | — | frog, duck, firefly, sparrow |
| hills | The hills | OUTDOOR | hills | — | — | sparrow, butterfly |
| forest | Forest Hills | OUTDOOR | forest | — | — | deer, rabbit, robin, woodpecker, owl, fox, sparrow, butterfly, firefly |
| observatory | The old dome | HIDDEN | observatory | — | — | — |
| otherside | ??? | HIDDEN | otherside | — | — | — |

## Migration matrix

| Location | Unity build | Interactions in Unity | Reached by | Fallback today |
|---|---|---|---|---|
| All 20 | **Written, not yet run** — builders exist for every row above (`LocationKit.ArchetypeFor` never returns null for a catalog place; asserted by `EveryCatalogPlaceIsInTheWorld`) | room objects (`object:*`), shopkeepers (`npc:*`), shop stock (`item:*`), animals (`critter:*`), Nib, Pipo | the app's trip state (`away/place`), or the preview host's world list in the Editor | WebView 3D stage (room + Forest Hills in 3D) and the 2.5D painted outside scenes for every place — unchanged |

Per-location identity in Unity: shops get the catalog's stock on the shelves and their own shopkeeper;
outdoor places get their catalog wildlife; specific props for park, garden, field, cricket, court,
lake, hills, Forest Hills, bakery, café, electronics, hardware, market, second-hand, repair, library,
sports hall, the observatory and "the other side". Every row moves to **Verified** only after
`WorldPlayTests.EveryLocationIsReachableAndBuilt` passes in Play Mode and its screenshot is reviewed.

## Character & suit

- Same Blender character (Mk I armor rig) in both renderers; screen face redesigned (friendly lit
  screen, crisp mint eyes, catchlight, blinking, gaze that follows app state).
- Suit: one source of truth in the app (`HomeViewModel.setSuit`, saved as `suit:worn`); renderers
  only display it. Fixed: Mk I rendering as cardboard after Mk 0, "take off the armor" putting it
  ON (and trying to fly), no explicit suit-off. Unity side: `PipoCharacter.Equip` is idempotent,
  Mk II+ thrusters only, armor stand shows the owned suit exactly when it isn't worn
  (`SuitOnIsMkIAndSuitOffIsNothing`, not yet run).

## Games inventory & improvement matrix

| Game | Original condition | Changes | Tests |
|---|---|---|---|
| Rock Paper Scissors | Complete: personality-based favourite throw, match logic guarded against double scoring | — | `RpsMatchTest` (existing) |
| Memory Match | Complete, but one fixed 12-card board | Three saved levels (6 → 8 → 10 pairs) unlocked by beating Pipo; his memory sharpens with the board; cards resize to fit | `MemoryLevelsTest` (2) |
| Reaction Race | Works; best time was session-only | Best time saved as a record, "New record!", shown in the HUD | build + routing tests |
| Tic-Tac-Toe | Complete: minimax with personality mistakes (beatable) | — | — |
| Flappy Pipo | Complete, persistent bests vs Pipo's own best | — | `ExperienceAndArcadeTest` (existing) |
| Pixel Shooter | Complete, persistent bests | — | existing |
| Cricket | Complete (bat an over, then bowl to Pipo) | — | existing |
| Table Tennis | Complete (first to 7) | — | existing |
| Games menu | Plain list | Each game shows your record vs Pipo / personal best ("new" if never played) | — |
| **Pipo Football** (new) | — | Real-time top-down 1-on-1: drag to run and dribble, tap to kick, Pipo chases/tackles/shoots with skill from his energy + confidence; first to 3 or 2:00; reactions; recorded in his journal/memory | `FootballSimTest` (6) |
| **Pipo's Pattern** (new, co-op) | — | Simon-style sequence on his antenna lights, growing and speeding up; Pipo can save you once per run (recall from curiosity/energy); shared best | `PatternRunTest` (4) |

World tie-in: when Pipo is out at the field, cricket ground or sports hall, the home screen's "Watch the match" chip now has **Join in**, opening Pipo Football / Cricket / Table Tennis.

Not yet done: on-device/emulator play-testing of the new game screens (no emulator installed;
phone deployment awaits approval); games are not yet placed as world activities in Unity locations.

## Verification log

| Check | How | Result |
|---|---|---|
| App unit tests | `./gradlew.bat testDebugUnitTest` | 268/268 pass |
| App debug build | `./gradlew.bat assembleDebug` | builds |
| World catalog ↔ Unity data | `WorldExportTest` | in sync |
| Unity editor install | Unity Hub CLI, editor to `D:\Unity\Editors`, downloads to `D:\Unity\Downloads` | in progress |
| Unity compile / Play Mode tests / screenshots | Unity batch mode | **not yet run** |
| Android library export | `PipoBuild.ExportAndroid` | **not yet run** |
| Emulator | — | none installed |
| Physical phone | — | not performed (awaiting approval; no ADB used) |
