# Test Results — Phase 5 + Citizens/Bedrock fixes — 2026-11-22 JST

## Automated build

- Command: `gradlew clean test shadowJar --no-daemon`
- Result: **SUCCESS**
- Tests: **72 passed, 0 failed, 0 skipped**
- Artifact: `MonaBowling-0.5.0-alpha.12.jar`
- Artifact size: 14,566,766 bytes
- SHA-256: `e6d10d84a3da9c45e59e31690c3c159348567bc590c53c0f78937888eed6c816`

Phase 5 additions covered by automated tests:

- Citizens skin URLs accept only public HTTPS PNG links and reject HTTP/non-PNG values;
- 20 ordinary 100-pin trajectories remain below 95 pins while preserving a 40+ pin strong chain;
- the runtime safely accepts mode-specific physics parameters only when its fixed clock matches;
- four-player cyclic turn selection, wraparound and completed-player skipping;
- configurable one-to-four player limit validation;
- production-width ten-pin balance: multiple maximum-power strike pockets;
- ordinary straight throws leave pins standing instead of granting automatic strikes;
- Shift/sneak release defaults toward lane centre from either starting side;
- camera yaw overrides automatic hook direction;
- later Shift timing increases spin while respecting the configured cap;
- narrow wall-top secret route remains out of the gutter and does not bounce;
- missing or falling off the wall top enters the slab gutter permanently;
- ordinary lane-side approaches still reflect from the inner bumper;
- 100-pin ten-frame scoring, including a 3000-point perfect game;
- spare bonus calculation and standing-pin input validation;
- 100-pin production layout size, symmetry, row structure and deck-width validation;
- uninterrupted same-side guard traversal for the secret strike;
- rejection of late gutter entry, side changes and leaving the guard corridor;
- enlarged production-spacing 10/100-pin physical chains, finite state and eventual sleep;
- 0.26 × 0.78 visual pin dimensions aligned with the 0.13 physical radius;
- moderate standard throws do not automatically clear all ten pins;
- extreme pin velocity is contained by the virtual side/rear deck boundaries;
- standard-mode balls reflect from the side bumpers without entering the gutter;
- gutter rolls always score zero without aborting when a pin was disturbed first;
- displaced but still-upright pins remain standing in the score judgement;
- a ball retains forward motion after striking a head pin and does not repeatedly collide with a falling pin;
- player movement is clamped to the configured `/bowling admin setspawn` foul line;
- standard and 100-pin modes both select their configured side-bumper behaviour;
- all Phase 1–4 database, scoring, split, input, physics and runtime tests.

The deterministic stress rack measured **0.4723 ms per fixed step**
on this development machine, with 537,512 collision candidate checks, 20,964
resolved contacts and 1,383 adaptive substeps. This is a regression sample,
not a production TPS guarantee.

## Isolated Paper smoke test

- Paper: 26.1.2 build 74 stable
- Java: Eclipse Temurin 25.0.4.1
- Result: **SUCCESS**
- Loaded plugins: `Citizens 2.0.43 build 4230`, `MonaBowling 0.5.0-alpha.12`
- Log: `MonaBowling Phase 5 enabled with 0 lane(s)`
- Citizens hook: enabled; `/setnpc` registered and player-only placement guard verified
- Optional cross-play providers absent: clean Java fallback (`Floodgate=false,Geyser=false`)
- `/bowling admin debug`: 100-pin counter, DB, physics and rendering counters shown
- `/bowling help`: successful
- Clean disable and server shutdown: successful
- Startup/shutdown plugin exceptions: none

## Production Hub smoke test

- Deployment: **SUCCESS**, 2026-11-22 20:16 JST
- Loaded plugins: `Citizens 2.0.43 build 4230`, `MonaBowling 0.5.0-alpha.12`
- Citizens JAR: byte-identical to Main; SHA-256 `2691e47c0370e55985809f6fa4b0048bd6dd832f1bd029967a456d334cb11127`
- Citizens receptionist: NPC ID 0 loaded; persistent marker, skin texture and right-click menu handler stored
- Skin asset: public HTTPS PNG returned HTTP 200 and matched the attached file SHA-256
- Existing database: preserved, five registered lanes loaded
- Hub listener: `127.0.0.1:25566`, direct TCP connection successful
- Services after deployment: Bungee, Main and Hub all `active`
- Startup plugin exceptions: none
- `/bowmenu`: registered with lane/mode/player-count selection and public lobby browser
- Per-lane player limit: `4`, production config verified
- Java visual: BlockDisplay/ItemDisplay; Bedrock/mixed-lobby fallback: native Item Entity
- Lobby recruitment: repeating chat notification removed and replaced by a live BossBar
- 100-pin physics: mode-specific energy dissipation; test matrix result 6–58 pins, no automatic strike
- Revised pin sweep, slide friction and angular transfer make 8–10 pin chains possible
- Shift/sneak timing release with configurable 6–16 spin strength
- 100-pin inner bumper, narrow wall-top secret line, slab gutter and red switch visuals
- Standard-mode side bumpers: enabled; restitution `0.72`, tangential retention `0.96`
- Wii-style lateral-position/direction guidance and server-side foul-line clamp: deployed
- Standard and 100-pin side bumpers: both enabled and production config verified
- Backup: `/home/minecraft/backups/monabowling-alpha12-20261122-201557`
- FAWE schematic: installed and SHA-256 verified

## Not yet tested

- A complete real-player 100-pin game on the production Hub
- The guard-rail secret strike with a Java client and a Bedrock client
- Real Floodgate/Geyser rendering, touch, gamepad and alternate attack input
- Final device confirmation of the new native Item Entity pin/ball fallback
- Final device confirmation of the lobby BossBar lifecycle
- Mixed Java/Bedrock concurrent play
- A complete 2–4 player turn-based game and mid-game disconnect recovery
- Production load with the configured one-game 100-pin limit
- Final client-side acceptance of the revised pin weight, gutter and wall handling

The manual matrix is in `docs/MANUAL_TEST_PLAN.md`. These client-only checks are
not reported as verified merely because the isolated Paper server started.
