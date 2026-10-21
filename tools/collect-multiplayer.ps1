# THE MULTIPLAYER HANDBOOK COLLECTOR — one command, one text file.
# Concatenates the whole multiplayer codebase (net layer, the relay server, the
# net screens, and the multiplayer tests) into code_notes\multiplayer.txt, with a
# section separator before every file so the file reads well in any editor.
# Run it from anywhere: tools\collect-multiplayer.bat

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "code_notes"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$dest = Join-Path $outDir "multiplayer.txt"

$sections = @(
    @{ Title = "THE NET LAYER (core/src/main/java/vajra/net)";
        Dir = "core\src\main\java\vajra\net";        Names = "*.java" },
    @{ Title = "THE RELAY SERVER (relay/src/main/java/vajra/relay)";
        Dir = "relay\src\main\java\vajra\relay";     Names = "*.java" },
    @{ Title = "THE NET SCREENS - THE UI (core/src/main/java/vajra/screen)";
        Dir = "core\src\main\java\vajra\screen";
        Names = "NetScreen.java", "LobbyScreen.java", "LineInput.java" },
    @{ Title = "THE MULTIPLAYER TESTS (core/src/test/java/vajra/net)";
        Dir = "core\src\test\java\vajra\net";        Names = "*.java" },
    @{ Title = "THE SCREEN TESTS FOR NETPLAY (core/src/test/java/vajra/screen)";
        Dir = "core\src\test\java\vajra\screen";
        Names = "InviteParseTest.java", "LineInputTest.java" }
)

"FVAJRA OVERDRIVE - THE WHOLE MULTIPLAYER CODEBASE, ONE FILE" > $dest
"generated $(Get-Date -Format 'yyyy-MM-dd HH:mm') - regenerate with tools\collect-multiplayer.bat" >> $dest

foreach ($s in $sections) {
    $dir = Join-Path $root $s.Dir
    foreach ($n in $s.Names) {
        Get-ChildItem -Path (Join-Path $dir $n) -File -ErrorAction SilentlyContinue |
            Sort-Object Name | ForEach-Object {
                $rel = $_.FullName.Substring($root.Length + 1)
                "" >> $dest
                ($("=" * 100)) >> $dest
                ("== " + $s.Title) >> $dest
                ("== FILE: " + $rel) >> $dest
                ($("=" * 100)) >> $dest
                (Get-Content $_.FullName -Raw) >> $dest
            }
    }
}

$lines = (Get-Content $dest).Count
Write-Host "done: $dest ($lines lines, $((Get-Item $dest).Length) bytes)"
