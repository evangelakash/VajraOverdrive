#!/bin/sh
# VAJRA OVERDRIVE - Mac DMG builder.  Run on a Mac:   sh make-mac-dmg.sh
# Builds "Vajra Overdrive.app" (mk1 ship icon) and a drag-to-Applications
# DMG with the tools that ship inside macOS (sips, iconutil, hdiutil) -
# nothing to download or install. Needs the unzipped setup package (the
# app/ folder and ship128.png sit beside this script).
set -e
cd "$(dirname "$0")"
[ -f ship128.png ] || { echo "  ship128.png missing - use the unzipped setup package"; exit 1; }
[ -d app ] || { echo "  app/ missing - use the unzipped setup package"; exit 1; }

APP="Vajra Overdrive.app"
WORK="$PWD/.dmgstage"
DMG="$PWD/VajraOverdrive.dmg"
rm -rf "$WORK" "$DMG"
mkdir -p "$WORK"
trap 'rm -rf "$WORK"' EXIT

# ---- the app bundle: Contents/Resources/game = the game, MacOS/vajra = launcher
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/game"
cp -R app/* "$APP/Contents/Resources/game/"
chmod +x "$APP/Contents/Resources/game/bin/lwjgl3"

cat > "$APP/Contents/MacOS/vajra" <<'EOF'
#!/bin/sh
RES="$(dirname "$0")/../Resources/game"
# our own runtime rides in the app when it was built with a jlink image under
# app/jre-mac-*; pick it by architecture, else the other, else the machine's java
CAND="jre-mac-arm jre-mac-x64"
[ "$(uname -m)" = arm64 ] || CAND="jre-mac-x64 jre-mac-arm"
JRE=""
for a in $CAND; do
    [ -x "$RES/$a/bin/java" ] && { JRE="$RES/$a/bin/java"; break; }
done
if [ -z "$JRE" ]; then
    command -v java >/dev/null 2>&1 || {
        osascript -e 'display alert "Java missing" message "Vajra Overdrive needs Java 17 or newer. Install the free Temurin 17 from adoptium.net, then run the game again."'
        exit 1
    }
    JRE=java
fi
cd "$RES" || exit 1
# the working directory IS the install folder: the game reads relay.txt from
# here and looks for the bundled relay (relay/lib) beside it
# XstartOnFirstThread is macOS law for GLFW: a launcher zip built on Windows
# never carries it, so the wrapper passes it itself
exec "$JRE" -XstartOnFirstThread -cp "$RES/lib/*" vajra.desktop.DesktopLauncher
EOF
chmod +x "$APP/Contents/MacOS/vajra"

cat > "$APP/Contents/Info.plist" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key><string>Vajra Overdrive</string>
  <key>CFBundleDisplayName</key><string>Vajra Overdrive</string>
  <key>CFBundleIdentifier</key><string>vajra.overdrive</string>
  <key>CFBundleExecutable</key><string>vajra</string>
  <key>CFBundleIconFile</key><string>vajra</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleVersion</key><string>1.0</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
EOF

# ---- the mk1 ship as the icns: ship128.png scaled into an iconset
ICONSET="$WORK/ship.iconset"
mkdir "$ICONSET"
sips -z 16 16 ship128.png --out "$ICONSET/icon_16x16.png" >/dev/null
sips -z 32 32 ship128.png --out "$ICONSET/icon_16x16@2x.png" >/dev/null
sips -z 32 32 ship128.png --out "$ICONSET/icon_32x32.png" >/dev/null
sips -z 64 64 ship128.png --out "$ICONSET/icon_32x32@2x.png" >/dev/null
sips -z 128 128 ship128.png --out "$ICONSET/icon_128x128.png" >/dev/null
sips -z 256 256 ship128.png --out "$ICONSET/icon_128x128@2x.png" >/dev/null
iconutil -c icns "$ICONSET" -o "$APP/Contents/Resources/vajra.icns"

# ---- the DMG: app left, Applications shortcut right - the drag-to-install
ln -s /Applications "$WORK/Applications"
cp -R "$APP" "$WORK/"
hdiutil create -volname "Vajra Overdrive" -srcfolder "$WORK" -ov -format UDZO "$DMG" >/dev/null

echo ""
echo "  ============================================"
echo "  BUILT:  $DMG"
echo "  The friend: open the dmg, drag Vajra Overdrive"
echo "  onto Applications, then launch it from there."
echo "  (First open refused? right-click the app once"
echo "  and choose Open - Gatekeeper's one-time ask.)"
echo "  Java 17+ is asked for at first launch."
echo "  ============================================"
