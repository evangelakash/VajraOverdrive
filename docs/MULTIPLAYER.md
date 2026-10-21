# MULTIPLAYER — the plan, and what Phase 1 already does

One pilot flies solo today. This document is the law for how pilots fly together: the
topology, the wire, the invite flow, the voice channel, and the phase ladder that takes
the game from a working lobby to live co-op. Phase 1 is **implemented now**; every later
phase names the files it will touch and the trap it must defuse.

---

## 1. Principles (non-negotiable)

1. **The host is the hub.** Every packet — control or voice — flows client → host → out.
   No mesh, no peer discovery. One rule for data, one rule for voice, trivially debuggable.
2. **One socket, one thread of truth.** A single UDP datagram socket per session
   (`UdpTransport`); control packets are tagged JSON, voice packets are tagged binary.
   `Session.update()` polls the transport every frame — the session is a plain state
   machine, no extra threads beyond the transport's receiver.
3. **Core stays headless.** The whole protocol runs in `core/` against a
   `LoopbackTransport` (an ArrayDeque dressed as a wire) so JUnit can host two sessions
   and walk the handshake without GL, sockets, or a microphone.
4. **No new dependencies.** JDK sockets for the wire, Java Sound (`javax.sound.sampled`)
   for the voice, libGDX `Json` for the control plane. Phase 5 may bring Steamworks;
   nothing before that.
5. **A degraded machine degrades, never crashes.** No mic → listen-only. No speakers →
   talk-only. No network → the screen says so. Every failure path is a status line.
6. **Career books stay local.** Kills, points and settings are written to the pilot's own
   `Profile` on their own machine (`vajra/profile/`); the session only carries an id and a
   name. The `multiplayer` ledger exists in every profile and stays empty until a
   multiplayer run can end.

---

## 2. The wire

UDP datagrams on port **26000** (`UdpTransport.PORT`). Byte 0 of every packet is a tag:

| Tag | Plane  | Contents                                                        |
|-----|--------|-----------------------------------------------------------------|
| `0` | control| UTF-8 JSON — one envelope, `Msg`, with a type string            |
| `1` | voice  | `[senderId][160 bytes of μ-law]` — 20 ms of speech              |

### Control envelope (`vajra/net/Msg`)

| Type      | From → To     | Carries                        | Meaning |
|-----------|---------------|--------------------------------|---------|
| `HELLO`   | client → host | `name`                         | "here I am" — join request |
| `WELCOME` | host → client | `id`, `members`                | id assigned + the roster; promotes the client to LIVE |
| `ROSTER`  | host → all    | `members`                      | the truth about the roster; doubles as the host's heartbeat |
| `READY`   | client → host | `flag`                         | my ready flag flipped |
| `START`   | host → all    | `num` (seed), sent ×3          | the run seed is locked; everyone hands off |
| `PING`    | client → host | `id`                           | keep-alive; also keeps NAT port pins alive |
| `BYE`     | client → host | —                              | leaving cleanly |

`Msg` sets `setIgnoreUnknownFields(true)` — an older client is understood, not rejected.

### Reliability

UDP is best-effort and the protocol leans on repetition, not ACKs: JOINING clients
re-send `HELLO` every heartbeat; the host re-broadcasts `ROSTER` every heartbeat;
`START` goes out three times. Losing one datagram costs 2 s, never a state machine.

---

## 3. Session lifecycle

States (`Session.State`):

```
            host()                      join(addr)
              │                              │
              ▼                              ▼
            OPEN ──── HELLO/WELCOME ────► JOINING
              │                              │
              └────────── ROSTER ────────────┤
              │                              ▼
              └────────── all LIVE ◄─────────┘
                             │
                   READY flips (all)
                             │
                       start() ×3
                             ▼
                          HANDOFF  ── (Phase 2/3 plug the run in here)
```

- **Host**: assigns ids 1..8, answers `HELLO` with `WELCOME`, rebroadcasts `ROSTER` on
  every change and every 2 s, sweeps any pilot silent for 6 s, relays voice.
- **Client**: retries `HELLO` until `WELCOME`, mirrors the roster (host is truth), pings
  every 2 s, and declares `LOST` after 6 s of host silence.
- **START** requires host + LIVE + everyone ready. A ready solo host starts alone; with
  joiners, any unready pilot holds the run. It locks a seed (`System.nanoTime`) and moves
  every member to `HANDOFF`.

---

## 4. The invite flow — how friends find each other

**There is one flow now, and it asks nothing of the friend.**

