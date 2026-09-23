@echo off
rem Uranus - Abnahme: alle Pruefungen in einem Zug, ein gemeinsames Protokoll alles.log im Projektordner.
rem   1. ohne Datenbank: UranusTests, Phase4Tests ... Phase8Tests
rem   2. Standbilder nach shots\
rem   3. Oberflaeche (UiCheck): oeffnet die App, drueckt Tasten und klickt - etwa 3 Minuten Maus und Tastatur nicht benutzen.
rem      Eigener Einstellungsknoten und eigener Bilderordner (shots\standbilder), deine Einstellungen bleiben unberuehrt.
rem   4. mit Datenbank (nur wenn uranus-db.properties da ist): Durchstich in zwei Laeufen (db_check.log).
rem      Das Schema wird NICHT neu aufgebaut, deine gespeicherten Szenarien bleiben.
chcp 65001 >nul
cd /d "%~dp0\.."
if not exist build\classes\com\dan\uranus\app\AppPrefs.class ( echo Bitte zuerst in NetBeans "Clean and Build" ausfuehren. & pause & exit /b 1 )
if not exist dist\Uranus.jar ( echo dist\Uranus.jar fehlt: bitte "Clean and Build" ausfuehren. & pause & exit /b 1 )
if not exist build\tools mkdir build\tools
javac --release 21 -nowarn -encoding UTF-8 -cp build\classes;lib\FStyle.jar -d build\tools tools\*.java tools\com\dan\uranus\render\Phase4Tests.java || ( echo Uebersetzen fehlgeschlagen. & pause & exit /b 1 )
set CP=build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar
set J=java -Dstdout.encoding=UTF-8
echo Gleich oeffnet sich die App fuer die Oberflaechenpruefung. Bitte dann etwa 3 Minuten Maus und Tastatur nicht benutzen.
pause
(
  echo === 1/4 Ohne Datenbank
  %J% -Djava.awt.headless=true -cp %CP% UranusTests
  %J% -Djava.awt.headless=true -cp %CP% com.dan.uranus.render.Phase4Tests
  %J% -Djava.awt.headless=true -cp %CP% Phase5Tests
  %J% -Djava.awt.headless=true -cp %CP% Phase6Tests
  %J% -Djava.awt.headless=true -cp %CP% Phase7Tests
  %J% -Djava.awt.headless=true -cp %CP% Phase8Tests
  echo === 2/4 Standbilder
  %J% -Djava.awt.headless=true -cp %CP% UranusShots shots
  echo === 3/4 Oberflaeche
  %J% -cp %CP% UiCheck shots
  echo === 4/4 Datenbank
  if exist uranus-db.properties ( %J% -Djava.awt.headless=true -cp %CP% UraDbCheck ) else ( echo uranus-db.properties fehlt - Durchstich uebersprungen )
) > alles.log 2>&1
echo.
findstr /b /l /c:"===" /c:"Ergebnis" /c:"[FEHLER]" alles.log
echo.
echo Fertig. Protokolle: alles.log und db_check.log, Bilder in shots\
pause
