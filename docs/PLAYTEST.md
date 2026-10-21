# PLAYTEST — two pilots, step by step

How to actually play the game with a friend: once on the same local network, once
across the internet. Person **A** hosts, person **B** joins. Every failure is a status
line on screen — nothing here should crash; when it does, that's the report.

You need: two machines (Windows or Mac, mixed is fine) with **Java 17+**, and the built
game. One computer can stand in for both during a first smoke test (two windows) — the
protocols don't care, only the checkpoints do.

---

## 0. Build and carry the game (once)

From the project root:

```
Windows:  gradlew.bat :lwjgl3:installDist :relay:installDist
Mac:      ./gradlew :lwjgl3:installDist :relay:installDist
```

That produces two runnable folders:

| Folder | What it is |
|---|---|
| `lwjgl3/build/install/lwjgl3/` | the game — `bin/lwjgl3.bat` (Windows) or `bin/lwjgl3` (Mac/Linux) |
| `relay/build/install/relay/` | the internet relay — only needed for online play |

**One-download route for a friend's machine:** `gradlew installer` builds
`build/installer/VajraOverdrive-setup.exe` — a double-clickable **Next-Next wizard**
(a self-contained exe: the whole game rides inside it, and the friend needs no other
downloads). Steps: welcome → **terms of play** (Next stays locked until accepted) →
install folder (default `%LOCALAPPDATA%\Programs\Vajra Overdrive`, no admin rights) →
optional **relay address for online play** + shortcut choices (written as `relay.txt`
beside the game; see §2b) → a progress page → finish, with a launch-now checkbox. It
checks Java 17+ up front and — when it is missing — says where the free copy is
(adoptium.net). For scripts: `setup.exe --silent [folder [relay]]` runs the same
install with no UI. When you ship the exe, also ship the `...-setup.exe.sha256` line
that sits beside it: the friend verifies the copy they got with
`certutil -hashfile VajraOverdrive-setup.exe SHA256`. The exe is Authenticode-signed
with our self-signed identity — genuine signatures stay valid, any tampering flips
the tab to "invalid"; a fully trusted name needs a paid certificate. The zip route
still exists for Mac or offline carrying: `gradlew makeSetup` — unzip and run
`install.bat` (Windows) or `sh install.sh` (Mac).