1. **The host presses HOST A RUN.** The game binds the game's UDP port, **shouts the room
   on the LAN** (a one-second beacon carrying the four-letter room code, the pilot's name
   and the port), and **opens the door on the router in the background**
   (`vajra/net/Nat`: UPnP SSDP + SOAP `AddPortMapping`, then NAT-PMP, then STUN for the
   public address). No port forwarding, no setup file, no second program, no admin rights.
2. **The lobby prints ONE link:** `ABCD@203.0.113.9:26000` — the public address when the
   door opened, this machine's LAN address when it did not.
3. **The friend presses FIND A RUN** and picks the room from the list — nothing is typed
   and no address is read off a screen. Or they type the host's link into that panel's one
   field: an invite joins the room, a bare address points the game at a server.
4. **One link serves both networks.** The link a host shares is its *public* address, which
   a pilot standing next to it may not be able to reach. So when the direct knock goes
   unanswered, the joiner **looks the room up by its four letters on the network it is on**
   and knocks at the LAN address the room shouted. Only then does it fall back to the
   relay at the address the link carried.
5. **If the door cannot be opened** (a router that refuses UPnP and NAT-PMP, or a machine
   with no public address at all), the host's lobby says so and offers **R**: the game
   starts the **bundled relay** on the host's own machine, opens its port, and the room
   moves onto it. The link becomes the relay's address. **No .bat file is ever asked of
   anybody** — the relay rides inside the game.

The old "read the IPv4 off the host's screen and type it in" step is gone, and with it the
port-forwarding step and the relay.txt edit.

### The netplay screen's three rows

| Row | What it does |
|---|---|
| **HOST A RUN** | The host's one button: bind, shout, open the door, print one link. |
| **FIND A RUN** | The join panel: the rooms the LAN hears, the linked server's rooms, and the one field that takes a typed link. |
| **BACK** | Leaves the screen. |

The typed field takes every shape by its own grammar — `ABCD@ip:port` (an invite), `ROOM:ABCD`
or four bare letters (a room code), a bare `ip:port` (points the game at a server) — so there is
no separate SERVER LINK row any more.

### The beacon's two hard-won rules

- **It rides the game's own port** (plus the side port beside it). A firewall rule that
  lets the game work lets the shout work; a rule that opens only the game's port would
  otherwise leave the room list permanently empty.
- **One socket per network interface, bound to that interface's address.** A broadcast sent
  from a wildcard socket leaves by whichever wire the routing table picks — a VPN adapter,
  a Hyper-V virtual switch, a Docker bridge — and dies there. Bound to the address, it
  leaves by the right one. This is the most common reason a LAN discovery "does not work"
  on a modern Windows machine.

---

## 5. Voice — `vajra/voice/`

Design point: **push-to-talk first.** Open mics are noise floors; a held key is a floor
of zero. (Voice-activation is a one-line change on `VoiceChannel.push` later.)

| Piece          | File            | What it does |
|----------------|-----------------|--------------|
| Codec          | `VoiceCodec`    | G.711 μ-law: 16-bit → 8-bit, static and pure, test-pinned. 8 kHz mono ⇒ 8 KB/s, a rounding error on any link |
| Capture        | `Mic`           | 8 kHz/16-bit/mono `TargetDataLine`, 20 ms frames, daemon thread paced by blocking reads; `null` when there is no mic |
| Playback       | `Speaker`       | 8 kHz `SourceDataLine` + jitter queue (10-frame cap, oldest dropped; silence written when dry so the line never starves); `null` when there is no output |
| Channel        | `VoiceChannel`  | owns the above; encodes frames when the talk key is held, stamps `[senderId]`, tracks per-member level + last-heard so the lobby can light a diamond per talking pilot |

Frame budget: 20 ms → 160 samples → 160 μ-law bytes → 162-byte datagram. Latency:
~80 ms jitter buffer + network. The session routes frames exactly like control traffic
(client → hub → broadcast), with one courtesy: you never hear yourself.

The same channel rides into the run untouched — Phase 3 only teaches the run screen to
poll the talk key (one line) and the HUD to show one diamond.

---

## 6. Members and profiles

A `Msg.Member` is `{id, name, ready}` — nothing else crosses the wire. The pilot's
identity, settings and ledgers stay in their local `Profile`; the session's name comes
from `Profiles.active().name`. When a multiplayer run can end (Phase 3), the ending run
folds into the **local** pilot's `multiplayer` book via `Profile.recordRun`'s
multiplayer sibling — score/kills are counted by the machine that earned them, so the
hub never becomes a score oracle.

---

## 7. The phase ladder

