# Sound

The game had no audio at all. It now has 50 sound effects covering the guns, the ship, the fight,
pickups and drops, the world's events and every menu. Nothing is downloaded or stored as a
binary: each sound is synthesised from arithmetic at start-up (`vajra.audio.Synth`, 263 ms for all
of them). Music is not included; this is effects only.

## What makes a sound, and when

| Group | Sounds | Fires on |
|---|---|---|
| Guns | `SHOT_MAGNUM` `SHOT_RIFLE` `SHOT_MG` `SHOT_SHELL` `SHOT_SCATTER` `SHOT_SNIPER` `SHOT_MISSILE` `SHOT_BOMB` `SHOT_LASER` `SHOT_CHARGED` | the player's weapon firing; each firearm is its real-world counterpart (below), chosen from the weapon's own data (`Sfx.forWeapon`) |
| Foes | `ENEMY_SHOT` `HIT_ENEMY` `IMPACT` `EXPLODE_S/M/L` | a foe firing (a small pistol; quieter with distance, panned by side), a round landing on a foe, your round striking rock and whining away, a foe falling (by size) |
| Boss | `BOSS_AWAKEN` `BOSS_PHASE` `BOSS_DEATH` `VICTORY` | the seal lifting, each phase of the duel, Bala falling |
| Ship | `HULL_HIT` `SHIELD_HIT` `SHIELD_ON/OFF/DOWN` `DASH` `BOOST` `LOW_HULL` `SHIP_DESTROYED` `RESPAWN` `GAME_OVER` `THRUST` | hits (hull and shield sound different), the shield rising, released and running dry, the dash and boost on their edge, a failing hull (faster beeps the lower it gets), a life lost, the run lost, and the engine loop |
| Pickups and drops | `COIN` `SHARD` `HEALTH` `PIECE` `DROP` `DROP_RARE` | collecting each kind, and loot leaving a body (a coin's tok, a shard's or band's sparkle) |
| The world | `CHECKPOINT` `CACHE_WAKE` `CACHE_OPEN` `LIFE_UP` `REGION` `FORGE` `ASTRA_WIND` `ASTRA_RELEASE` `DOCK` | an anchor taken, a cache fighting back and paying, a life returning, a gate crossed, the lance forging or charging, the Astra gathering and releasing, the bay locking |
| Menus and the bazaar | `UI_MOVE` `UI_SELECT` `UI_BACK` `UI_TAB` `UI_DENY` `UI_CONFIRM` `PURCHASE` `SELL` `SHOP_OPEN` `SHOP_CLOSE` | stepping a menu, choosing, leaving, turning a shop section, a refused action, the purchase question, coins changing hands, selling shards, the hangar opening and closing |

## The guns

The first gun sounds were a swept square wave, a laser's "pew", and sounded like a toy. A real shot
is not a tone: it is a **supersonic crack** (a razor of broadband noise a few milliseconds long),
a **muzzle blast**, a **boom** felt in the chest (a burst of low noise, not a pitched kick-drum
sine), and a **room** that answers with echoes off the walls, all pushed through a saturator.
`Synth.gun(...)` builds that anatomy, and each weapon is only a different set of numbers:

| Weapon in the game | Real-world counterpart | How it sounds |
|---|---|---|
| Single Bolt, Double Bolt, Aim Shot | **Glock 17, 9 mm** | a flat, snapping pop, a little body, almost no tail |
| Burst, Twin Burst | **AR-15, 5.56** | a violent crack, a hard chest slap, an outdoor roll-off |
| Light MG | submachine gun | a snap and a pop, nearly dry, so it can rattle at 14 a second |
| Buckshot | 12-gauge shotgun | wide ragged blast, then the pump racked (two clacks) |
| Heavy Shell | .50 / cannon | the deepest boom and a long rolling tail |
| Piercer, Wallbore | high-velocity rifle | whip-crack with a distinct slap-back echo |
| Missile, Tracking Missiles | rocket | igniter thump, then a roaring, hissing climb |
| Bomb | grenade launcher | the hollow thunk of the tube |
| Laser, Charge Shot | (energy weapons: no firearm to copy) | left synthetic |
| foes' guns | small pistol | a pop and a short echo, quieter |

Sounds are 44.1 kHz so the crack keeps its top end. The room is a dense, random wash of faint reflections, not a few evenly spaced echoes: spaced
echoes ring like a struck steel plate. The weights follow the real order: cannon darkest and
longest, then AR-15, then Glock.

**Listen to them without the game:** `java -cp core/build/classes/java/main vajra.audio.SfxExport
<folder> SHOT_MAGNUM SHOT_RIFLE ...` writes ordinary WAV files (no names = all 50).

## Behaviour

- **One report per gap.** Every sound has a minimum gap, so a five-pellet volley is one shot and a
  burst of coins a patter. Shots and hits vary their pitch a few percent; menus and bells do not.
- **Coins climb.** Coins taken in a run rise a whole tone each, up to seven, then reset after a pause.
- **In the world, not on the UI.** Sounds that happen in the world are quieter with distance (silent
  past about a screen and a half) and panned left or right by where they are.
- **Hushed when it should be.** The engine loop stops in the hangar, under the pause menu and when
  the run ends.
- **Volume.** Settings has a SOUND row (0 to 100% in steps of 20, default 80%), saved per pilot. Each
  change plays a coin so the new level is heard. Old saves get the default.

## How it is built

`World` and the screens speak to `Audio` (a static door, like `Net`), which hands the call to a
`SfxBus`. In tests the bus is silence or a recorder, so the simulation stays headless and every
event is asserted (`SfxEventsTest`, `SfxUiTest`). In the game the bus is `SfxPlayer`, which writes
the synthesised WAVs to a temporary folder (removed on exit) and plays them through libGDX.
`Ambience` drives the two sounds that are not events: the engine and the low-hull warning.

`SynthTest` checks every sound numerically: audible, never clipped, finite, the right length,
deterministic, all different, faded at both ends, the engine loop seamless, the character right.
`GunSynthTest` holds the firearms to a gunshot's anatomy: the peak lands in the first few
milliseconds, the crack carries well over half the early energy, a heavier gun is darker, longer
and more bass-heavy, the room answers with a decaying tail and echoes, the shotgun is racked, each
weapon maps to its real counterpart, and **the old toy sound fails the same tests** (it has about
a tenth of the crack a real shot has).

## Replacing a sound with a recording

Drop `assets/sfx/<name>.ogg` (or `.wav`) with the lowercase name from `Sfx` (for example
`assets/sfx/coin.ogg`). It is used instead of the synthesised one, with no code change.

## Honest limits

I cannot listen. The synthesis is checked by measurement (waveforms, spectra, the tests above), not
by ear, and the real playback path was smoke-tested (all 50 load and play on OpenAL here) but not
auditioned. "Sounds like a real Magnum" is a judgement only a listener can make. Synthesised effects are
serviceable and consistent, not studio quality: expect to want to tune pitches and levels (each
sound's base gain is in `Sfx`, its recipe in `Synth`), and recorded files can replace any of them.
Partner players' shots in co-op are not yet voiced.
