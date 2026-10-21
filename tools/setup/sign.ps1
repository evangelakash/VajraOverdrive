# Authenticode-signs the built installer with a self-signed code-signing identity
# created once on this machine, then hands over a SHA-256 for the friend's check.
# An honest note: a self-signed signature shows a publisher and detects tampering,
# but no third party vouches for it. A paid code-signing certificate is the upgrade.
param([Parameter(Mandatory = $true)][string]$Exe)

$subj = "CN=Vajra Overdrive Setup"
$cert = Get-ChildItem Cert:\CurrentUser\My |
    Where-Object { $_.Subject -eq $subj -and $_.HasPrivateKey } |
    Sort-Object NotAfter -Descending | Select-Object -First 1
if ($cert -eq $null) {
    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject $subj `
        -KeyUsage DigitalSignature -KeyExportPolicy Exportable `
        -CertStoreLocation "Cert:\CurrentUser\My" -NotAfter ((Get-Date).AddYears(3))
}

$sig = Set-AuthenticodeSignature -FilePath $Exe -Certificate $cert -HashAlgorithm SHA256
"signature: $($sig.Status)  ($sig.SignerCertificate.Subject)"
$hash = (Get-FileHash -Algorithm SHA256 $Exe).Hash
Set-Content -Path "$Exe.sha256" -Value $hash
"sha256: $hash"
# A self-signed identity can never land in the machine's Trusted Root store, so
# Windows calls its chain untrusted — that is expected here. What the signature
# still buys: if anyone edits the exe after it was signed, the tab says "invalid".
$rejected = $sig.StatusMessage -and
            $sig.StatusMessage -notmatch "not trusted by the trust provider"
if ($sig.Status -ne "Valid" -and $rejected) { exit 1 }
