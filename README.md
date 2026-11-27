# MonaBowling

MonaBowling is a server-authoritative bowling engine for the MONAKA SERVER Hub.
The target runtime is Paper 26.1.2 on Java 25 behind BungeeCord, with Java and
Bedrock players sharing the same lane state.

This repository currently contains the **Phase 1 foundation, Phase 2 physics,
Phase 3 ten-pin loop, Phase 4 Java/Bedrock compatibility layer, Phase 5
100-pin mode and Phase 6 wall-dodge mode**. It is an installable alpha.

## Phase status

| Phase | Status | Delivered |
|---|---|---|
| 1 — Foundation | Complete | Gradle project, lifecycle, validated configuration, command tree, SQLite migrations, lane CRUD, stats/ranking persistence |
| 2 — Physics | Complete | 120 Hz deterministic engine, adaptive continuous collision steps, spin curve, gutters, obstacles, pin chains, sleeping, snapshots, async runtime and profiling |
| 3 — 10 pin | Complete | World BlockDisplay rendering, two-use throw input, pinsetter, official scoring, splits and completed-game persistence |
| 4 — Cross-play | Complete in code; device QA pending | Floodgate/Geyser detection, input calibration, alternate attack input, guide particles and native-item fallback |
| 5 — 100 pin | Complete in code; device/game-balance QA pending | 100-pin rack, 3000-point scoring, broadphase/render budgets, cascade effects and guard-rail secret strike |
| 6 — Wall dodge | Complete in code; device/game-balance QA pending | Ten escalating obstacle stages, cross-play wall blocks, continuous collision and standard scoring |
| 7 — Polish | In progress | 1–4 player turn rotation and GUI lobby delivered; recovery, spectators, replay, effects and load tests remain |

## Runtime design

- SQLite runs in WAL mode with foreign keys, a busy timeout and a versioned
  schema.
- Database work caused by player commands is serialized on one dedicated I/O
  thread. Bukkit/Paper calls remain on the main thread.
- Lane definitions are immutable snapshots held in a concurrent registry.
- The physics runtime is lazy. It creates worker threads only while at least one
  simulation is active, and stops them when the final simulation is removed.
- Physics workers only touch pure Java state. Immutable snapshots with
  previous/current positions are consumed by the main-thread renderer for
  interpolation.
- Pin-pair collision candidates use a uniform spatial grid instead of checking
  every pair in a 100-pin rack.
- WorldGuard, PlaceholderAPI, Floodgate and Geyser are soft dependencies. Their absence
  does not prevent startup.
- A session owns one lane and up to four players. Each participant has an
  independent scorecard and standing-pin state, while one active turn owns the
  physical rack. Visuals are non-persistent and tagged `monabowling_visual`.
- The first right-click starts the power meter; the second releases. Horizontal
  view movement during charging becomes spin. A configured timeout releases the
  ball automatically.
- Pressing Shift/sneak while charging releases immediately with a timed hook.
  Camera movement selects the curve direction; without it, the hook curves from
  the player's lateral starting position toward the lane centre.
- Quit, kick, death, world change, plugin stop and simulation failure remove the
  session, displays, tagged ball and physics state.
- Floodgate/Geyser public APIs select configurable touch, controller,
  keyboard/mouse or unknown-input yaw calibration. `/bowling client` reports
  the detected edition, device input, provider and effective multiplier.
- Java sessions use a round blue ItemDisplay ball and BlockDisplay pins.
  Bedrock or mixed-edition multiplayer sessions can use non-persistent
  native item-entity visuals because Bedrock has no native Java Display Entity.
- Duplicate input packets are debounced, yaw wraparound is accumulated safely,
  and Bedrock may use either use or attack for the two-stage throw input.
- The configuration constrains physics workers to at most four and defaults to
  one, matching the Hub's 1 GiB heap budget.
- 100-pin mode is restricted independently (one concurrent game by default),
  caps display updates per tick, reuses visual entities between rolls and
  aggregates collision audio instead of producing one sound per contact.
- 100-pin scoring uses the same ten-frame strike/spare rules with 100 pins per
  rack, so a perfect game scores 3000.
