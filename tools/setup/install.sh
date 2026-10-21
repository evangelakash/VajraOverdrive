#!/bin/sh
# VAJRA OVERDRIVE - setup for macOS (run:  sh install.sh  from this folder)
echo ""
echo "  ============================================"
echo "   VAJRA OVERDRIVE  --  SETUP"
echo "  ============================================"
echo ""

# ---- the game runs on Java 17+; anything else is the only requirement ----
if ! command -v java >/dev/null 2>&1; then
    echo "  JAVA NOT FOUND.  Install \"Temurin 17\" from https://adoptium.net"
    echo "  then run this setup again."
    exit 1
fi
java -version 2>&1 | head -1 | sed 's/^/  Found /'

# ---- where to install ----
DEST="$HOME/Games/Vajra Overdrive"
printf "  Install into [%s]: " "$DEST"
read TGT
[ -n "$TGT" ] && DEST="$TGT"

printf "  A SHARED relay box, or ENTER to skip (the game opens its own door): "
read RLY

# ---- copy the game folder ----
echo "  Installing to $DEST ..."
rm -rf "$DEST"
cp -R "$(pwd)/app" "$DEST" || { echo "  COPY FAILED (try: sudo sh install.sh)"; exit 1; }
chmod +x "$DEST/bin/lwjgl3"

# ---- the relay rides along: a Mac host must be able to serve a room ----
if [ -d "$(pwd)/relay" ]; then
    mkdir -p "$DEST/relay"
    cp -R "$(pwd)/relay/." "$DEST/relay/"
fi
for F in run-relay.bat host-server.bat host-server.ps1 vajra.txt; do
    [ -f "$(pwd)/$F" ] && cp "$(pwd)/$F" "$DEST/" 2>/dev/null
done

# ---- survive Gatekeeper on the copied files ----
xattr -dr com.apple.quarantine "$DEST" 2>/dev/null

# ---- a double-clickable launcher on the desktop ----
cat > "$HOME/Desktop/Vajra Overdrive.command" <<EOF
#!/bin/sh
cd "$DEST"
exec "$DEST/bin/lwjgl3"
EOF
chmod +x "$HOME/Desktop/Vajra Overdrive.command"

# ---- the relay answers if internet play is wanted (already asked above) ----
[ -n "$RLY" ] && echo "$RLY" > "$DEST/relay.txt"

echo ""
echo "  ============================================"
echo "  INSTALLED.  \"Vajra Overdrive.command\" is on"
echo "  your desktop (if the first open is refused,"
echo "  right-click it once, then Open)."
echo "  Folder: $DEST"
echo ""
echo "  Flying with a friend: MULTIPLAYER -> HOST A RUN."
echo "  On a LAN your room appears in their list; over the"
echo "  internet the game opens its own door and prints one"
echo "  code to send. vajra.txt says more."
echo "  ============================================"
