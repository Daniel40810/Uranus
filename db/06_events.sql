-- =====================================================================
-- Uranus / Schema DEMO
-- 06 - Quellen und Ereignisse der Geschichten
-- Erzeugt von tools/UraStoryGen: Entdeckungen, Vorbeiflug und Jahreszeiten (berechnet). Nicht von Hand ändern.
-- =====================================================================

BEGIN
  ura_api.put_source(p_code => 'HZ_VOYAGER2', p_title => 'JPL Horizons: Voyager 2 (-32) relativ zu Uranus',
    p_url => 'https://ssd.jpl.nasa.gov/api/horizons.api', p_ephemeris => 'Voyager_2_ST+refit2022_m', p_retrieved => DATE '2026-09-23', p_sha256 => '1fd6487f8da11a2ce823acdfb8f112be47cb64f29766936bfa5b71b006dbf25d',
    p_note => 'Bis 1989 Missionsentwurf aus Kegelschnitten (grobe Genauigkeit); 145 Vektoren 21.-27.01.1986');
END;
/

BEGIN
  ura_api.put_source(p_code => 'CALC', p_title => 'Berechnet in der App (Ephemeride, Hyperbel, Mondbahnen)',
    p_url => NULL, p_ephemeris => NULL, p_retrieved => DATE '2026-09-23', p_sha256 => NULL,
    p_note => 'tools/UraStoryGen: Jahreszeiten 1900-2100, Ereignisse des Vorbeiflugs');
END;
/

BEGIN
  ura_api.put_source(p_code => 'HIST', p_title => 'Entdeckungsgeschichte (Standardliteratur, Wikipedia: Moons of Uranus, Rings of Uranus)',
    p_url => 'https://en.wikipedia.org/wiki/Moons_of_Uranus', p_ephemeris => NULL, p_retrieved => DATE '2026-09-23', p_sha256 => NULL,
    p_note => 'Tage nur, wo sie überliefert sind; sonst das Jahr');
END;
/

