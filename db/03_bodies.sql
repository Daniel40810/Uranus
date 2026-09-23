-- =====================================================================
-- Uranus / Schema DEMO
-- 03 - Quellen und Körper: Uranus und seine 29 Monde
-- Erzeugt von tools/UraGen. Fakten aus UranusSystem.facts(), Quellen mit SHA-256 der gesicherten Datei.
-- =====================================================================

BEGIN
  ura_api.put_source(p_code => 'JPL_URA182', p_title => 'JPL Planetary Satellite Mean Elements, Ephemeride URA182',
    p_url => 'https://ssd.jpl.nasa.gov/sats/elem/', p_ephemeris => 'URA182', p_retrieved => DATE '2026-09-23', p_sha256 => '117586215e999efbe00794669d8a7aca826f441e4d42c4e56fd01a8cc74a5a31',
    p_note => 'Jacobson & Park 2025, AJ 169:65. Mittlere Elemente zum Äquator, Epoche 2000-01-01.5 TDB');
END;
/

BEGIN
  ura_api.put_source(p_code => 'JPL_URA184', p_title => 'JPL Planetary Satellite Mean Elements, Ephemeride URA184',
    p_url => 'https://ssd.jpl.nasa.gov/sats/elem/', p_ephemeris => 'URA184', p_retrieved => DATE '2026-09-23', p_sha256 => '117586215e999efbe00794669d8a7aca826f441e4d42c4e56fd01a8cc74a5a31',
    p_note => 'Brozović 2025 (JWST-Update). Laplace-Ebene, Epoche 2025-01-01.0 TDB; passt zu Horizons erst 0,01 Tage später');
END;
/

BEGIN
  ura_api.put_source(p_code => 'JPL_URA117', p_title => 'JPL Planetary Satellite Mean Elements, Ephemeride URA117',
    p_url => 'https://ssd.jpl.nasa.gov/sats/elem/', p_ephemeris => 'URA117', p_retrieved => DATE '2026-09-23', p_sha256 => '117586215e999efbe00794669d8a7aca826f441e4d42c4e56fd01a8cc74a5a31',
    p_note => 'Sheppard et al. 2024. Ekliptik J2000, Epoche 2020-01-01.0 TDB; mittlere Anomalie taugt nicht als Ort');
END;
/

BEGIN
  ura_api.put_source(p_code => 'HZ_FIT', p_title => 'Angleich an JPL Horizons (ura184_merged)',
    p_url => 'https://ssd.jpl.nasa.gov/api/horizons.api', p_ephemeris => 'HORIZONS', p_retrieved => DATE '2026-09-23', p_sha256 => '5407970e29670cc01486c8e7ee27bde6be3553b6593296a0d13cce9afc332ddc',
    p_note => 'Form aus JPL, mittlere Anomalie und Periode an Horizons-Vektoren 1975-2030 angeglichen (tools/UraGen)');
END;
/

BEGIN
  ura_api.put_source(p_code => 'WIKI_RINGS', p_title => 'Wikipedia: Rings of Uranus, Ringtabelle (Revision 1372354849)',
    p_url => 'https://en.wikipedia.org/wiki/Rings_of_Uranus', p_ephemeris => NULL, p_retrieved => DATE '2026-09-23', p_sha256 => '943f9b598014bd8a4c9331df48a180504fc26d495938ff7b56eebda516a15d60',
    p_note => 'Radien Esposito 2002, Breiten Karkoschka 2001/de Pater 2006, e und i Stone 1986/French 1988');
END;
/

BEGIN
  ura_api.put_source(p_code => 'IAU_2015', p_title => 'IAU WGCCRE 2015: Pol und Rotation des Uranus',
    p_url => 'https://doi.org/10.1007/s10569-017-9805-5', p_ephemeris => NULL, p_retrieved => DATE '2026-09-23', p_sha256 => NULL,
    p_note => 'Nordpol RA 257,311°, Dec -15,175°; W = 203,81° - 501,1600928°/Tag');
END;
/

