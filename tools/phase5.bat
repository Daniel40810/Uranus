@echo off
rem Uranus - Phase 5 in einem Zug: Bestandsaufnahme, Einrichtung, Durchstich.
rem Schreibt db_inventur.log, db_setup.log, db_check.log in den Projektordner.
chcp 65001 >nul
cd /d "%~dp0\.."
if not exist build\classes\com\dan\uranus\db\UranusDao.class ( echo Bitte zuerst in NetBeans "Clean and Build" ausfuehren. & pause & exit /b 1 )
if not exist lib\ojdbc11.jar ( echo lib\ojdbc11.jar fehlt. & pause & exit /b 1 )
if not exist uranus-db.properties ( echo uranus-db.properties fehlt im Projektordner. & pause & exit /b 1 )
if not exist build\tools mkdir build\tools
javac --release 21 -nowarn -encoding UTF-8 -cp build\classes;lib\FStyle.jar -d build\tools tools\*.java tools\com\dan\uranus\render\Phase4Tests.java || ( echo Uebersetzen fehlgeschlagen. & pause & exit /b 1 )
set CP=build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar
echo === 1/3 Bestandsaufnahme (liest nur)
java -Dstdout.encoding=UTF-8 -cp %CP% UraDbInventur > nul
echo === 2/3 Einrichtung
java -Dstdout.encoding=UTF-8 -cp %CP% UraDbSetup
echo === 3/3 Durchstich
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% UraDbCheck
echo.
echo Fertig. Protokolle: db_inventur.log, db_setup.log, db_check.log
pause