- The secret strike requires one uninterrupted run over the same narrow wall-top
  line, beginning near the foul line and reaching its visible rear red switch.
  The lane-facing wall remains a bumper; missing or leaving the wall top drops
  the ball into the slab gutter and invalidates the attempt.
- Wall dodge uses the normal ten-pin scorecard. Every match receives a new course
  seed, producing different lateral positions and depths while the ten frames become
  progressively denser. Walls participate in the 120 Hz continuous collision simulation.
- Wall visuals are temporary real server blocks rather than Java-only Display
  Entity geometry. Java and Bedrock see the same course; only air is replaced,
  and original block data is restored on stage change, abort or shutdown.

## Build

Requirements: JDK 25 and Gradle 8.10 or the included wrapper.

```text
./gradlew clean test shadowJar
```

The built plugin is `build/libs/MonaBowling-0.6.0-alpha.18.jar`.

Citizens 2.0.43 build 4230 is an optional runtime integration. When it is present,
an administrator can run `/setnpc` at the desired location to create or move the
bowling receptionist. The NPC uses the configured PNG skin and opens `/bowmenu`
when right-clicked.

## Commands

Player commands:

- `/bowmenu` — lane, mode, 1–4 players, public lobby, stats and ranking GUI
- `/bowling`
- `/bowling join [standard|hundred-pin|wall-dodge] [lane-id]`
- `/bowling leave`
- `/bowling client`
- `/bowling stats [standard|hundred-pin|wall-dodge]`
- `/bowling top [standard|hundred-pin|wall-dodge]`
- `/bowling help`

Administration:

- `/bowling admin create <ID>`
- `/bowling admin delete <ID>`
- `/bowling admin setlane <ID>`
- `/bowling admin setspawn <ID>`
- `/bowling admin setpins <ID>`
- `/bowling admin reload`
- `/bowling admin debug`

`/bowling join [lane-id]` remains a backward-compatible standard-game command.
`/bowling join hundred-pin [lane-id]` starts a 100-pin game.
`/bowling join wall-dodge [lane-id]` starts wall dodge. Legacy `spin-control`
lane/config data is read as `wall-dodge` during migration.
`/bowling leave` safely ends the session and returns the player.
For multiplayer, create a 2–4 player public lobby in `/bowmenu`; other players
join it from the lobby list. The lobby starts when full, or the owner can start early.

## Permissions

- `monabowling.play` — normal commands, default true
- `monabowling.spectate` — future spectator access, default true
- `monabowling.admin` — lane setup and diagnostics, default op

## Data

`bowling.db` stores:

- versioned schema state;
- immutable lane coordinates and enabled modes;
- player identity snapshots;
- completed games and frame totals;
- per-mode best scores and cumulative statistics.

No database connection or Bukkit object is accessed by the physics worker.
Immutable physics snapshots are consumed by the main-thread renderer. See
`docs/CROSSPLAY.md` for Bungee/Floodgate backend configuration and
`docs/LANE_BUILD_GUIDE.md` for exact lane construction dimensions.
Ready-to-place building files are documented in `schematics/README.md` and are
included as both Litematica `.litematic` and WorldEdit/FAWE `.schem` formats.

## External libraries and licenses

- Paper API — compile-only, PaperMC license terms.
- Geyser/Floodgate public APIs — optional runtime integration, not bundled, MIT.
- Xerial SQLite JDBC — bundled, Apache License 2.0.
- JUnit Jupiter — tests only, Eclipse Public License 2.0.
- Gradle Shadow plugin — build only, Apache License 2.0.

No Wii Sports Resort model, texture, sound or other copyrighted asset is
included. Future visual and audio assets must be original.

## Verification boundary

Automated tests cover schema migration, lane persistence, statistics, ranking,
model validation, ten-pin and 100-pin scoring, all ten wall-dodge layouts,
split topology, ball/pin dynamics,
production-spacing 100-pin collision chains, secret-strike corridor validation,
deterministic state and async runtime lifecycle. Production deployment and
real-player Java/Bedrock gameplay remain unverified. The Phase 5 rack is an
original geometric reconstruction based on publicly documented 91-pin and
100-pin layouts; it does not include or claim an extracted Nintendo asset.
See `TEST_RESULTS.md` and the manual checklist.
