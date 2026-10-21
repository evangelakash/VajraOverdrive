# Coding Guidelines — Vajra Overdrive

The house rules. One page on how this codebase is built: what goes where, what anything is
called, which shared kit to reach for first, and the standing operating procedures. When a
new file's placement or a name is in question, this page answers it.

---

## 1. Project structure

```
Vajra_Overdrive_Reboot/
├── assets/                  every shipped resource (see §3 — naming is a contract)
│   ├── atala/               world art by loka: bullets/, enemies/<kind>/, fx/, walls/,
│   │                        energy_orb_1/, space/, plants/
│   ├── brand/               studio + game identity art (studio-logo, vajra-overdrive-logo)
│   ├── checkpoint/          checkpoint holder + orbs
│   ├── coins/, shards/, health_drop/   pickup faces
│   ├── effects/             smoke-low-hull/, small-flame/
│   ├── fonts/               typekit in use + fonts/_source/ (license + originals)
│   ├── popup_art/           pre-rendered Devanagari popup glyphs
│   ├── ship_mask/, vajra_ships/        ship silhouettes and painted art
│   ├── vajrayudha/          the seven-band blade
│   └── weapon_display_images/          weapon face cards
├── core/src/main/java/vajra/           the game
│   ├── root: VajraGame, SaveGame, Settings
│   ├── profile/ the pilot's book (Profile, Profiles — roster, settings, career ledgers)
│   ├── flow/    state machine (Flow, FlowState)
│   ├── net/     the wire (Session, Msg, Transport — the one socket a session owns)
│   ├── voice/   push-to-talk (VoiceChannel, VoiceCodec, Mic, Speaker)
│   ├── screen/  rendering + UI (see §4 for what lives where)
│   ├── world/   simulation (World, WorldGen, LokaTable, TileMap, Effect, Score…)
│   ├── enemy/   enemy bodies + brains
│   ├── ship/    the player's hull + hangar data (Ship, ShipDef, Hangar, Upgrade)
│   └── weapon/  weapon data (WeaponDef)
├── core/src/test/           headless logic tests (pure math, no GL)
├── lwjgl3/src/              desktop launcher + the screenshot/transition tests
├── docs/                    this guide + design-notes/ (the PDF mood boards)
└── tools/                   generators (gen_ships.py, gen_fx.py, gen_popup_art.ps1…)
```

This layout stands. It is layered the right way round — `screen/` only reads `world/`,
`world/` never imports `screen/`, and every asset is reached through one kit class in
`screen/`. New packages are not needed; growing happens inside the existing ones.

## 2. Naming — code

| Thing | Rule | Example |
|---|---|---|
| Package | one lowercase word, loka-terms allowed | `vajra.world` |
| Class | `UpperCamel`, role suffixes from a fixed set | `Table` for lookup tables (`LokaTable`), `Director` for schedulers (`EnemyDirector`, `CacheDirector`), `Brain` for enemy steering (`Brain`, `BossBrain`), `*Images` for one asset-family kit, `*Screen` for a FlowState screen |
| Method | `lowerCamel`, a sentence in verb form | `blockedPx`, `orderRetinue`, `runEnded` |
| Constant | `UPPER_SNAKE`, published only when a test or another package needs it | `VICTORY_LAP`, `WALLET_X1` |
| Boolean | reads as a predicate | `runEnded`, `blinking`, `shipUnlocked` |
| Geometry | suffix carries the unit/axis: `…_X/_Y/_W/_H`, `…_PX` for pixel-space | `blockedPx`, `solidPx` |
| Screen ids / keys | the game's own English names pass through a single converter at the kit | `"Single Bolt"` → `single-bolt.png` |

Dos and don'ts:

- **No abbreviation soup.** `WHISKER` beats `D2`. An existing short constant (`CX`, `GOLD_R`)
  is fine — it is the codebase's vocabulary.
- **No duplicate names for one concept.** The run-over predicate is `World.runEnded()` — a
  second copy under another name (`isOver`, `runEnded` re-derived) is a bug in waiting.
