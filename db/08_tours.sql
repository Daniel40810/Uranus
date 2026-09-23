-- =====================================================================
-- Uranus / Schema DEMO
-- 08 - Kamerafahrten
-- Erzeugt von tools/UraStoryGen. Blickrichtungen zur Sonde aus der Hyperbel berechnet.
-- =====================================================================

BEGIN
  ura_api.put_tour('VOYAGER', 'Voyager 2 · 1986', 'VOYAGER', 'CLOCK', 1,
    'Der einzige Besuch: der Vorbeiflug am 24. Januar 1986');
  ura_api.put_tour_step('VOYAGER', 1, 0, 2446453.999205, 170.98, 29.36, 36, 'Uranus',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Voyager 2 · Januar 1986', 'Nach achteinhalb Jahren und zwei Planeten kommt die Sonde fast genau aus Richtung Sonne. Uranus zeigt ihr den Südpol: Es ist Sommer im Süden.');
  ura_api.put_tour_step('VOYAGER', 2, 12, 2446454.899205, -155.32, -44.36, 4.5, 'Voyager 2',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Anflug', 'Noch gut acht Stunden. Die Kameras finden zehn neue Monde und zwei neue Ringe. Das Magnetfeld ist um 59° gekippt — niemand hatte das erwartet.');
  ura_api.put_tour_step('VOYAGER', 3, 26, 2446455.209969, -157.52, -25.72, 2.6, 'Voyager 2',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Vorbei an Miranda', 'Nächster Punkt zu Miranda: 29.099 km. Die Bilder zeigen einen Mond aus Flicken — Kanten, Terrassen und Steilwände bis 20 km Höhe.');
  ura_api.put_tour_step('VOYAGER', 4, 38, 2446455.219019, -160.35, -18.59, 2.2, 'Voyager 2',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Durch die Ringebene', 'Voyager 2 kreuzt die Äquatorebene 115.419 km vom Zentrum — 4,5 Uranusradien, weit außerhalb der dichten Ringe. Die Plasmawellen-Antenne hört Staubkörner auf die Sonde prasseln.');
  ura_api.put_tour_step('VOYAGER', 5, 50, 2446455.249205, -121.85, 25.97, 2.4, 'Voyager 2',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Größte Annäherung · 24. Januar 1986, 17:58 UTC', 'Voyager 2 fliegt 81.595 km über der Wolkenoberfläche vorbei (107.154 km vom Zentrum), mit 18,0 km/s relativ zu Uranus.');
  ura_api.put_tour_step('VOYAGER', 6, 62, 2446455.361289, -15.19, 77.39, 7.5, 'Uranus',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Im Schatten', 'Für 76 Minuten steht Uranus zwischen Sonde und Sonne. Das Sonnenlicht, das durch die Atmosphäre fällt, verrät deren Aufbau.');
  ura_api.put_tour_step('VOYAGER', 7, 74, 2446456.449205, 170.98, 29.36, 38, 'Uranus',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', 'Weiter nach Neptun', 'Uranus lenkt die Sonde um. Dreieinhalb Jahre später erreicht sie Neptun — bis heute war kein anderes Raumschiff bei Uranus.');
  ura_api.put_tour_step('VOYAGER', 8, 82, 2446456.549205, 170.98, 29.36, 40, 'Uranus',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;time=ease', NULL, NULL);
END;
/

BEGIN
  ura_api.put_tour('JAHR', 'Ein Uranusjahr', 'SEASONS', 'CLOCK', 2,
    '84 Erdjahre in 90 Sekunden: Sonnenwenden und Tagundnachtgleichen aus der Ephemeride');
  ura_api.put_tour_step('JAHR', 1, 0, 2431915.446222, 48, 14, 7.2, 'Uranus',
    'moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin', 'Sonnenwende 1946', 'Die Sonne steht über 82,2° Nord, fast über dem Pol. Mittsommer im Norden: seit 21 Jahren Tag am Nordpol, am anderen Pol Nacht.');
  ura_api.put_tour_step('JAHR', 2, 20, 2439150.898536, 48, 14, 7.2, 'Uranus',
    'moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin', 'Tagundnachtgleiche 1966', 'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Süden Frühling — für 21 Jahre.');
  ura_api.put_tour_step('JAHR', 3, 40, 2446337.527017, 48, 14, 7.2, 'Uranus',
    'moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin', 'Sonnenwende 1985', 'Die Sonne steht über 82,2° Süd, fast über dem Pol. Mittsommer im Süden: seit 21 Jahren Tag am Südpol, am anderen Pol Nacht.');
  ura_api.put_tour_step('JAHR', 4, 60, 2454440.395245, 48, 14, 7.2, 'Uranus',
    'moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin', 'Tagundnachtgleiche 2007', 'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Norden Frühling — für 21 Jahre.');
  ura_api.put_tour_step('JAHR', 5, 80, 2462602.445682, 48, 14, 7.2, 'Uranus',
    'moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin', 'Sonnenwende 2030', 'Die Sonne steht über 82,2° Nord, fast über dem Pol. Mittsommer im Norden: seit 21 Jahren Tag am Nordpol, am anderen Pol Nacht.');
  ura_api.put_tour_step('JAHR', 6, 90, 2462662.445682, 48, 14, 7.2, 'Uranus',
    'moons=off;trails=0;magneto=0;space=0;scale=0;axis=1;cam=sun;time=lin', NULL, NULL);
END;
/

BEGIN
  ura_api.put_tour('ENTDECKUNG', 'Entdeckungen', 'DISCOVERY', 'STORY', 3,
    'Von Herschel bis JWST: Monde und Ringe erscheinen im Jahr ihrer Entdeckung');
  ura_api.put_tour_step('ENTDECKUNG', 1, 0, 2371629.416667, 26, 32, 24, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1781 · Herschel', 'In Bath sieht William Herschel ein Scheibchen, das kein Stern ist. Er hält es für einen Kometen — es ist der erste mit dem Fernrohr gefundene Planet.');
  ura_api.put_tour_step('ENTDECKUNG', 2, 9, 2373758.5, 40, 30, 26, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1787 · Titania und Oberon', 'Sechs Jahre später findet Herschel die zwei größten Monde. Sein Sohn John benennt sie nach Shakespeares Elfenkönigspaar.');
  ura_api.put_tour_step('ENTDECKUNG', 3, 18, 2397419.5, 56, 28, 24, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1851 · Ariel und Umbriel', 'William Lassell, Brauer und Amateurastronom, sieht mit seinem 24-Zoll-Spiegel zwei weitere Monde.');
  ura_api.put_tour_step('ENTDECKUNG', 4, 27, 2432597.5, 70, 26, 20, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1948 · Miranda', 'Gerard Kuiper findet am McDonald-Observatorium den fünften Mond — fast hundert Jahre nach Lassell.');
  ura_api.put_tour_step('ENTDECKUNG', 5, 36, 2443213.333333, 86, 24, 9, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1977 · Die Ringe', 'Ein Stern flackert vor und nach Uranus neunmal: Ringe! Entdeckt aus einem Flugzeug über dem Indischen Ozean, dem Kuiper Airborne Observatory.');
  ura_api.put_tour_step('ENTDECKUNG', 6, 46, 2446455.25, 104, 22, 11, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1986 · Voyager 2', 'Der einzige Besuch: zehn neue Monde von Puck bis Cordelia, dazu λ und der Staubring ζ.');
  ura_api.put_tour_step('ENTDECKUNG', 7, 56, 2450697.5, 122, 34, 95, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1997 · Caliban und Sycorax', 'Die ersten irregulären Monde: eingefangene Brocken auf weiten, rückläufigen Bahnen.');
  ura_api.put_tour_step('ENTDECKUNG', 8, 65, 2452791.5, 140, 30, 90, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '1999–2003 · Hubble und Bodenteleskope', 'Perdita in alten Voyager-Bildern, Cupid und Mab mit Hubble, Margaret als einziger rechtläufiger Irregulärer — und die Staubringe ν und μ.');
  ura_api.put_tour_step('ENTDECKUNG', 9, 74, 2460252.5, 156, 30, 70, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '2023 · S/2023 U1', 'Ein 8 km großer Brocken, gefunden mit dem Magellan-Teleskop in Chile.');
  ura_api.put_tour_step('ENTDECKUNG', 10, 82, 2460708.5, 170, 26, 18, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', '2025 · S/2025 U1', 'Das James-Webb-Teleskop findet einen 10 km kleinen Mond zwischen Ophelia und Bianca — der 29. Uranusmond.');
  ura_api.put_tour_step('ENTDECKUNG', 11, 92, 2461040.5, 180, 26, 30, 'Uranus',
    'moons=2;trails=1;magneto=0;space=0;scale=0;axis=0;rate=0.0416667;time=ease', NULL, NULL);
END;
/

BEGIN
  ura_api.put_tour('RUNDFLUG', 'Rundflug', 'NONE', 'STORY', 4,
    'Einführung: was die App zeigt');
  ura_api.put_tour_step('RUNDFLUG', 1, 0, NULL, 32, 24, 30, 'Uranus',
    'moons=1;trails=1;magneto=1;space=0;scale=0;axis=0;flight=0;rate=0.0416667', 'Uranus heute', 'Der Planet liegt auf der Seite: 97,8° Achsneigung. Die Mulde zeigt, wie seine Masse den Raum krümmt — Tiefe aus der Masse, Breite aus dem Radius.');
  ura_api.put_tour_step('RUNDFLUG', 2, 12, NULL, 78, 30, 34, 'Uranus',
    'space=1', 'Gitterraum', 'Dasselbe als räumliches Gitter, das zu den Massen hingezogen wird.');
  ura_api.put_tour_step('RUNDFLUG', 3, 24, NULL, 110, 18, 2.6, 'Miranda',
    'space=0', 'Miranda', 'Der Flickenteppich unter den Monden: Canyons, Terrassen, Steilwände.');
  ura_api.put_tour_step('RUNDFLUG', 4, 38, NULL, 160, 8, 26, 'Uranus',
    'magneto=1', 'Magnetfeld', '59° gegen die Drehachse gekippt und zum Südpol versetzt; der Schweif windet sich als Korkenzieher.');
  ura_api.put_tour_step('RUNDFLUG', 5, 52, NULL, -150.1206940451, -73.8792424115, 16, 'Uranus',
    'magneto=0;lens=1', 'Gravitationslinse', 'Hinter Uranus das Zentrum der Milchstraße, vom Planeten zu einem Ring verbogen — stark überhöht.');
  ura_api.put_tour_step('RUNDFLUG', 6, 64, NULL, -150.1206940451, -73.8792424115, 16, 'Uranus',
    'flight=1', 'Im ε-Ring', 'Durch den hellsten Ring, zwischen den Hirten Cordelia und Ophelia.');
  ura_api.put_tour_step('RUNDFLUG', 7, 84, NULL, 32, 24, 30, 'Uranus',
    'flight=0;magneto=1', 'Und jetzt du', 'Ziehen dreht, das Mausrad zoomt, ein Klick auf einen Mond fliegt hin.');
  ura_api.put_tour_step('RUNDFLUG', 8, 92, NULL, 40, 24, 30, 'Uranus',
    '', NULL, NULL);
END;
/

COMMIT;
