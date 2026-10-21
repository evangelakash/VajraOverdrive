param([int]$Port = 27040)

# VAJRA OVERDRIVE - this laptop becomes the play server.
# Starts the bundled relay, asks YOUR OWN ROUTER (UPnP - nothing on the internet,
# no third-party service) to forward UDP here, and prints the exact line friends
# enter in the game's SERVER LINK row. The room code the lobby shows stays the
# access key: a server alone receives nobody.

$here = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1) the relay's jars and a java: the install's own copies, any dev build, else PATH
$libs = @("$here\relay\lib", "$here\relay\build\install\relay\lib", "$here\..\..\relay\build\install\relay\lib")
$lib = $libs | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $lib) {
    Write-Host "NO RELAY HERE - the installed game carries relay\ ; build gradlew :relay:installDist for a dev tree"
    exit 1
}
$javas = @("$here\jre\bin\java.exe", "$here\jre\bin\java", "java")
$java = $javas | Where-Object { Test-Path $_ } | Select-Object -First 1

# 2) the relay: skipped when one already answers on the port
if (Get-NetUDPEndpoint -LocalPort $Port -ErrorAction SilentlyContinue) {
    Write-Host "A relay already answers on UDP $Port - keeping it"
} else {
    Start-Process -WindowStyle Hidden $java -ArgumentList '-cp', "$lib\*", 'vajra.relay.RelayServer', "$Port"
    Start-Sleep -Seconds 2
    if (-not (Get-NetUDPEndpoint -LocalPort $Port -ErrorAction SilentlyContinue)) {
        Write-Host "THE RELAY DID NOT BIND UDP $Port - re-run once; if it repeats, run run-relay.bat to see the error live"
        exit 1
    }
}

# 3) your router, asked by itself: UPnP opens WAN UDP here. Some routers refuse
#    the call (a setting in the router page) - then the page does the forward,
#    once, and the LAN line below works meanwhile.
$wan = $null
try {
    Get-Service upnphost, SSDPSRV -ErrorAction SilentlyContinue |
        Where-Object { $_.Status -ne 'Running' } | Start-Service -ErrorAction SilentlyContinue
    $nat = New-Object -ComObject HNetCfg.NATUPnP
    $map = $nat.StaticPortMappingCollection   # null when no router answers the UPnP call
    if ($map) {
        try { $m = $map.Add($Port, $Port, "UDP", "Vajra Overdrive relay", "127.0.0.1", $true) }
        catch { $m = $map.Item($Port, "UDP") }  # already forwarded: keep its record
        if ($m) { $wan = $m.ExternalIPAddress; Write-Host "ROUTER: UPnP forwards WAN UDP $Port to this machine" }
    }
} catch { }

# 4) the share lines
$lanIps = Get-NetIPAddress -AddressFamily IPv4 -PrefixOrigin Dhcp | Select-Object -ExpandProperty IPAddress
$lan = $lanIps | Where-Object { $_ -like "192.168.*" -or $_ -like "10.*" } | Select-Object -First 1
if (-not $lan) { $lan = "127.0.0.1" }

""
"  THIS MACHINE IS THE SERVER"
if ($wan) { "  FOR FRIENDS ACROSS THE NET : $wan`:$Port" }
"  FOR FRIENDS ON YOUR LAN    : $lan`:$Port"
""
"  FRIENDS: run the game - NETPLAY - SERVER LINK - enter the fitting line,"
"  ENTER, then JOIN A RUN with the ROOM code shown in your lobby."
"  THE ROOM CODE IS THE ACCESS KEY: without it, nobody gets in."
""
"  Nothing was hosted anywhere else: only your own router was spoken to."
if (-not $wan) { "  (UPnP stayed silent: your router refused the call - forward UDP $Port to this"
                 "   machine once in the router's own page, and the WAN line appears next run.)" }
Read-Host "ENTER closes this window (the server keeps running)"
exit 0