### Phase 1 — the lobby (IMPLEMENTED NOW)
`vajra/net/{Transport, LoopbackTransport, UdpTransport, Msg, Session}` ·
`vajra/voice/{VoiceCodec, Mic, Speaker, VoiceChannel}` ·
`vajra/screen/{NetScreen, LobbyScreen}` · home row MULTIPLAYER.
Host, join by code, roster, ready, host-start seed handshake, push-to-talk voice with
per-pilot talking diamonds, timeout sweeps, leave. Tests: `SessionTest` (full handshake
over loopback, fake clock), `VoiceCodecTest` (μ-law roundtrip), `UdpTransportTest`
(localhost socket smoke).

### Phase 2 — shadow runs (IMPLEMENTED NOW)
Everyone enters a **seeded** run from `HANDOFF` and flies it alone; the roster's book
keeps every pilot's best flight and posts it beside their name. No live sync — the race
is against identical chaos.
**The audit's honest result:** the feared dt-paced spawns do not exist — every spawn is
gated on quota (boss), position (regions, caches), or a static `WorldGen` garrison, so
the real work was (a) a **fixed timestep** (`GameScreen.STEP = 1/60`, backlog shed,
never a spiral) so sim time is machine-independent, and (b) seeding `World`'s two
gameplay RNG streams (`orbRng`, `lootRng`) from the session seed — terrain and dressing
stay fixed hand-tuned content. `World(0)` keeps the legacy constants, so every existing
test and solo flight is untouched.
Pieces: `Net` (the one session per process, outliving screens), `Session.State.RUN` +
`RESULT` rides the roster, `LobbyScreen` handoff → `GameScreen(batch, flow, ui, seed)`,
`PauseScreen` quit → back to the lobby, V-talk pumps during the run.
**Gate: `DeterminismTest` — two worlds, seed 4242, one `ShipControls` script, identical
score/kills/ship position/orbs after 60 sim-seconds; plus `SessionTest`'s ledger walk.**

### Phase 3 — live co-op (host-authoritative) (IMPLEMENTED NOW)
The host's `World` is the truth of the fight; each pilot's hull is the truth of its own
machine. Every seat sends its pose and hears everyone else's as ghost mirrors; the fight
itself — foes, their rounds, the boss, loot — runs on the host alone and broadcasts
snapshots. The guest's seat runs a reduced world: its hull, its ledger, its shop, and
ghosts of everything else; its rounds relay as `SHOT` events the host spawns truthfully,
and the host's verdicts on its hull relay home as `PAIN` (touch/shove/kill).

**The wire (`vajra/net/GameWire`, five binary planes `[tag][sender][LE body]`, no ACKs):**
- `SHIP` (both ways, 30 Hz): hull def+hp frac, pose, velocity, the sender's sim clock
  (the reader integrates the pose over its wire age — the half-tile gate trick), iFrames.
  Ghost hulls rebuild themselves from the def byte: zero handshake.
- `SHOT` (both ways, per round): hostile flag, pose, velocity, damage, radius, tint —
  def-less, drawn by the existing colored-circle fallback. Owner rides the sender byte:
  the host credits kills to the pilot whose round landed.
- `SWARM` (host→all, 10 Hz): foe rows (id/pose/flavour with boss/elite/rank bits, cap 100)
  and loot rows (id/type/variant/pose, cap 24) — client worlds hold no local garrison,
  they glide ghosts and expire whatever the next snapshot dropped.
- `PAIN` (hub→owners, 2-pilot cap): a touch/shove/kill relayed to the machine that flies
  the hurt hull; its own `Ship.damage` answers. Books stay per-pilot.
- `TAKE` (guest→hub): sweep ids; idempotent, so a lost packet just re-asks.

**Plumbing:** spawn chokes (`spawnBullet`/`spawnEnemyBullet`/`add(Enemy)`/`countKill`/
`LootSystem.pop`/`Bullet.detonate`) stamp owners and net ids; the guest's screen drives
`World.updateSeat` (score/popups/hull/region/its own magnet+docking — no foes, no boss,
no contact loop on ghosts); screens attach their world to `Net.session.listen` on entry.

**Gate (met):** `CoOpTest` walks two loopback sessions with 60 ms per-hop latency, one
ShipControls script per seat, both worlds from one seed — mirrors stay within half a tile
(16 px) while boosting; plus a `SHOT` relay, a cross-seat touch, a partner-kill pay, a
guest sweep retiring the host's drop, and the host's run-over ending the guest's run.

Voice carries over unchanged; results still land in the lobby book via the Phase-2 flow.

