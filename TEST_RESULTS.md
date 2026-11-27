# Test Results — Phase 6 wall dodge — 2026-11-22 JST

## Automated build

- Command: `gradlew clean test shadowJar --no-daemon`
- Result: **SUCCESS**
- Tests: **79 passed, 0 failed, 0 skipped**
- Artifact: `MonaBowling-0.6.0-alpha.18.jar`
- Artifact size: 14,576,348 bytes
- SHA-256: `8d0dced75442d684d6068a0a24d8fd17e76ccc62ca52325514e55df9fe9eaf87`

Phase 6 additions covered by automated tests:

- ten progressively denser wall-dodge stages remain inside the playable lane;
- every generated wall gate leaves more than one bowling-ball width of clearance;
- invalid stage numbers are rejected and legacy `spin-control` data migrates to `wall-dodge`;
- wall blocks are sampled from segment centres rather than their two edges;
- every obstacle on all five production lane coordinate systems leaves multiple
  visibly empty Minecraft block columns;
- every stage has a simulated collision-free outer throw that reaches the pin deck;
- walls are isolated video-derived pieces rather than alternating full-width gates;
- rolling, wall, bumper and pin sounds are delivered to participants at the foul line;

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
- Result: **SUCCESS** for `MonaBowling 0.6.0-alpha.13`
- Tests loaded: Paper 26.1.2 build 74 and Citizens build 4230
- Log: `MonaBowling Phase 6 enabled with 0 lane(s)`
- Config reload, command registration, clean disable and shutdown: successful

Previous Phase 5 production baseline:

- Result: **SUCCESS**
- Loaded plugins: `Citizens 2.0.43 build 4230`, `MonaBowling 0.5.0-alpha.12`
- Log: `MonaBowling Phase 5 enabled with 0 lane(s)`
- Citizens hook: enabled; `/setnpc` registered and player-only placement guard verified
- Optional cross-play providers absent: clean Java fallback (`Floodgate=false,Geyser=false`)
- `/bowling admin debug`: 100-pin counter, DB, physics and rendering counters shown
- `/bowling help`: successful
- Clean disable and server shutdown: successful
- Startup/shutdown plugin exceptions: none

## Production Hub Phase 6 deployment

Wall-visibility hotfix:

- `0.6.0-alpha.15` deployed successfully on 2026-11-23
- Full-width alternating gates were replaced with isolated video-derived wall pieces
- All ten stages have a simulated collision-free outer throw reaching the pin deck
- Rolling and collision audio is played locally to every participant at the foul line
- Backup: `/home/minecraft/backups/monabowling-alpha15-20261123-0008`
- `0.6.0-alpha.14` deployed successfully at 20:46 JST
- The edge-sampling bug that rounded both wall segments into the passage was removed
- All five production lane coordinate systems retain an empty visible block column
- Hub, Main and Bungee are active; startup exceptions: none
- Backup: `/home/minecraft/backups/monabowling-alpha14-20261122-2045`

- Deployment: **SUCCESS**, 2026-11-22 20:39 JST
- Loaded: `MonaBowling 0.6.0-alpha.13`, Citizens build 4230, five existing lanes
- Legacy production modes `standard,hundred-pin,spin-control` loaded without DB mutation;
  the former working name is exposed as `wall-dodge`
- Production config explicitly enables `wall-dodge` and defines block material,
  thickness, restitution and disabled side bumpers
- Hub, Main and Bungee services all remained/recovered `active`; ports 25565–25567 listen
- Startup plugin exceptions: none
- Production JAR SHA-256: `b70f89ef8b03d2fbd9217a5221cadc9a1a27a170087f3ca4dd990720ff782a09`
- Backup: `/home/minecraft/backups/monabowling-alpha13-20261122-203653`
- Real Java/Bedrock gameplay of all ten stages remains the final device check

## Previous Production Hub Phase 5 smoke test

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

## Alpha 16 wall visibility hotfix — 2026-11-23

- Artifact: `MonaBowling-0.6.0-alpha.16.jar`
- SHA-256: `bc76730cc38beaa95a45b11d20be6d1cf26c6494575280fc4dacea6243820e2d`
- Automated tests: 79 passed, 0 failed
- Wall blocks now resolve the actual supported air block around each saved lane Y coordinate.
- Cross-play renderer replacement now closes the old renderer before placing replacement walls.
- A zero-wall placement emits a diagnostic warning rather than failing silently.
- Hub deployment completed; five lanes loaded and Paper reached `Done` in 32.132 seconds.
- Hub, Main and Bungee services were all `active` after deployment.
- Backup: `/home/minecraft/backups/monabowling-alpha16-20261123-012041`
- Remaining acceptance check: confirm the wall visually in a real Java or Bedrock wall-dodge match.

## Alpha 17 visual-aligned wall collision — 2026-11-27

- Artifact: `MonaBowling-0.6.0-alpha.17.jar`
- SHA-256: `ff7cd7e14bc0521098870ff9f845064a0a9a0e0ee0d91b19b51519a024d4ff61`
- Automated tests: 81 passed, 0 failed
- Physics obstacles are generated from the real blocks successfully placed by the renderer.
- Every block hitbox is inset by 0.10m per side, producing a 0.80m footprint inside its 1m visual.
- Missing or skipped visual blocks no longer retain invisible planned collision boxes.
- Hub deployment completed; five lanes loaded and Paper reached `Done` in 29.952 seconds.
- Hub, Main and Bungee services were all `active` after deployment.
- Backup: `/home/minecraft/backups/monabowling-alpha17-20261127-165656`
- Remaining acceptance check: confirm contact timing with a real Java or Bedrock client.

## Alpha 18 random wall courses — 2026-11-27

- Artifact: `MonaBowling-0.6.0-alpha.18.jar`
- SHA-256: `f8559c8105e359c0664c8641ba1dbc5669efff0bee4cc94673df030acabba813`
- Automated tests: 82 passed, 0 failed
- Each match now derives its ten stable frame layouts from a fresh random course seed.
- 100 seeds across all ten frames retained the guaranteed outer route.
- 25 seeds across all five production lane transforms retained multiple open block columns.
- Obstacles and the menu icon now use `POLISHED_BLACKSTONE_WALL`.
- Wall-post collision is inset by 0.24m per side to match its narrow visual.
- Hub deployment completed; five lanes loaded and Paper reached `Done` in 31.088 seconds.
- Hub, Main and Bungee services were all `active` after deployment.
- Backup: `/home/minecraft/backups/monabowling-alpha18-20261127-171725`
- Remaining acceptance check: compare several newly created matches on Java and Bedrock clients.
