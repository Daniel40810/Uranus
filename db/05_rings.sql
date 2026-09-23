-- =====================================================================
-- Uranus / Schema DEMO
-- 05 - Ringe aus der gesicherten Wikipedia-Tabelle
-- Erzeugt von tools/UraGen. Bereiche 'a–b' werden zu Minimum und Maximum, '?' zu NULL.
-- =====================================================================

BEGIN
  ura_api.put_ring(p_name => 'ζ', p_a_km => 39600, p_width_min_km => 3500, p_width_max_km => 3500,
    p_ecc => NULL, p_incl_deg => NULL, p_tau_min => 0.0045, p_tau_max => 0.0045, p_dusty => 'J',
    p_year => 1986, p_by => 'Voyager 2 (als 1986U2R)',
    p_source => 'WIKI_RINGS', p_note => 'Radius = Mitte des Bereichs 37 850–41 350 km');
END;
/

BEGIN
  ura_api.put_ring(p_name => '6', p_a_km => 41837, p_width_min_km => 1.6, p_width_max_km => 2.2,
    p_ecc => 0.001, p_incl_deg => 0.062, p_tau_min => 0.18, p_tau_max => 0.25, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => '5', p_a_km => 42234, p_width_min_km => 1.9, p_width_max_km => 4.9,
    p_ecc => 0.0019, p_incl_deg => 0.054, p_tau_min => 0.18, p_tau_max => 0.48, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => '4', p_a_km => 42570, p_width_min_km => 2.4, p_width_max_km => 4.4,
    p_ecc => 0.0011, p_incl_deg => 0.032, p_tau_min => 0.16, p_tau_max => 0.3, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'α', p_a_km => 44718, p_width_min_km => 4.8, p_width_max_km => 10,
    p_ecc => 0.0008, p_incl_deg => 0.015, p_tau_min => 0.3, p_tau_max => 0.7, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'β', p_a_km => 45661, p_width_min_km => 6.1, p_width_max_km => 11.4,
    p_ecc => 0.004, p_incl_deg => 0.005, p_tau_min => 0.2, p_tau_max => 0.35, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'η', p_a_km => 47175, p_width_min_km => 1.9, p_width_max_km => 2.7,
    p_ecc => 0, p_incl_deg => 0.001, p_tau_min => 0.16, p_tau_max => 0.25, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'γ', p_a_km => 47627, p_width_min_km => 3.6, p_width_max_km => 4.7,
    p_ecc => 0.001, p_incl_deg => 0.002, p_tau_min => 0.7, p_tau_max => 0.9, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'δ', p_a_km => 48300, p_width_min_km => 4.1, p_width_max_km => 6.1,
    p_ecc => 0, p_incl_deg => 0.001, p_tau_min => 0.3, p_tau_max => 0.6, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'λ', p_a_km => 50023, p_width_min_km => 1, p_width_max_km => 2,
    p_ecc => 0, p_incl_deg => 0, p_tau_min => 0.1, p_tau_max => 0.2, p_dusty => 'N',
    p_year => 1986, p_by => 'Voyager 2',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'ε', p_a_km => 51149, p_width_min_km => 19.7, p_width_max_km => 96.4,
    p_ecc => 0.0079, p_incl_deg => 0, p_tau_min => 0.5, p_tau_max => 2.5, p_dusty => 'N',
    p_year => 1977, p_by => 'Elliot, Dunham, Mink (Sternbedeckung, Kuiper Airborne Observatory)',
    p_source => 'WIKI_RINGS', p_note => NULL);
END;
/

BEGIN
  ura_api.put_ring(p_name => 'ν', p_a_km => 67300, p_width_min_km => 3800, p_width_max_km => 3800,
    p_ecc => NULL, p_incl_deg => NULL, p_tau_min => 0.0000054, p_tau_max => 0.0000054, p_dusty => 'J',
    p_year => 2003, p_by => 'Showalter, Lissauer (Hubble)',
    p_source => 'WIKI_RINGS', p_note => 'Radius = hellste Stelle laut Anmerkung');
END;
/

BEGIN
  ura_api.put_ring(p_name => 'μ', p_a_km => 97700, p_width_min_km => 17000, p_width_max_km => 17000,
    p_ecc => NULL, p_incl_deg => NULL, p_tau_min => 0.0000085, p_tau_max => 0.0000085, p_dusty => 'J',
    p_year => 2003, p_by => 'Showalter, Lissauer (Hubble)',
    p_source => 'WIKI_RINGS', p_note => 'Radius = hellste Stelle laut Anmerkung');
END;
/

COMMIT;
