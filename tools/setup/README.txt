VAJRA OVERDRIVE -- setup package
================================

What this is
  A runnable copy of the game plus a two-minute installer.

How to install
  Windows:  double-click  VajraOverdrive-setup.exe  (or install.bat)
  Mac:      sh make-mac-dmg.sh   -> VajraOverdrive.dmg: open it and drag the
            app onto Applications. Or simply:  sh install.sh
            from this folder (terminal install, no dmg).

  The exe on Windows is a full wizard (terms of play, install path,
  shortcuts, and an optional shared relay box). install.sh asks two
  questions (where to install, the shared relay box), checks for Java,
  and puts a Vajra Overdrive icon on the desktop (a .command on the
  Mac). The Mac dmg puts the app into Applications with the mk1 ship
  as its icon. Every copy carries the vajra.txt note on flying with a
  friend, and the relay that can serve a room when a router refuses.

The only requirement
  Java 17 or newer. If the installer says it is missing, grab the free
  "Temurin 17" runtime from https://adoptium.net and run the installer
  again. Copies built with a bundled runtime (tools/jre) need nothing
  at all.

Playing with a friend
  Same building/Wi-Fi:  one pilot MULTIPLAYER -> HOST A RUN. Their
  pilot opens MULTIPLAYER -> JOIN A RUN and the host's room is already
  in their list — no address is typed and no code is copied. (The
  lobby still prints a code for anyone who wants one.)
  From anywhere on the internet: the same button. The game asks your
  OWN router to open the door (UPnP) — no port forwarding, nothing to
  install — and prints one code, ABCD@your.public.address:26000, to
  send. If the router refuses every mapping the game starts its own
  relay and prints the relay's address instead; a shared relay box can
  also be named during the install (it is stored as relay.txt beside
  the game) — see docs/PLAYTEST.md.