- **Comments tell the why, in full sentences.** The codebase's comment voice is prose:
  what the player sees, what game-feel the constant serves. A comment that names a file
  must name the *current* file (see §3: wrong-path comments go stale).

## 3. Naming — assets

Assets are referenced by literal path in Java, so a filename is an interface. The rules:

1. **Lowercase kebab-case, ASCII only, no spaces, no doubled extensions.** libGDX's
   `FileHandle` backs internal files with a classpath lookup that 404s on spaces inside
   nested paths — that is how `sahah/SAHAH LOGO.png` once went invisible. Never again:
   a new asset gets a space-free path or it does not ship.
2. **Folder is the owner, lowercase, singular meaning**: `checkpoint/`, `effects/`,
   `brand/`, `popup_art/`, `weapon_display_images/` (kept: the art set's own historical
   name; do not start new folders with underscores outside `_source`).
3. **Per-crop names describe the picture, not the tool**: `low_hull.png`, `small-flame.png`,
   `studio-logo.png`. Tool artifacts (`pixilart-sprite.png`, `untitled-3.png`) are renamed
   on adopt — the filename outlives the tool.
4. **Conversions live with the art**: generated crops from the pack go under `atala/…`,
   not beside the original sheets. The original sheets
   (`SpaceShooterAssetPack_*.png`) stay at the root as the source of record.
5. **Font delivery**: the shipped TTF sits flat in `fonts/` under its file's own name
   (`courbe-sans.ttf`, `Rajdhani-SemiBold.ttf`); license + unmodified originals go to
   `fonts/_source/<family>/`.
6. **Reference a path through the kit's `artPath()`/`DIR` constant** when the name is
   derived, or a `public static final String` when it is fixed. Never spell the same path
   twice.

Canonical lookup for anything already in `assets/`: grep the loader kit classes
(`*Images.java`) — exactly one of them owns each path.

## 4. The shared kits — reach for these first

| Kit | Owns | Use it for |
|---|---|---|
| `screen/ImageKit` | THE texture loader: exists-check, filter, swallow | Every image class's load. Returns null when missing → the caller draws its own placeholder. **Never write another try/catch texture load.** |
| `screen/Ui` | fonts (`hud/subhead/menu/title/caption` via `UiTheme.FONT_*`), cameras, shape/batch pass handling, `measure()`, `centered*` | Anything text-metric or pass-related |
| `screen/UiTheme` | palette constants, `FOOT_Y`, `SAFE_MARGIN`, `SCREEN_H/W`, `FOOTER_Y` | Any color/geometry constant; stray local hexes are out of theme |
| `screen/Template` | `accentR/G/B` + `setAccent`, `title`, `menuRow`, `keys`, `COL`, tracking | The loka accent, headings, footer key hints, menu geometry |
| `screen/Ornament` | `titleMarkers`, `markers`, `divider`, `frame`, `FRAME_INSET` | Diamond furniture, the hairline frame |
| `world/World.runEnded()` | the one run-over predicate | GameScreen, Hud, saves — never re-derive `gameOver‖vajraDone‖won` by hand |
| `SaveGame.finish(h, points)` | record + store as one event | Only `Profiles.endRun` |
| `profile/Profiles.endRun(world)` | THE run exit: folds the run into the active pilot's book + calls SaveGame.finish | GameScreen's end block, PauseScreen quit — never call SaveGame.finish directly |
| `profile/Profiles.active()` | the live pilot (name, `settings`, campaign/multiplayer books) | SettingsScreen, the shake/popup readers — never a second global Settings |
| `net/Session` | THE session: roster, ready, seed, voice routing, results book, one `update()` pump | Lobby/Netplay screens only — never touch the transport or `Gdx.files` through a side door; run the protocol headless over `LoopbackTransport` |
| `net/GameWire` | THE live-co-op planes: `SHIP/SHOT/SWARM/PAIN/TAKE` send/parse + the per-frame `pump(session, world, dt)` | GameScreen only — never hand a plane bytes; the chokes (spawn/countKill/obtain) own the emits, `session.listen` owns the reads. Send and parse offsets are a pair: change both or pin both |
| `net/Bin` | little-endian put/get for every plane body; `getU8` reads unsigned | GameWire exclusively — no other module packs bytes |
| `net/Net` | the one live session per process: `active()`/`pump()`/`close()` | Every screen that hosts the session (lobby, run); sessions are created by NetScreen and outlive them |
| `voice/VoiceChannel` | push-to-talk encode/decode + talking levels | Lobby and the run screens hold V; screens read `talkingOf`, never the mic lines |
| `world/World(0)` | THE world constructor: seed 0 = the legacy constants, a session seed grows the race's orb/loot chaos | GameScreen's `loadWorld`, determinism tests — never reseed subsystem RNGs by hand |
| `world/World.WORLD_CX / PIT_X / PIT_Y` | the map's geometry spine | Brains, compass, spawn placement — literals like `2600` belong to WorldGen (data placement) or nothing |

