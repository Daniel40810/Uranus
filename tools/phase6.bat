@echo off
rem Uranus - Phase 6 in einem Zug: Neuaufbau des Schemas (00-08), Durchstich, Pruefungen ohne Datenbank.
rem Schreibt db_setup.log, db_check.log und phase6.log in den Projektordner.
chcp 65001 >nul
cd /d "%~dp0\.."
if not exist build\classes\com\dan\uranus\render\TourPlayer.class ( echo Bitte zuerst in NetBeans "Clean and Build" ausfuehren. & pause & exit /b 1 )
if not exist build\classes\com\dan\uranus\model\eingebaut_touren.csv ( echo Build veraltet: bitte "Clean and Build" ausfuehren. & pause & exit /b 1 )
if not exist lib\ojdbc11.jar ( echo lib\ojdbc11.jar fehlt. & pause & exit /b 1 )
if not exist uranus-db.properties ( echo uranus-db.properties fehlt im Projektordner. & pause & exit /b 1 )
if not exist build\tools mkdir build\tools
javac --release 21 -nowarn -encoding UTF-8 -cp build\classes;lib\FStyle.jar -d build\tools tools\*.java tools\com\dan\uranus\render\Phase4Tests.java || ( echo Uebersetzen fehlgeschlagen. & pause & exit /b 1 )
set CP=build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar
echo === 1/3 Einrichtung (Neuaufbau, nur Objekte URA_*)
java -Dstdout.encoding=UTF-8 -cp %CP% UraDbSetup
echo === 2/3 Durchstich
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% UraDbCheck
echo === 3/3 Geschichten ohne Datenbank
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% Phase6Tests > phase6.log 2>&1
type phase6.log
echo.
echo Fertig. Protokolle: db_setup.log, db_check.log, phase6.log
pause
