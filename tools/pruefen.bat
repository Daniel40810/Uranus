@echo off
rem Prueflauf fuer Uranus (Phase 1-8, ohne Datenbank): uebersetzt tools\ gegen build\classes und schreibt pruefen.log
rem Voraussetzung: das Projekt wurde in NetBeans gebaut (build\classes vorhanden), javac/java im PATH.
chcp 65001 >nul
cd /d "%~dp0\.."
if not exist build\classes\com\dan\uranus ( echo Bitte zuerst in NetBeans bauen. & exit /b 1 )
if not exist build\tools mkdir build\tools
javac --release 21 -nowarn -encoding UTF-8 -cp build\classes;lib\FStyle.jar -d build\tools tools\*.java tools\com\dan\uranus\render\Phase4Tests.java || exit /b 1
set CP=build\classes;build\tools;lib\FStyle.jar;lib\ojdbc11.jar
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% UranusTests > pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% com.dan.uranus.render.Phase4Tests >> pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% Phase5Tests >> pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% Phase6Tests >> pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% Phase7Tests >> pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% Phase8Tests >> pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -Djava.awt.headless=true -cp %CP% UranusShots shots >> pruefen.log 2>&1
java -Dstdout.encoding=UTF-8 -cp %CP% UiCheck shots >> pruefen.log 2>&1
type pruefen.log