BEGIN
  ura_api.put_event(2371629.416667, 'DISCOVERY', 'DAY', 'Uranus entdeckt',
    'William Herschel sieht in Bath ein Scheibchen, das kein Stern ist, und hält es zuerst für einen Kometen. Es ist der erste Planet, der mit einem Fernrohr gefunden wurde.', NULL, NULL, 'HIST');
  ura_api.put_event(2373758.5, 'DISCOVERY', 'DAY', 'Titania entdeckt',
    'William Herschel · Name aus „Ein Sommernachtstraum“ — Elfenkönigin', 'Titania', NULL, 'HIST');
  ura_api.put_event(2373758.5, 'DISCOVERY', 'DAY', 'Oberon entdeckt',
    'William Herschel · Name aus „Ein Sommernachtstraum“ — Elfenkönig', 'Oberon', NULL, 'HIST');
  ura_api.put_event(2397419.5, 'DISCOVERY', 'DAY', 'Ariel entdeckt',
    'William Lassell · Name aus „Der Sturm“ und Pope — ein Luftgeist', 'Ariel', NULL, 'HIST');
  ura_api.put_event(2397419.5, 'DISCOVERY', 'DAY', 'Umbriel entdeckt',
    'William Lassell · Name aus Pope, „Der Lockenraub“ — ein düsterer Geist', 'Umbriel', NULL, 'HIST');
  ura_api.put_event(2415649.49337, 'SEASON', 'DAY', 'Sonnenwende 1901',
    'Die Sonne steht über 82,2° Süd, fast über dem Pol. Mittsommer im Süden: seit 21 Jahren Tag am Südpol, am anderen Pol Nacht.', NULL, NULL, 'CALC');
  ura_api.put_event(2423755.804454, 'SEASON', 'DAY', 'Tagundnachtgleiche 1923',
    'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Norden Frühling — für 21 Jahre.', NULL, NULL, 'CALC');
  ura_api.put_event(2431915.446222, 'SEASON', 'DAY', 'Sonnenwende 1946',
    'Die Sonne steht über 82,2° Nord, fast über dem Pol. Mittsommer im Norden: seit 21 Jahren Tag am Nordpol, am anderen Pol Nacht.', NULL, NULL, 'CALC');
  ura_api.put_event(2432597.5, 'DISCOVERY', 'DAY', 'Miranda entdeckt',
    'Gerard Kuiper · Name aus „Der Sturm“ — Prosperos Tochter', 'Miranda', NULL, 'HIST');
  ura_api.put_event(2439150.898536, 'SEASON', 'DAY', 'Tagundnachtgleiche 1966',
    'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Süden Frühling — für 21 Jahre.', NULL, NULL, 'CALC');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring 6 entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, '6', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring 5 entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, '5', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring 4 entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, '4', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring α entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, 'α', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring β entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, 'β', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring η entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, 'η', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring γ entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, 'γ', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring δ entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, 'δ', 'HIST');
  ura_api.put_event(2443213.333333, 'DISCOVERY', 'DAY', 'Ring ε entdeckt',
    'Der Stern SAO 158687 flackert vor und nach Uranus neunmal kurz: neun schmale Ringe. Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)', NULL, 'ε', 'HIST');
  ura_api.put_event(2446066.5, 'DISCOVERY', 'YEAR', 'Puck entdeckt',
    'Voyager 2 · Name aus „Ein Sommernachtstraum“', 'Puck', NULL, 'HIST');
  ura_api.put_event(2446337.527017, 'SEASON', 'DAY', 'Sonnenwende 1985',
    'Die Sonne steht über 82,2° Süd, fast über dem Pol. Mittsommer im Süden: seit 21 Jahren Tag am Südpol, am anderen Pol Nacht.', NULL, NULL, 'CALC');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Cordelia entdeckt',
    'Voyager 2 · Name aus „König Lear“', 'Cordelia', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Ophelia entdeckt',
    'Voyager 2 · Name aus „Hamlet“', 'Ophelia', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Bianca entdeckt',
    'Voyager 2 · Name aus „Der Widerspenstigen Zähmung“', 'Bianca', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Cressida entdeckt',
    'Voyager 2 · Name aus „Troilus und Cressida“', 'Cressida', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Desdemona entdeckt',
    'Voyager 2 · Name aus „Othello“', 'Desdemona', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Juliet entdeckt',
    'Voyager 2 · Name aus „Romeo und Julia“', 'Juliet', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Portia entdeckt',
    'Voyager 2 · Name aus „Der Kaufmann von Venedig“', 'Portia', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Rosalind entdeckt',
    'Voyager 2 · Name aus „Wie es euch gefällt“', 'Rosalind', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Belinda entdeckt',
    'Voyager 2 · Name aus Pope, „Der Lockenraub“', 'Belinda', NULL, 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Ring ζ entdeckt',
    'Voyager 2 (als 1986U2R)', NULL, 'ζ', 'HIST');
  ura_api.put_event(2446431.5, 'DISCOVERY', 'YEAR', 'Ring λ entdeckt',
    'Voyager 2', NULL, 'λ', 'HIST');
  ura_api.put_event(2446455.209969, 'FLYBY', 'MINUTE', 'Vorbei an Miranda',
    'Nächster Punkt zu Miranda: 29.099 km. Die Bilder zeigen einen Mond aus Flicken — Kanten, Terrassen und Steilwände bis 20 km Höhe.', 'Miranda', NULL, 'CALC');
  ura_api.put_event(2446455.219019, 'FLYBY', 'MINUTE', 'Durch die Ringebene',
    'Voyager 2 kreuzt die Äquatorebene 115.419 km vom Zentrum — 4,5 Uranusradien, weit außerhalb der dichten Ringe. Die Plasmawellen-Antenne hört Staubkörner auf die Sonde prasseln.', NULL, NULL, 'CALC');
  ura_api.put_event(2446455.249205, 'FLYBY', 'MINUTE', 'Größte Annäherung',
    'Voyager 2 fliegt 81.595 km über der Wolkenoberfläche vorbei (107.154 km vom Zentrum), mit 18,0 km/s relativ zu Uranus.', NULL, NULL, 'CALC');
  ura_api.put_event(2446455.351289, 'FLYBY', 'MINUTE', 'Im Uranusschatten',
    'Für 76 Minuten steht Uranus zwischen Sonde und Sonne. Das Sonnenlicht, das durch die Atmosphäre fällt, verrät deren Aufbau.', NULL, NULL, 'CALC');
  ura_api.put_event(2450449.5, 'DISCOVERY', 'YEAR', 'Caliban entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Caliban', NULL, 'HIST');
  ura_api.put_event(2450449.5, 'DISCOVERY', 'YEAR', 'Sycorax entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Sycorax', NULL, 'HIST');
  ura_api.put_event(2451179.5, 'DISCOVERY', 'YEAR', 'Perdita entdeckt',
    'Voyager 2 · Name aus „Das Wintermärchen“', 'Perdita', NULL, 'HIST');
  ura_api.put_event(2451179.5, 'DISCOVERY', 'YEAR', 'Stephano entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Stephano', NULL, 'HIST');
  ura_api.put_event(2451179.5, 'DISCOVERY', 'YEAR', 'Prospero entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Prospero', NULL, 'HIST');
  ura_api.put_event(2451179.5, 'DISCOVERY', 'YEAR', 'Setebos entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Setebos', NULL, 'HIST');
  ura_api.put_event(2451910.5, 'DISCOVERY', 'YEAR', 'Francisco entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Francisco', NULL, 'HIST');
  ura_api.put_event(2451910.5, 'DISCOVERY', 'YEAR', 'Trinculo entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Trinculo', NULL, 'HIST');
  ura_api.put_event(2451910.5, 'DISCOVERY', 'YEAR', 'Ferdinand entdeckt',
    'Bodenteleskop · Name aus „Der Sturm“', 'Ferdinand', NULL, 'HIST');
  ura_api.put_event(2452640.5, 'DISCOVERY', 'YEAR', 'Cupid entdeckt',
    'Hubble · Name aus „Timon von Athen“', 'Cupid', NULL, 'HIST');
  ura_api.put_event(2452640.5, 'DISCOVERY', 'YEAR', 'Mab entdeckt',
    'Hubble · Name aus „Romeo und Julia“', 'Mab', NULL, 'HIST');
  ura_api.put_event(2452640.5, 'DISCOVERY', 'YEAR', 'Margaret entdeckt',
    'Bodenteleskop · Name aus „Viel Lärm um nichts“', 'Margaret', NULL, 'HIST');
  ura_api.put_event(2452640.5, 'DISCOVERY', 'YEAR', 'Ring ν entdeckt',
    'Showalter, Lissauer (Hubble)', NULL, 'ν', 'HIST');
  ura_api.put_event(2452640.5, 'DISCOVERY', 'YEAR', 'Ring μ entdeckt',
    'Showalter, Lissauer (Hubble)', NULL, 'μ', 'HIST');
  ura_api.put_event(2454440.395245, 'SEASON', 'DAY', 'Tagundnachtgleiche 2007',
    'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Norden Frühling — für 21 Jahre.', NULL, NULL, 'CALC');
  ura_api.put_event(2459945.5, 'DISCOVERY', 'YEAR', 'S/2023 U1 entdeckt',
    'Bodenteleskop · Name aus noch unbenannt', 'S/2023 U1', NULL, 'HIST');
  ura_api.put_event(2460676.5, 'DISCOVERY', 'YEAR', 'S/2025 U1 entdeckt',
    'JWST · Name aus noch unbenannt (JWST)', 'S/2025 U1', NULL, 'HIST');
  ura_api.put_event(2462602.445682, 'SEASON', 'DAY', 'Sonnenwende 2030',
    'Die Sonne steht über 82,2° Nord, fast über dem Pol. Mittsommer im Norden: seit 21 Jahren Tag am Nordpol, am anderen Pol Nacht.', NULL, NULL, 'CALC');
  ura_api.put_event(2469840.964129, 'SEASON', 'DAY', 'Tagundnachtgleiche 2050',
    'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Süden Frühling — für 21 Jahre.', NULL, NULL, 'CALC');
  ura_api.put_event(2477025.576457, 'SEASON', 'DAY', 'Sonnenwende 2069',
    'Die Sonne steht über 82,2° Süd, fast über dem Pol. Mittsommer im Süden: seit 21 Jahren Tag am Südpol, am anderen Pol Nacht.', NULL, NULL, 'CALC');
  ura_api.put_event(2485124.991804, 'SEASON', 'DAY', 'Tagundnachtgleiche 2091',
    'Die Sonne steht über dem Äquator, die Ringe sind von der Kante beleuchtet. Danach wird es im Norden Frühling — für 21 Jahre.', NULL, NULL, 'CALC');
END;
/

COMMIT;
