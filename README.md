# Uranus

Der Planet, der auf der Seite liegt: Uranus mit seinen 13 Ringen und 29 Monden im gekrümmten Raum, mit echtem Datum, Sonnenstand, Magnetfeld und Polarlicht. Dazu gibt es vier Geschichten (Voyager 2 · 1986, ein Uranusjahr, Entdeckungen, Rundflug) und eine Spielwiese mit N-Körper-Rechnung, auf der man Monde ziehen, werfen und schwerer machen kann.

Java 21 · Swing · FStyle · Oracle 21c (optional). Paket `com.dan.uranus`, Hauptklasse `com.dan.uranus.app.UranusApp`.

## Starten

- **Aus NetBeans:** Projekt öffnen und „Run“. Die Hauptklasse ist eingetragen.
- **Aus `dist`:** Nach „Clean and Build“ liegt `dist\Uranus.jar` mit `dist\lib\` bereit. Doppelklick auf die jar oder `java -jar dist\Uranus.jar`.
- Die App startet sofort mit dem eingebauten Datensatz. Ist eine Datenbank eingerichtet, lädt sie im Hintergrund, und die Monde gleiten auf ihre Lage aus der Datenbank. Reihe 1 zeigt „Daten: Datenbank DEMO“ oder „Daten: eingebaut“; der Tooltip nennt den Grund.

## Bedienung

| Taste / Griff | Wirkung |
|---|---|
| Ziehen · Mausrad | Kamera drehen · zoomen |
| Klick auf einen Mond | hinfliegen, folgen, Karte |
| Doppelklick ins Leere · Esc | zurück zur Übersicht |
| Überfahren (0,3 s) | kurze Karte am Zeiger: Monde, Ringe, Uranus, Sonde, Punkte der Zeitleisten |
| Leertaste | Zeit anhalten / weiter (während einer Geschichte: Fahrt anhalten) |
| + / − | Tempo schneller / langsamer |
| 0 · 1–5 | Uranus (Übersicht) · Miranda, Ariel, Umbriel, Titania, Oberon |
| G · T · L | Mulde oder Gitterraum · Bahnen · Beschriftung |
| S oder Kamerasymbol oben rechts | Standbild |
| F1, ? oder Symbol „?“ | Hilfetafel mit allen Tasten, dort auch „Auf Standard zurück“ |

**Geschichten** (Reihe 3): Voyager 2 · 1986, Ein Uranusjahr, Entdeckungen, Rundflug. Esc, ein Klick, Ziehen, das Mausrad oder derselbe Knopf beenden die Fahrt; danach stehen Uhr, Tempo und Ebenen wie vorher.

**Spielwiese** (Knopf in Reihe 2): N-Körper-Rechnung (IAS15, Uranus mit J2 und J4, Sonne als Störer) ab dem echten Stand von jetzt oder aus einem Szenario.

| Griff | Wirkung |
|---|---|
| Mond ziehen | verschieben; beim Loslassen Kreisbahn plus Schwung der Maus |
| Mausrad über einem Mond | Masse in Stufen von ×√10 (×1/1000 bis ×10 000) |
| Umschalt + Ziehen ins Leere | Gast setzen; das Mausrad wählt vorher die Masse |
| Strg+Z oder „Rückgängig“ | einen Zwischenstand zurück (alle 2 s gespeichert, bis zu 60) |
| „Eigene ▾“ | Stand speichern (nur mit Datenbank), „Jetzt“, eigene Szenarien |
| „Zurück“ | Spielwiese verlassen; die Monde gleiten an ihren echten Ort |

**Standbilder** landen als PNG in doppelter Fenstergröße (höchstens 3 840 px breit) in `Bilder\Uranus` (`%USERPROFILE%\Pictures\Uranus`, anderer Ordner mit `-Duranus.bilder=...`). Datum der Szene, Sonnenstand und Einstellungen stehen als Textfelder im PNG.

**Einstellungen** (Fenster, Raum, Maßstab, Monde, Ebenen, Tempo) werden beim Beenden gemerkt, unter Windows in der Registry unter `HKCU\Software\JavaSoft\Prefs\com\dan\uranus`. Datum, Geschichte und Spielwiese nicht: jeder Start beginnt in der Gegenwart.

**Protokoll:** `%USERPROFILE%\.uranus\uranus.log` (bei 1 MB wird eine Vorgängerdatei `uranus.log.1` angelegt). Fehler beim Zeichnen erscheinen zusätzlich als Zeile im Bild.

Auf Bildschirmen mit Skalierung (125 %, 150 % …) rechnet die App in Gerätepixeln. Wird das zu langsam, senkt sie die Auflösung selbst; Reihe 2 zeigt Bilder je Sekunde und Auflösung.

## Datenbank

Oracle 21c, Service `PDBORCL`, Schema `DEMO`. Alle Objekte tragen das Präfix `URA_`, der Zugriff läuft über das Package `URA_API` (1.2).

| Tabelle | Inhalt |
|---|---|
| URA_SOURCE | Quellen mit Prüfsumme |
| URA_BODY, URA_ORBIT, URA_RING | Uranus, 29 Monde, 58 Bahnen (29 gültige), 13 Ringe |
| URA_EVENT | 57 Ereignisse: Entdeckungen, Vorbeiflug, Jahreszeiten |
| URA_CRAFT, URA_CRAFT_STATE | Voyager 2 mit 145 Horizons-Vektoren |
| URA_TOUR, URA_TOUR_STEP | 4 Fahrten mit 33 Stationen |
| URA_SCENARIO, URA_SCENARIO_BODY | 5 eingebaute Szenarien mit 75 Körpern, dazu eigene |

**Verbindung:** eine Datei `uranus-db.properties`:

```
url=jdbc:oracle:thin:@//localhost:1521/PDBORCL
user=DEMO
password=...
```

Die App sucht sie nacheinander: `-Duranus.db=...`, Arbeitsordner, neben `Uranus.jar`, im Ordner darüber (Projektordner), `%USERPROFILE%\.uranus\`. Das Kennwort steht nur in dieser Datei. Sie wird nicht nach `dist` kopiert und gehört nicht in die Versionsverwaltung.

**Einrichten:** `tools\db_setup.bat` baut das Schema neu auf (Skripte `db\00_drop.sql` bis `db\09_scenarios.sql`, nur Objekte `URA_*`) und macht einen Selbsttest. `tools\db_check.bat` prüft in zwei Läufen, ob die App aus der Datenbank dasselbe lädt wie aus dem eingebauten Satz, ob PL/SQL wie Java rechnet und ob die Regeln des Schemas greifen. Ohne Datenbank läuft die App mit dem eingebauten Satz (`src\com\dan\uranus\model\eingebaut_*.csv`).

## Prüfen

Vorher einmal NetBeans „Clean and Build“; `javac` muss im `PATH` stehen.

| Datei | Was |
|---|---|
| `tools\alles.bat` | alles in einem Zug: ohne Datenbank, Oberfläche, Einrichtung und Durchstich; ein gemeinsames Protokoll `alles.log` |
| `tools\pruefen.bat` | Prüfungen ohne Datenbank (Phasen 1–8) und Standbilder nach `shots\` |
| `tools\phase7.bat`, `tools\phase6.bat` … | die Läufe einzelner Phasen |

Die Oberflächenprüfung benutzt einen eigenen Einstellungsknoten und einen eigenen Bilderordner; die echten Einstellungen bleiben unberührt.

## Aufbau

| Paket | Inhalt |
|---|---|
| `app` | UranusApp (Fenster, Tasten), ControlBar (drei Reihen FStyle), AppPrefs, Snapshot, AppLog, WrapLayout |
| `model` | Body, Ring, OrbitElements, Craft, Tour, StoryEvent, Scenario, SimClock, UranusSystem (eingebauter Satz) |
| `physics` | Ephemeris (Sonnenstand, Pol, Rotation), Kepler, TimeScale (UTC → TDB), SpaceMap, CurvatureField |
| `play` | Ias15, PlayForces, Playground, PlayBody, PlayEvent, Debris |
| `render` | Renderer, eigener Render-Thread (RenderLoop), Himmel, Planet, Monde, Magnetosphäre, Lichtbahnen, Ringflug, Nachbearbeitung, Karten, Hilfe |
| `db` | UranusDb (Verbindungsdatei), UranusDao (URA_API → UranusSystem) |
| `ui` | UranusView: Bild zeigen, Maus und Tasten |
| `tools/` | Prüfprogramme, Erzeuger der Skripte (UraGen, UraStoryGen, UraPlayGen), Einrichtung und Durchstich |
| `db/` | Skripte 00–09, `quellen/` mit den gesicherten Rohdaten und Berichten |

Weltkoordinaten: y = Nordpol nach IAU, x = Knoten des Uranusäquators auf dem ICRF-Äquator, z = x × y. Die Monde laufen rückläufig um +y.

## Quellen

- JPL Solar System Dynamics, Planetary Satellite Mean Elements (URA182, URA184, URA117): https://ssd.jpl.nasa.gov/sats/elem/
- JPL Horizons (Monde 1975–2030, Voyager 2 im Januar 1986): https://ssd.jpl.nasa.gov/horizons/
- Ringtabelle nach Wikipedia „Rings of Uranus“, Revision 1372354849, lizenziert unter CC BY-SA 4.0; darin Esposito 2002, Karkoschka 2001, de Pater 2006, Stone 1986, French 1988
- IAU Working Group on Cartographic Coordinates and Rotational Elements (Pol und Rotation), Standish (Bahnelemente der Planeten)
- Rein, H. & Spiegel, D. S. (2015): IAS15, MNRAS 446, 1424 (Integrator der Spielwiese)
- Zur Stabilität der inneren Monde: arXiv 2205.14272

Die Rohdaten liegen unverändert mit SHA-256 in `db\quellen\`; `LIESMICH.txt` beschreibt Abruf und Befunde.
