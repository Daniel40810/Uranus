# Legt auf dem Desktop die Verknuepfung "Uranus" mit dem Programmsymbol an.
# Aufruf ueber Verknuepfung.bat (Doppelklick).
# Die Verknuepfung startet javaw (ohne Konsolenfenster) im Projektordner; dort findet die App
# uranus-db.properties fuer die Datenbank DEMO.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar  = Join-Path $root 'dist\Uranus.jar'
$ico  = Join-Path $root 'icon\uranus.ico'
if (-not (Test-Path $jar)) { throw "dist\Uranus.jar fehlt - zuerst in NetBeans bauen (Clean and Build)." }
if (-not (Test-Path $ico)) { throw "icon\uranus.ico fehlt." }

$javaw = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javaw.exe'))) {
    $javaw = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
} else {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) { throw "javaw.exe nicht gefunden - JAVA_HOME setzen oder Java in den PATH aufnehmen." }

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'Uranus.lnk'
$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath       = $javaw
$s.Arguments        = '-Xmx2g -jar "' + $jar + '"'
$s.WorkingDirectory = $root
$s.IconLocation     = $ico
$s.Description      = 'Uranus mit Ringen und Monden im gekruemmten Raum'
$s.Save()
Write-Host "Verknuepfung angelegt: $lnk"