BEGIN
  ura_api.put_body(p_name => 'Uranus', p_naif => 799, p_group => 'PLANET', p_parent => NULL,
    p_radius_km => 25559, p_gm_km3s2 => 5793939, p_albedo => 0.3, p_year => 1781,
    p_by => 'William Herschel', p_namesake => 'Uranos, griechischer Himmelsgott',
    p_pole_ra => 257.311, p_pole_dec => -15.175, p_w0 => 203.81, p_wdot => -501.1600928, p_sort => 0);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Miranda', p_naif => 705, p_group => 'MAJOR', p_parent => 'Uranus',
    p_radius_km => 235.8, p_gm_km3s2 => 4.2716, p_albedo => 0.32, p_year => 1948,
    p_by => 'Gerard Kuiper', p_namesake => '„Der Sturm“ — Prosperos Tochter', p_sort => 1);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Ariel', p_naif => 701, p_group => 'MAJOR', p_parent => 'Uranus',
    p_radius_km => 578.9, p_gm_km3s2 => 83.4955, p_albedo => 0.53, p_year => 1851,
    p_by => 'William Lassell', p_namesake => '„Der Sturm“ und Pope — ein Luftgeist', p_sort => 2);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Umbriel', p_naif => 702, p_group => 'MAJOR', p_parent => 'Uranus',
    p_radius_km => 584.7, p_gm_km3s2 => 85.0973, p_albedo => 0.26, p_year => 1851,
    p_by => 'William Lassell', p_namesake => 'Pope, „Der Lockenraub“ — ein düsterer Geist', p_sort => 3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Titania', p_naif => 703, p_group => 'MAJOR', p_parent => 'Uranus',
    p_radius_km => 788.4, p_gm_km3s2 => 226.9262, p_albedo => 0.35, p_year => 1787,
    p_by => 'William Herschel', p_namesake => '„Ein Sommernachtstraum“ — Elfenkönigin', p_sort => 4);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Oberon', p_naif => 704, p_group => 'MAJOR', p_parent => 'Uranus',
    p_radius_km => 761.4, p_gm_km3s2 => 205.3015, p_albedo => 0.31, p_year => 1787,
    p_by => 'William Herschel', p_namesake => '„Ein Sommernachtstraum“ — Elfenkönig', p_sort => 5);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Cordelia', p_naif => 706, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 20, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„König Lear“', p_sort => 6, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Ophelia', p_naif => 707, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 21, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Hamlet“', p_sort => 7, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'S/2025 U1', p_naif => 75052, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 5, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 2025,
    p_by => 'JWST', p_namesake => 'noch unbenannt (JWST)', p_sort => 8, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Bianca', p_naif => 708, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 26, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Der Widerspenstigen Zähmung“', p_sort => 9, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Cressida', p_naif => 709, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 40, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Troilus und Cressida“', p_sort => 10, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Desdemona', p_naif => 710, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 32, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Othello“', p_sort => 11, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Juliet', p_naif => 711, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 47, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Romeo und Julia“', p_sort => 12, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Portia', p_naif => 712, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 68, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Der Kaufmann von Venedig“', p_sort => 13, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Rosalind', p_naif => 713, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 36, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => '„Wie es euch gefällt“', p_sort => 14, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Cupid', p_naif => 727, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 9, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 2003,
    p_by => 'Hubble', p_namesake => '„Timon von Athen“', p_sort => 15, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Belinda', p_naif => 714, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 45, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1986,
    p_by => 'Voyager 2', p_namesake => 'Pope, „Der Lockenraub“', p_sort => 16, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Perdita', p_naif => 725, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 15, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1999,
    p_by => 'Voyager 2', p_namesake => '„Das Wintermärchen“', p_sort => 17, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Puck', p_naif => 715, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 81, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 1985,
    p_by => 'Voyager 2', p_namesake => '„Ein Sommernachtstraum“', p_sort => 18, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Mab', p_naif => 726, p_group => 'INNER', p_parent => 'Uranus',
    p_radius_km => 12, p_gm_km3s2 => NULL, p_albedo => 0.07, p_year => 2003,
    p_by => 'Hubble', p_namesake => '„Romeo und Julia“', p_sort => 19, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Francisco', p_naif => 722, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 11, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 2001,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 20, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Caliban', p_naif => 716, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 36, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 1997,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 21, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Stephano', p_naif => 720, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 16, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 1999,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 22, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'S/2023 U1', p_naif => 75051, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 4, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 2023,
    p_by => 'Bodenteleskop', p_namesake => 'noch unbenannt', p_sort => 23, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Trinculo', p_naif => 721, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 9, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 2001,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 24, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Sycorax', p_naif => 717, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 75, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 1997,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 25, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Margaret', p_naif => 723, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 10, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 2003,
    p_by => 'Bodenteleskop', p_namesake => '„Viel Lärm um nichts“', p_sort => 26, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Prospero', p_naif => 718, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 25, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 1999,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 27, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Setebos', p_naif => 719, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 24, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 1999,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 28, p_density => 1.3);
END;
/

BEGIN
  ura_api.put_body(p_name => 'Ferdinand', p_naif => 724, p_group => 'IRREGULAR', p_parent => 'Uranus',
    p_radius_km => 10, p_gm_km3s2 => NULL, p_albedo => 0.05, p_year => 2001,
    p_by => 'Bodenteleskop', p_namesake => '„Der Sturm“', p_sort => 29, p_density => 1.3);
END;
/

COMMIT;
