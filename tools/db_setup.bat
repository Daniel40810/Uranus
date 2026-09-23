@echo off
rem Uranus - Schema einrichten: db\00_drop.sql bis db\09_scenarios.sql. Protokoll: db_setup.log
rem   db_setup.bat                  alle Skripte (Neuaufbau, nur Objekte URA_*)
rem   db_setup.bat 04_orbits.sql    nur dieses Skript
chcp 65001 >nul
cd /d "%~dp0\.."
if not exist build\classes\com\dan\uranus\db\UranusDao.class ( echo Bitte zuerst in NetBeans "Clean and Build" ausfuehren. & pause & exit /b 1 )
if not exist lib\ojdbc11.jar ( echo lib\ojdbc11.jar fehlt. & pause & exit /b 1 )
if not exist uranus-db.properties ( echo uranus-db.properties fehlt im Projektordner. & pause & exit /b 1 )
if not exist build\tools mkdir build\tools
javac --release 21 -nowarn -encoding UTF-8 -cp build\classes;lib\FStyle.jar -d build\tools tools\*.java tools\com\dan\uranus\render\Phase4Tests.java || ( echo Uebersetzen fehlgeschlagen. & pause & exit /b 1 )
set CP=build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar
java -Dstdout.encoding=UTF-8 -cp %CP% UraDbSetup %*
echo.
echo Fertig. Protokoll: db_setup.log
pause