**A real Mac installer too:** on any Mac (a repo copy or the unzipped setup
package), `gradlew macDmg` / `sh make-mac-dmg.sh` builds **`VajraOverdrive.dmg`**
with the tools macOS already has (sips, iconutil, hdiutil): the friend opens the
dmg, drags **Vajra Overdrive** onto Applications, launches — the app wears the mk1
ship, asks for Java 17+ with a proper dialog if missing. (First open refused?
Right-click → Open once — Gatekeeper's unsigned-app ask.)

Copy the `lwjgl3` folder to each play machine (zip it, AirDrop it, a USB key — whatever).
The window opens at 1920×1080; **F12** swaps to 1280×720, **F11** fullscreen.

**On a Mac, first run:** the scripts are unsigned, so Gatekeeper may refuse. Either
right-click → Open once, or clear the quarantine flag on the unzipped folder:

```
xattr -dr com.apple.quarantine lwjgl3
```

and run `bin/lwjgl3` from Terminal. (macOS will also ask once whether to allow
"Incoming network connections" for java — yes, that's the lobby.)

---

## 1. Local network playtest — the 10 minutes

Both machines on the same Wi-Fi / Ethernet. **A** hosts, **B** joins.

1. **A launches the game**, home screen → `MULTIPLAYER` → **HOST A RUN**.
2. **A allows the firewall prompt** (Windows asks for java the first time; Mac asks
   once as above). The lobby's message line shows the invite:
   `CODE ABCD@192.168.x.x:26000 · SHARE IT`. Under it, a second line reports what the
   door opener found (`UPnP PORT OPENED ON THE ROUTER`, `NO PUBLIC ADDRESS — …`, …).
3. **B launches the game** → `MULTIPLAYER` → **FIND A RUN**. **The rooms on the
   network are listed** — A's room appears with A's name and the four letters. B picks it
   with the arrow keys and **ENTER**. *Nothing is typed, no address is read off a screen.*
   (B can also use the link field in **FIND A RUN** and paste the code instead.)
4. **Both lobbies now show the same roster**, one diamond each, and a PING column that
   fills in within a couple of seconds.
   *(A failure here is a status line: `NO ROOM FOUND — CHECK THE CODE AND THE HOST` is
   the mistyped-code case.)*
5. **Voice check:** hold **V** (either pilot) — a lit diamond appears on the other
   lobby's roster while holding. Talk both ways. No mic = the pilot just doesn't light
   up; they still hear, and their lobby says `NO MICROPHONE — LISTEN ONLY`.
6. **Ready and start:** both press **READY UP**; the host presses **START**. The host
   locks a seed and both desks drop into the **same run**, spawned at the same point.
7. **The co-op itself — what "good" looks like:**
   - **B's hull is visible to A and vice versa**, and while holding SHIFT (boost) with
     no steering, each pilot's copy of the other stays roughly on the tile (half a
     tile of drift is the design gate; a few hundred px means the link is lossy).
   - **Each hull wears its own pilot's colour bar** — pink for the host, cyan for the
     guest. If both bars are the same colour, that is a bug.
   - **Each pilot's own kills land on its own score** — fire at a foe from B; the
     popup and point go to B's book, not A's.
   - **The Astra lands for both:** F on B's machine throws twenty cubes at the foes B
     aimed at, and A's world takes those hits. The kill pays B.
   - **Loot is shared truth:** B sweeps a coin; within ~0.1 s that coin is gone from
     A's world too. Nothing is collected twice.
   - **Foes are A's word:** the garrison, waves, the boss — only A's machine simulates
     them; B sees them as ghosts. B's hull still bleeds: a foe round touching B's
     mirror relays home as PAIN and B's own hull answers.
   - **Voice still works in the run** (hold V — same channel).
8. **Cut a pilot (dropout drill):** B closes the window mid-run. Within ~10 s A's
   lobby-to-run state says the pilot dropped; A's run continues. B can rejoin later —
   the lobby is the source of truth, not the run.
9. **Result round-trip:** end the run on purpose (die out or finish). Both desks land
   back in the lobby with each pilot's best flight posted beside their name.
10. **Restart hygiene:** ready up and run again — a **new seed** each start; no stale
    ghost hulls from the last race should survive the handoff.

Expected frictions, none fatal: A's Windows defender prompt, B on an "AP-isolation"
guest Wi-Fi (the router forbids machine-to-machine — the room never appears in B's
list; try the router's main band or a hotspot).

---

## 2. Internet playtest — "from some random place"

**Try the direct door first.** A hosts, B joins from anywhere:

1. **A**: `MULTIPLAYER` → **HOST A RUN**. The door opener works in the background (a few
   seconds). The lobby's second line says what it found:
   - `UPnP PORT OPENED ON THE ROUTER` or `NAT-PMP PORT OPENED ON THE ROUTER` — the door
     is open and the link carries A's **public** address: `CODE ABCD@203.0.113.9:26000`.
     **Send that link to B and B is in.** No forwarding, no relay, nothing installed.
   - `NO PUBLIC ADDRESS — A RELAY IS NEEDED` — A is behind a double NAT or the router
     refuses every mapping. A presses **R** in the lobby: the game starts the bundled
     relay on A's own machine, opens its port, and the link becomes the relay's address.
2. **B**: `MULTIPLAYER` → the link field in **FIND A RUN** → type the link (or pick the room from **ON THIS
   NETWORK** if B happens to be on A's LAN). B knocks directly on the public address; if
   nobody answers, B looks the room up on B's own network by its four letters and knocks
   there instead — so the same link works either way.
3. **If the door would not open**, the relay is the way. It is a single process with zero
   dependencies; it exists so neither home router needs a port forward — both pilots dial
   OUT to it. Either stand one up publicly (2a below), or let A's own machine serve it:
   A presses **R** in the lobby and the game starts the bundled relay by itself.

### 2a. Stand up the relay (one-time, ~10 minutes on a $5 box)

Any host with **Java 17** and an open UDP port. A cheap Ubuntu VPS is the usual shape:

```bash
scp -r relay/build/install/relay you@your.box:~/relay      # from the build machine
ssh you@your.box
sudo apt install -y openjdk-17-jre-headless                # if java isn't there yet
nohup ~/relay/bin/relay > ~/relay.log 2>&1 &               # serves UDP 27040
sudo ufw allow 27040/udp                                   # open the port (if ufw is on)
```

Windows box instead of a VPS: run `relay.bat`, and open the port once as administrator:

```
netsh advfirewall firewall add rule name="vajra relay" dir=in action=allow protocol=UDP localport=27040
```

(On a cloud provider, also open the port in the provider's firewall panel — the VM's
own firewall is the second of two doors.)

The relay prints `vajra relay on udp/27040` and logs rooms as they live.

### 2b. Aim both pilots at the relay

The game needs to know the relay's address, checked in this order at launch: the
`-Dvajra.relay=…` flag, then a `relay.txt` beside the install, then the built-in
default. Three ways:

- **The setup wrote it already:** if this copy was installed with
  `build/setup/VajraOverdrive-setup.zip`'s installer, it asked for the relay address
  on the way. Check the file exists:
- **One-line text file** (no rebuild, no flags):
  put the address — `your.box.domain:27040`, one line — into `relay.txt` beside the
  game folder (`C:\Users\…\Programs\Vajra Overdrive\relay.txt`).
- **One-line source edit** (travels with every future build):
  `core/src/main/java/vajra/net/RelayTransport.java` → change the
  `RELAY = "127.0.0.1:27040"` default to `"your.box.domain:27040"`, rebuild.
- **Or a launch flag, no rebuild** — the gradle runner scripts read `JAVA_OPTS`:

  ```bash
  Windows:  set JAVA_OPTS=-Dvajra.relay=your.box.domain:27040
            bin\lwjgl3.bat
  Mac:      export JAVA_OPTS=-Dvajra.relay=your.box.domain:27040
            bin/lwjgl3
  ```

Both A and B must point at the **same** relay box.

### 2c. The play itself

Proceed exactly as the LAN play (same menu, same lobby, same rules), with these swaps:

1. **A: HOST ONLINE** (not HOST ON LAN). The lobby reads
   `CODE ROOM:ABCD · SHARE IT`. The relay's log grows a line: `ABCD ep1 HOSTS`.
2. **A sends `ROOM:ABCD`** to B — on the beach, on a train, wherever.
3. **B: JOIN A RUN** → types `ROOM:ABCD` (a bare `ABCD` works too). Same lobby, same
   diamonds, same voice.
4. **Ready up, start** — everything from the LAN list at step 7 applies; the only
   changes are the extra hop: the fight's snapshots now bounce through the relay box,
   so expect a breath more latency. Each pilot's copy of the other may drift a little
   more than half a tile during hard boosts — that's the wire, not a bug; it snaps
   back once the flight evens out.
5. **Dropout over the internet** is the same drill: silence for half a minute drops a
   pilot from the roster (`PILOT DROPPED`); the room ends when the host's last member
   leaves. **B rejoins the same code while the room is alive**, or waits for a fresh
   room if it expired.
6. **Relay restart = room reset:** restarting `bin/relay` kills living rooms; the host
   simply hosts again and shares the new code. Nothing in the game needs a restart.

### 2d. When something doesn't join

| Status line | What it means | The usual fix |
|---|---|---|
| `RELAY DEAD AT host:port` | the game can't reach the relay box | wrong address, port closed, relay not running — check the log |
| `ROOM GONE — THE HOST LEFT` | the code is a live code no more | the host left; re-host and re-share |
| `ROOM FULL — 8 PILOTS MAX` | that room is at capacity | another room |
| `CODE ALREADY TAKEN — TRY ANOTHER` | the host's generated code exists live | the host tries again (a fresh code is drawn each attempt) |
| `JOIN FAILED` | LAN join: wrong code or the router blocks machine-to-machine | re-check the string; guest Wi-Fi "AP isolation" blocks LAN joins |
| `PORT 26000 BUSY — QUIT THE OTHER LOBBY` | this machine already hosts | close the other window |
| `PILOT DROPPED` | a pilot went silent ~10 s | Wi-Fi blip, sleep, or exit; they rejoin |

---

## 3. The playtest report (what to write down)

A one-liner per item is enough — this is the acceptance pass for Phase 3/4:

- [ ] Lobby join both ways (LAN room from the list, a typed code) — time to both names visible
- [ ] Voice both ways (fiber quality: crackle/dropout words lost)
- [ ] Start lands on both desks (seed lock; no one stuck at "KNOCKING")
- [ ] Boost-convergence: how far apart the mirrors are, hard boost, straight line
- [ ] Cross-kill ledger: whose popup, whose score grew
- [ ] The Astra on the guest's seat damages the host's foes, and the kill pays the guest
- [ ] Each hull wears its OWN seat colour (pink host, cyan guest) — never two pinks
- [ ] One coin swept on B disappears for A
- [ ] Guest takes a hit and keeps living; guest death (all lives) ends their seat cleanly
- [ ] Host death ends both runs (the run-over relay)
- [ ] The guest's crew block shows a real ping number for the host (not a row of dashes)
- [ ] Pull-the-plug: B's window closed; A's roster notices within ~10 s, and B's next run
      shows THE HOST IS GONE with ESC to leave
- [ ] Results posted on both lobbies after a finished run
- [ ] Over the internet only: does the direct door open (UPnP/NAT-PMP), and does voice
      survive a minute of chat (μ-law on a laser of 8 KB/s); do shots feel a beat late

Anything outside these shapes — a crash, a hull that never appears, a score that never
moves — is a finding: note **who** (host/joiner), **which network**, and the exact
status line. The docs' gates in `MULTIPLAYER.md` assumed those answers would mostly be
"default good"; this sheet is how the assumption meets reality.