### Phase 4 — the internet relay (IMPLEMENTED NOW)
A minimal relay (`vajra/relay`, one UDP port, ~250 lines, no dependencies): both pilots
dial OUT to a public box — the host `REGISTER`s a room under a four-letter code it
picked, friends `JOIN` the code, and from then on every packet (control JSON, voice,
all five game planes) rides framed `DATA` through it. No port forwarding on either side
works; even symmetric NAT joins, because both ends speak first. The relay stamps each
frame with the sender's endpoint id; the client turns that into a "virtual address"
(`relay-<ep>` at port ep) so the session above — HELLO, roster addressing, sweeps,
results book — runs **unchanged** on the relay wire. The host's lobby shows the code as
`ROOM:XXXX`; the join dialog takes `room:xxxx` (or a bare four-letter token). A silent
endpoint is swept after 30 s; a room ends with its last member.

**Deploy the relay (any box with Java 17):** every install carries its own copy now —
run `run-relay.bat` beside the game on your own machine (LAN play with no port forward),
or copy `relay/build/install/relay` to a public box (`gradlew :relay:installDist` builds
it), run `bin/relay` with the UDP port open in the firewall. Then point the game at it
via `relay.txt` beside the game (one line, `host:port`), the `-Dvajra.relay=…` flag, or
the `RelayTransport.RELAY` constant (fallback `127.0.0.1:27040`).
**The invite carries the relay now**: the host's lobby shows `ABCD@<host-ip>:<relayport>`;
typed into JOIN A RUN, the joiner dials THAT server — so the host's own auto-started
relay (HOST ONLINE boots it when the dial finds silence) is straight joinable, no
relay.txt on the friend's side. Room-only codes (`ROOM:ABCD`) still join whatever the
friend's default chain holds, and a bare `address:port` stays the LAN join path.
**Gate (met):** `RelayTest` — a real server on localhost, host+join, packets both ways,
the refuses (dead room, dead relay), and the invite-address dial. The
two-home-networks-behind-symmetric-NAT check is the owner's playtest, not a CI thing.

**Still debt:** text chat where voice isn't welcome, and cross-session career-book sync.

**The crew block + ping (Phase 3.5):** in flight the HUD lists every pilot under the
vitals line (left wall): name in seat colour, then RTT in the plain Arial face — green
at/below 90 ms, red above, `-` unmeasured and on your own row. The law (`Session`): the
ROSTER carries the host's clock stamp; a guest's `PING` stamps its own clock and echoes
that roster stamp home, so the HOST measures its RTT to the guest (same machine's clock
twice — never a cross-clock subtraction); the host's unchanged echo of the guest's own
stamp gives the guest its read; both ride the roster's `ping` field (EWMA-smoothed,
0 = unknown) to every HUD. `SessionPingTest` walks both directions.

### Phase 5 — Steam
steamworks4j: real lobbies, friend invites (the code string disappears), Steam voice as
an alternate `VoiceChannel` backend, achievements. The session API does not change.

### Phase 6 — the no-hassle connection (BUILT NOW)
`vajra/net/LanBeacon` (the LAN shout and the ear) · `vajra/net/Nat` (UPnP, NAT-PMP, STUN) ·
`vajra/net/Net` (the room code, the beacon, the ear, the door) · `NetScreen` (one HOST
button, one JOIN panel).

**What it removes:** the typed IPv4, the port forwarding, the `relay.txt` edit, the
`run-relay.bat` step and the "install a relay box" story. The friend picks a room from a
list or pastes one code.

**Gates (met):** `NetPlayTest` — the beacon's bytes, the segment broadcast, the STUN
address (both flavours), the UPnP control URL, the invite's shape. `LanJoinTest` — over
REAL sockets: a host shouts, an ear hears, the friend joins the room it names, and the
roster opens. The real broadcast is proven on the machine that runs it.

---

## 8. Testing SOP for net code

For a human-shaped pass over real networks, follow `docs/PLAYTEST.md` — it walks the
LAN play and the internet relay play step by step, with the acceptance sheet at the end.

- Protocol logic is tested over `LoopbackTransport` pairs — deterministic, no sleeps,
  fake clock via the package-private `Session.update(long nowMs)`.
- Codec behavior is pinned by roundtrip tests (silence exact, tone within μ-law's
  quantization error).
- Exactly two tests touch real sockets (`UdpTransportTest`, `RelayTest` — localhost, each
  bounded at ~2 s) — real wire smoke without flakiness risk.
- `Mic`/`Speaker` are never unit-tested (no audio device in CI); their contracts are
  "open or null" and the codec carries the correctness. Play-test two machines for the
  real feel.