Rule of three, enforced: the third time you write a loop, a try/catch, or a layout formula,
it moves into the owning kit (or creates one in `screen/`).

## 5. Standing operating procedures

**Build loop.** `gradlew.bat build --offline` is the gate, run before handing work back.
Headless tests live in `core/src/test` (pure logic); pixel-asserting screenshot tests in
`lwjgl3/src/test` regenerate `lwjgl3/build/*.png` — view them after every visual change.

**Hit regions move with the drawing.** Every drawn control (`TABS_*`, `WALLET_*`,
`PAGER_Y`, SELL) publishes its geometry as a constant and the click/probe handler reads
the *same constant*. A drag move of pixels without the constant is a regression.

**Never allocate in `render()`.** Scratch buffers, `GlyphLayout` reuse via `Ui.measure`
— per-frame `new` is allowed only for immutable data tables computed once.

**An asset is soft.** A missing file is a drawn placeholder (see ImageKit's null contract),
never a crash, never a stack trace at boot.

**Trust boundaries validate.** Save files are untrusted input: unknown keys are skipped,
not fatal. Doors between player input and world state clamp (`MathUtils.clamp`, not
hand-rolled ifs).

**One concept, one owner.** The accent lives in `Template`; HUD/compass deliberately stay
brand gold — a comment records the intent where the choice is made.

## 6. Adding things — the recipes

- **A weapon**: data in `weapon/WeaponDef` (name is the art key) → drop
  `weapon_display_images/<kebab-of-name>.png` → nothing else; font of patterns, cards and
  bullets derive themselves.
- **A loka**: `world/LokaTable` entry → its colors come from `TATVA_RGB`, the accent
  flows through `Template.setAccent` automatically → name art under `atala/enemies/<kind>/`.
- **A screen**: a `*Screen implements FlowState` in `screen/`, renders through
  `Template` + `Ornament` + `Ui`, headings via `Ornament.titleMarkers` (diamonds, no
  rules), footer via `Template.keys` at `UiTheme.FOOT_Y`, geometry as published constants.
- **An asset folder**: §3 rules + one owner kit class in `screen/` + an exists-test in
  `core/src/test` asserting the crops are real files.

## 7. Debt register (known, deliberately deferred)

Not done in the 2026-10 pass, each checked and ranked:

1. `ShopUi` forge/hull/prose labels use three phrasings for the same state machine
   (`hullBuyLine` vs `weaponState` vs `forgeLabel`) — merge into one `stateLine` family.
2. `WorldGen` still spells the `2600` spine as literals (~25 sites) — data-placement code;
   swap only if it is ever edited again.
3. Per-screen ornament near-twins (pause/settings/controls dim + panel sequences) could
   fold into one `Template.chrome()` — currently 3×6 lines, visually pinned by screenshot
   tests; fold the day a fourth screen copies it.
4. `UiMenu` key-repeat skeleton repeats `world.input` handling ideas — extraction waits
   for the first third user.
5. `ShipRenderer` plume tint overlaps `FxImages` whitening logic — cross-file coupling;
   document before touching.
6. Unused imports left behind in converted loader classes (`BulletImages`, `FxImages`,
   `NebulaImages`, `PopupArt`, …) — cosmetic; sweep on next edit of each.
7. README weapon tables trail the actual `WeaponDef` data — regenerate from the table.
