# Makes the desktop and start-menu shortcuts for the installed game.
# The launcher is play.bat when it came with the copy (it prefers the bundled
# Java runtime); otherwise the Gradle start script beside bin\.
param([Parameter(Mandatory = $true)][string]$Dest)
$launcher = if (Test-Path "$Dest\play.bat") { "$Dest\play.bat" } else { "$Dest\bin\lwjgl3.bat" }
$w = New-Object -ComObject WScript.Shell
foreach ($base in @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))) {
    $s = $w.CreateShortcut("$base\Vajra Overdrive.lnk")
    $s.TargetPath = $launcher
    $s.WorkingDirectory = $Dest
    $s.IconLocation = "$Dest\vajra.ico, 0"
    $s.Save()
}