- Screens that open transports must catch the bind failure and show a status line —
  `build --offline` green is the house law, and a port-busy pilot is a pilot.

---

## 9. Net-code debt register (deliberate)

| Debt | Why deferred | When to pay |
|------|--------------|-------------|
| No ACKs (repetition instead) | loopback + heartbeat make loss a 2 s cost, not a bug | Phase 4 relay, where loss is real |
| No encryption / no auth | LAN trust model; a sniffer hears voices and scores, nothing else | Phase 4 — relay authenticates, then the wire does |
| μ-law instead of Opus | zero dependencies, 8 KB/s, fine for LAN chat | Phase 5 if quality complaints survive the fun |
| Host migration (host quits → session dies) | the room dies with the hub; the guests are told and can leave | Phase 4 |
| `HELLO` from strangers joins silently | harmless at LAN scale, roster cap 4 bounds flood | Phase 4 auth |
| Backlog shed, not banked (fixed-step cap 2) | below ~30 fps the sim slows instead of spiralling; the co-op clock-stamp makes sim-time drift a cosmetic cost, not a desync | — |
| No in-run HUD plate (roster/results live in the lobby) | the run itself is unchanged presentation; results read best at the lobby table | Phase 4 buddy-plate work |
| Abandoned flights report on pause-quit only | a pilot who closes the window mid-flight never reports; the book keeps best-of-reported | Phase 4 relay book-keeping |
| ~~PAIN rides the 2-pilot broadcast~~ | **paid**: four-pilot rooms are live — the hub now carries every seat's planes to every other seat, so pilots 2, 3 and 4 see each other | — |
| Kill pay is flat (TIER + foe hp, no combo mult) | each pilot's book stays its own honest ledger; a shared combo meter is presentation work | co-op scoring pass |
| Caches/orbs stay host-only places | the guest's door stays closed to sub-rooms the host sim; the wire for cache interiors is a slice of its own | co-op caches pass |
| Ghost hp bars are owner-reported, full at spawn between packets | a hull that dies on its own machine reports hp 0 within a SHIP frame at 30 Hz | cosmetic |
| Boss roar trauma stays host-side cosmetic | the guest's screen shake misses one roar; `World.nearestHull` targeting makes the fight itself seat-agnostic | cosmetic |
| Popups only fire per-seat | a partner's region crossing pays their own book; mirrors don't chap their seats about it | cosmetic |
| SHIP age assumes near-synced sim clocks | both seats run the same fixed step in real time; heavy load skew shows as a small mirror lag, clamped at 0.25 s | Phase 4 latency lessons |
| A door the router refuses | UPnP and NAT-PMP are both refused by some routers (and by every router behind a second NAT). The bundled relay is the fallback, but a relay on a machine with no public address cannot be reached — such a host can only play on its own LAN | when a public rendezvous is affordable |
| The snapshot caps the fight at 64 foes and 12 drops | the nearest to any seat, so the fight in front of you is whole; the far side of the map is not | when the world's roster grows past one screenful |


---

## Co-op playtest fixes (joiner lag, smoke, ping, leavers, headings)

- **Joiner lag, trails, smoke that never left.** The guest's world (`World.updateSeat`) never
  aged or retired its visual effects, so every exhaust puff, smoke wisp, ring and spark it added
  stayed on screen and the list grew every frame. It now ages and retires them like the host.
  (`CoOpStreamTest.aGuestRetiresItsEffects`)
- **Smoother foes on the guest.** The host's swarm snapshot flies at 20 Hz (was 10), and a ghost
  foe coasts on its measured velocity between snapshots instead of waiting.
- **Other pilots' headings.** The SHIP plane carries the hull's facing (27-byte body); the mirror
  eases to it and a chevron in the pilot's seat colour is drawn ahead of every other hull.
- **Ping.** The host used to time a guest from a roster stamp the guest echoed on its own
  schedule, so the host's read of a guest could be up to 2 s of nonsense. A guest now measures its
  own round trip (a PING each second) and reports it; the host puts every pilot's ping on the
  roster. The crew panel shows `NN MS` for everyone (green to 80, amber to 160, red above), your
  own row shows your ping to the host, and the host's row reads `HOST`.
- **Leavers.** `Session.leftCount()/lastLeft()` record a departure (BYE, timeout, or a pilot
  missing from the roster); the HUD announces "NAME LEFT THE RUN" for four seconds. The partner's
  hull is removed when the seat is gone.

Not verified: play over a real Wi-Fi link between two machines.
