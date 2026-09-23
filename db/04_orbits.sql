-- =====================================================================
-- Uranus / Schema DEMO
-- 04 - Bahnen: JPL-Mittelelemente wie veröffentlicht und an Horizons angeglichen
-- Erzeugt von tools/UraGen aus db/quellen. Nicht von Hand ändern — neu erzeugen.
-- =====================================================================

BEGIN
  ura_api.put_orbit(p_body => 'Miranda', p_source => 'JPL_URA182', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 129846, p_ecc => 0.001,
    p_incl_deg => 4.4, p_node_deg => 100.9, p_argp_deg => 154.8, p_mean_anom_deg => 73,
    p_period_days => 1.413479, p_apsis_period_yr => 8.939, p_node_period_yr => 17.787,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 1.817, p_fit_mean_deg => 0.937,
    p_note => 'wie veröffentlicht (Epoche 2000-01-01.5)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Miranda', p_source => 'HZ_FIT', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 129846, p_ecc => 0.001,
    p_incl_deg => 4.4, p_node_deg => 100.9, p_argp_deg => 154.8, p_mean_anom_deg => 72.393,
    p_period_days => 1.41347903, p_apsis_period_yr => 8.939, p_node_period_yr => 17.787,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 2.289, p_fit_mean_deg => 0.63,
    p_note => 'Form JPL URA182; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Ariel', p_source => 'JPL_URA182', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 190929, p_ecc => 0.001,
    p_incl_deg => 0, p_node_deg => 0, p_argp_deg => 9.6, p_mean_anom_deg => 193.5,
    p_period_days => 2.520379, p_apsis_period_yr => 28.901, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 0.406, p_fit_mean_deg => 0.131,
    p_note => 'wie veröffentlicht (Epoche 2000-01-01.5)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Ariel', p_source => 'HZ_FIT', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 190929, p_ecc => 0.001,
    p_incl_deg => 0, p_node_deg => 0, p_argp_deg => 9.6, p_mean_anom_deg => 193.6066,
    p_period_days => 2.52037922, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 0.193, p_fit_mean_deg => 0.087,
    p_note => 'Form JPL URA182; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Umbriel', p_source => 'JPL_URA182', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 265986, p_ecc => 0.004,
    p_incl_deg => 0.1, p_node_deg => 174.8, p_argp_deg => 183.4, p_mean_anom_deg => 253,
    p_period_days => 4.144177, p_apsis_period_yr => 64.126, p_node_period_yr => 129.745,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 0.26, p_fit_mean_deg => 0.095,
    p_note => 'wie veröffentlicht (Epoche 2000-01-01.5)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Umbriel', p_source => 'HZ_FIT', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 265986, p_ecc => 0.004,
    p_incl_deg => 0.1, p_node_deg => 174.8, p_argp_deg => 183.4, p_mean_anom_deg => 253.0601,
    p_period_days => 4.14417693, p_apsis_period_yr => 64.126, p_node_period_yr => 129.745,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 0.205, p_fit_mean_deg => 0.083,
    p_note => 'Form JPL URA182; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Titania', p_source => 'JPL_URA182', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 436298, p_ecc => 0.002,
    p_incl_deg => 0.1, p_node_deg => 29.5, p_argp_deg => 184, p_mean_anom_deg => 68.1,
    p_period_days => 8.705869, p_apsis_period_yr => 579.928, p_node_period_yr => 1644.649,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 0.216, p_fit_mean_deg => 0.079,
    p_note => 'wie veröffentlicht (Epoche 2000-01-01.5)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Titania', p_source => 'HZ_FIT', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 436298, p_ecc => 0.002,
    p_incl_deg => 0.1, p_node_deg => 29.5, p_argp_deg => 184, p_mean_anom_deg => 68.0791,
    p_period_days => 8.70586853, p_apsis_period_yr => 579.928, p_node_period_yr => 1644.649,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 0.212, p_fit_mean_deg => 0.081,
    p_note => 'Form JPL URA182; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Oberon', p_source => 'JPL_URA182', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 583511, p_ecc => 0.002,
    p_incl_deg => 0.1, p_node_deg => 76.8, p_argp_deg => 132.2, p_mean_anom_deg => 143.6,
    p_period_days => 13.463237, p_apsis_period_yr => 158.604, p_node_period_yr => 192.798,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 0.211, p_fit_mean_deg => 0.124,
    p_note => 'wie veröffentlicht (Epoche 2000-01-01.5)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Oberon', p_source => 'HZ_FIT', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 583511, p_ecc => 0.002,
    p_incl_deg => 0.1, p_node_deg => 76.8, p_argp_deg => 132.2, p_mean_anom_deg => 143.5811,
    p_period_days => 13.46323653, p_apsis_period_yr => 158.604, p_node_period_yr => 192.798,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 0.228, p_fit_mean_deg => 0.122,
    p_note => 'Form JPL URA182; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Cordelia', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 49755, p_ecc => 0,
    p_incl_deg => 0.2, p_node_deg => 1.1, p_argp_deg => 0, p_mean_anom_deg => 287.4,
    p_period_days => 0.3347, p_apsis_period_yr => NULL, p_node_period_yr => 0.6,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 161.101, p_fit_mean_deg => 50.787,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Cordelia', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 49755, p_ecc => 0,
    p_incl_deg => 0.2, p_node_deg => 1.1, p_argp_deg => 0, p_mean_anom_deg => 276.6999,
    p_period_days => 0.33503619, p_apsis_period_yr => NULL, p_node_period_yr => 0.6,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 0.316, p_fit_mean_deg => 0.098,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Ophelia', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 53765, p_ecc => 0.011,
    p_incl_deg => 0.2, p_node_deg => 151.6, p_argp_deg => 19.3, p_mean_anom_deg => 213.4,
    p_period_days => 0.3764, p_apsis_period_yr => 0.4, p_node_period_yr => 0.8,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 160.42, p_fit_mean_deg => 41.588,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Ophelia', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 53765, p_ecc => 0.011,
    p_incl_deg => 0.2, p_node_deg => 151.6, p_argp_deg => 19.3, p_mean_anom_deg => 203.7072,
    p_period_days => 0.37638023, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 2.313, p_fit_mean_deg => 0.605,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'S/2025 U1', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 57844, p_ecc => 0.039,
    p_incl_deg => 4, p_node_deg => 70.8, p_argp_deg => 313.9, p_mean_anom_deg => 275.6,
    p_period_days => 0.4201, p_apsis_period_yr => 0.5, p_node_period_yr => 1,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'J',
    p_fit_max_deg => NULL, p_fit_mean_deg => NULL,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0); kein Horizons-Abgleich möglich');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Bianca', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 59170, p_ecc => 0.006,
    p_incl_deg => 2.3, p_node_deg => 272.5, p_argp_deg => 328, p_mean_anom_deg => 109.1,
    p_period_days => 0.4347, p_apsis_period_yr => 0.5, p_node_period_yr => 1.1,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 138.641, p_fit_mean_deg => 45.245,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Bianca', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 59170, p_ecc => 0.006,
    p_incl_deg => 2.3, p_node_deg => 272.5, p_argp_deg => 328, p_mean_anom_deg => 100.6779,
    p_period_days => 0.43457954, p_apsis_period_yr => 0.5, p_node_period_yr => 1.1,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 3.062, p_fit_mean_deg => 1.075,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Cressida', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 61770, p_ecc => 0.004,
    p_incl_deg => 1.8, p_node_deg => 308.2, p_argp_deg => 87.9, p_mean_anom_deg => 0.5,
    p_period_days => 0.4639, p_apsis_period_yr => 0.6, p_node_period_yr => 1.2,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 175.471, p_fit_mean_deg => 57.576,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Cressida', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 61770, p_ecc => 0.004,
    p_incl_deg => 1.8, p_node_deg => 308.2, p_argp_deg => 87.9, p_mean_anom_deg => 352.7129,
    p_period_days => 0.46356976, p_apsis_period_yr => 0.6, p_node_period_yr => 1.2,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 2.358, p_fit_mean_deg => 0.591,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Desdemona', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 62663, p_ecc => 0.007,
    p_incl_deg => 3.1, p_node_deg => 283.9, p_argp_deg => 137, p_mean_anom_deg => 230,
    p_period_days => 0.4736, p_apsis_period_yr => 0.6, p_node_period_yr => 1.3,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 157.034, p_fit_mean_deg => 48.735,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Desdemona', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 62663, p_ecc => 0.007,
    p_incl_deg => 3.1, p_node_deg => 283.9, p_argp_deg => 137, p_mean_anom_deg => 222.3589,
    p_period_days => 0.4736486, p_apsis_period_yr => 0.6, p_node_period_yr => 1.3,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 5.447, p_fit_mean_deg => 1.129,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Juliet', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 64362, p_ecc => 0.006,
    p_incl_deg => 3, p_node_deg => 141, p_argp_deg => 274.9, p_mean_anom_deg => 319.8,
    p_period_days => 0.4931, p_apsis_period_yr => 0.7, p_node_period_yr => 1.4,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 152.65, p_fit_mean_deg => 40.211,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Juliet', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 64362, p_ecc => 0.006,
    p_incl_deg => 3, p_node_deg => 141, p_argp_deg => 274.9, p_mean_anom_deg => 312.5084,
    p_period_days => 0.49306695, p_apsis_period_yr => 0.7, p_node_period_yr => 1.4,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 3.97, p_fit_mean_deg => 0.686,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Portia', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 66101, p_ecc => 0.004,
    p_incl_deg => 2.7, p_node_deg => 146.7, p_argp_deg => 31.6, p_mean_anom_deg => 310.1,
    p_period_days => 0.5132, p_apsis_period_yr => 0.8, p_node_period_yr => 1.6,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 177.282, p_fit_mean_deg => 34.756,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Portia', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 66101, p_ecc => 0.004,
    p_incl_deg => 2.7, p_node_deg => 146.7, p_argp_deg => 31.6, p_mean_anom_deg => 303.1403,
    p_period_days => 0.51319128, p_apsis_period_yr => 0.8, p_node_period_yr => 1.6,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 5.326, p_fit_mean_deg => 1.118,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Rosalind', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 69930, p_ecc => 0.003,
    p_incl_deg => 1.7, p_node_deg => 330, p_argp_deg => 231.8, p_mean_anom_deg => 287.7,
    p_period_days => 0.5583, p_apsis_period_yr => 0.9, p_node_period_yr => 1.9,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 178.266, p_fit_mean_deg => 65.303,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Rosalind', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 69930, p_ecc => 0.003,
    p_incl_deg => 1.7, p_node_deg => 330, p_argp_deg => 231.8, p_mean_anom_deg => 281.3145,
    p_period_days => 0.55846348, p_apsis_period_yr => 0.9, p_node_period_yr => 1.9,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 1.245, p_fit_mean_deg => 0.3,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Cupid', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 74396, p_ecc => 0.007,
    p_incl_deg => 2, p_node_deg => 31.1, p_argp_deg => 168, p_mean_anom_deg => 3.3,
    p_period_days => 0.6125, p_apsis_period_yr => 1.2, p_node_period_yr => 2.3,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 178.427, p_fit_mean_deg => 62.38,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Cupid', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 74396, p_ecc => 0.007,
    p_incl_deg => 2, p_node_deg => 31.1, p_argp_deg => 168, p_mean_anom_deg => 357.5045,
    p_period_days => 0.61283216, p_apsis_period_yr => 1.2, p_node_period_yr => 2.3,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 2.485, p_fit_mean_deg => 0.576,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Belinda', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 75258, p_ecc => 0.002,
    p_incl_deg => 1.4, p_node_deg => 96.2, p_argp_deg => 58.6, p_mean_anom_deg => 226.4,
    p_period_days => 0.6236, p_apsis_period_yr => 1.2, p_node_period_yr => 2.4,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 158.577, p_fit_mean_deg => 56.268,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Belinda', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 75258, p_ecc => 0.002,
    p_incl_deg => 1.4, p_node_deg => 96.2, p_argp_deg => 58.6, p_mean_anom_deg => 220.6339,
    p_period_days => 0.62352968, p_apsis_period_yr => 1.2, p_node_period_yr => 2.4,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 1.938, p_fit_mean_deg => 0.314,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Perdita', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 76418, p_ecc => 0.005,
    p_incl_deg => 1.6, p_node_deg => 270.4, p_argp_deg => 296.9, p_mean_anom_deg => 168,
    p_period_days => 0.6382, p_apsis_period_yr => 1.3, p_node_period_yr => 2.6,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 176.391, p_fit_mean_deg => 57.355,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Perdita', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 76418, p_ecc => 0.005,
    p_incl_deg => 1.6, p_node_deg => 270.4, p_argp_deg => 296.9, p_mean_anom_deg => 162.3413,
    p_period_days => 0.63800596, p_apsis_period_yr => 1.3, p_node_period_yr => 2.6,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 1.631, p_fit_mean_deg => 0.244,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Puck', p_source => 'JPL_URA182', p_ephemeris => 'URA182',
    p_frame => 'EQUATORIAL', p_epoch_jd => 2451545, p_a_km => 86004, p_ecc => 0,
    p_incl_deg => 0.3, p_node_deg => 216.1, p_argp_deg => 0, p_mean_anom_deg => 50.1,
    p_period_days => 0.761833, p_apsis_period_yr => 2.226, p_node_period_yr => 4.454,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 4.712, p_fit_mean_deg => 1.866,
    p_note => 'wie veröffentlicht (Epoche 2000-01-01.5)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Puck', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 86007, p_ecc => 0.009,
    p_incl_deg => 1.1, p_node_deg => 111.2, p_argp_deg => 337, p_mean_anom_deg => 264.1,
    p_period_days => 0.7618, p_apsis_period_yr => 1.9, p_node_period_yr => 3.9,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 178.651, p_fit_mean_deg => 41.955,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Puck', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 86007, p_ecc => 0.009,
    p_incl_deg => 1.1, p_node_deg => 111.2, p_argp_deg => 337, p_mean_anom_deg => 259.3244,
    p_period_days => 0.76183337, p_apsis_period_yr => 1.9, p_node_period_yr => 3.9,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 1.885, p_fit_mean_deg => 0.278,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Mab', p_source => 'JPL_URA184', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 97737, p_ecc => 0.006,
    p_incl_deg => 1.8, p_node_deg => 307.8, p_argp_deg => 318.6, p_mean_anom_deg => 250.8,
    p_period_days => 0.9229, p_apsis_period_yr => 3.1, p_node_period_yr => 6.1,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 169.429, p_fit_mean_deg => 40.674,
    p_note => 'wie veröffentlicht (Epoche 2025-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Mab', p_source => 'HZ_FIT', p_ephemeris => 'URA184',
    p_frame => 'LAPLACE', p_epoch_jd => 2460676.5, p_a_km => 97737, p_ecc => 0.006,
    p_incl_deg => 1.8, p_node_deg => 307.8, p_argp_deg => 318.6, p_mean_anom_deg => 246.9271,
    p_period_days => 0.92295082, p_apsis_period_yr => 3.1, p_node_period_yr => 6.1,
    p_pole_ra_deg => 77.3, p_pole_dec_deg => 15.2, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 0.932, p_fit_mean_deg => 0.191,
    p_note => 'Form JPL URA184; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Francisco', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 4275700, p_ecc => 0.144,
    p_incl_deg => 146.8, p_node_deg => 101.9, p_argp_deg => 137.6, p_mean_anom_deg => 288.4,
    p_period_days => 267, p_apsis_period_yr => 12473, p_node_period_yr => 14419,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 179.544, p_fit_mean_deg => 148.645,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Francisco', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 4275700, p_ecc => 0.144,
    p_incl_deg => 146.8, p_node_deg => 101.9, p_argp_deg => 137.6, p_mean_anom_deg => 129.473,
    p_period_days => 267.20151264, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 5.822, p_fit_mean_deg => 2.163,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Caliban', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 7167000, p_ecc => 0.2,
    p_incl_deg => 141.4, p_node_deg => 174.9, p_argp_deg => 349.7, p_mean_anom_deg => 241.2,
    p_period_days => 580, p_apsis_period_yr => 8795, p_node_period_yr => 6483,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 28.452, p_fit_mean_deg => 10.509,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Caliban', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 7167000, p_ecc => 0.2,
    p_incl_deg => 141.4, p_node_deg => 174.9, p_argp_deg => 349.7, p_mean_anom_deg => 236.0329,
    p_period_days => 578.61057022, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 22.791, p_fit_mean_deg => 6.975,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Stephano', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 7951400, p_ecc => 0.235,
    p_incl_deg => 143.6, p_node_deg => 193.3, p_argp_deg => 17.1, p_mean_anom_deg => 164.4,
    p_period_days => 677, p_apsis_period_yr => 5469, p_node_period_yr => 5190,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 36.878, p_fit_mean_deg => 26.207,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Stephano', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 7951400, p_ecc => 0.235,
    p_incl_deg => 143.6, p_node_deg => 193.3, p_argp_deg => 17.1, p_mean_anom_deg => 192.4355,
    p_period_days => 676.54590259, p_apsis_period_yr => 5469, p_node_period_yr => 5190,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 16.815, p_fit_mean_deg => 9.045,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'S/2023 U1', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 7976600, p_ecc => 0.25,
    p_incl_deg => 143.9, p_node_deg => 260.2, p_argp_deg => 158.7, p_mean_anom_deg => 101.8,
    p_period_days => 681, p_apsis_period_yr => 5078, p_node_period_yr => 5022,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 174.084, p_fit_mean_deg => 138.401,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'S/2023 U1', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 7976600, p_ecc => 0.25,
    p_incl_deg => 143.9, p_node_deg => 260.2, p_argp_deg => 158.7, p_mean_anom_deg => 260.7658,
    p_period_days => 680.26116703, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 16.464, p_fit_mean_deg => 8.163,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Trinculo', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 8502600, p_ecc => 0.22,
    p_incl_deg => 167.1, p_node_deg => 196.5, p_argp_deg => 162.2, p_mean_anom_deg => 55.6,
    p_period_days => 749, p_apsis_period_yr => 2589, p_node_period_yr => 4265,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 43.306, p_fit_mean_deg => 28.867,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Trinculo', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 8502600, p_ecc => 0.22,
    p_incl_deg => 167.1, p_node_deg => 196.5, p_argp_deg => 162.2, p_mean_anom_deg => 88.1225,
    p_period_days => 748.71294828, p_apsis_period_yr => 2589, p_node_period_yr => 4265,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 3.155, p_fit_mean_deg => 1.222,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Sycorax', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 12193200, p_ecc => 0.52,
    p_incl_deg => 157, p_node_deg => 267.1, p_argp_deg => 25.4, p_mean_anom_deg => 332.1,
    p_period_days => 1286, p_apsis_period_yr => 1394, p_node_period_yr => 1863,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 177.404, p_fit_mean_deg => 108.172,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Sycorax', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 12193200, p_ecc => 0.52,
    p_incl_deg => 157, p_node_deg => 267.1, p_argp_deg => 25.4, p_mean_anom_deg => 146.3541,
    p_period_days => 1289.7316439, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 7.257, p_fit_mean_deg => 3.347,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Margaret', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 14425000, p_ecc => 0.642,
    p_incl_deg => 60.5, p_node_deg => 0.9, p_argp_deg => 90.7, p_mean_anom_deg => 115.9,
    p_period_days => 1655, p_apsis_period_yr => NULL, p_node_period_yr => 1002,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 33.85, p_fit_mean_deg => 18.299,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Margaret', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 14425000, p_ecc => 0.642,
    p_incl_deg => 60.5, p_node_deg => 0.9, p_argp_deg => 90.7, p_mean_anom_deg => 105.1223,
    p_period_days => 1628.70511948, p_apsis_period_yr => NULL, p_node_period_yr => 1002,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 23.353, p_fit_mean_deg => 13.88,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Prospero', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 16221000, p_ecc => 0.441,
    p_incl_deg => 149.4, p_node_deg => 324.5, p_argp_deg => 180.4, p_mean_anom_deg => 197.6,
    p_period_days => 1974, p_apsis_period_yr => 1154, p_node_period_yr => 1369,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 138.56, p_fit_mean_deg => 58.581,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Prospero', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 16221000, p_ecc => 0.441,
    p_incl_deg => 149.4, p_node_deg => 324.5, p_argp_deg => 180.4, p_mean_anom_deg => 130.7168,
    p_period_days => 1984.63141892, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 17.078, p_fit_mean_deg => 5.991,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Setebos', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 17519800, p_ecc => 0.579,
    p_incl_deg => 153.9, p_node_deg => 244.7, p_argp_deg => 356.1, p_mean_anom_deg => 148,
    p_period_days => 2215, p_apsis_period_yr => 48, p_node_period_yr => 48,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 149.631, p_fit_mean_deg => 69.247,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Setebos', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 17519800, p_ecc => 0.579,
    p_incl_deg => 153.9, p_node_deg => 244.7, p_argp_deg => 356.1, p_mean_anom_deg => 290.2055,
    p_period_days => 2207.815394, p_apsis_period_yr => NULL, p_node_period_yr => NULL,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 9.967, p_fit_mean_deg => 6.457,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen; Präzession verworfen (passt ohne besser)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Ferdinand', p_source => 'JPL_URA117', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 20421400, p_ecc => 0.395,
    p_incl_deg => 169.2, p_node_deg => 223.9, p_argp_deg => 166.9, p_mean_anom_deg => 172.3,
    p_period_days => 2788, p_apsis_period_yr => 747, p_node_period_yr => 1001,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'MEAN', p_current => 'N',
    p_fit_max_deg => 141.969, p_fit_mean_deg => 56.676,
    p_note => 'wie veröffentlicht (Epoche 2020-01-01.0)');
END;
/

BEGIN
  ura_api.put_orbit(p_body => 'Ferdinand', p_source => 'HZ_FIT', p_ephemeris => 'URA117',
    p_frame => 'ECLIPTIC', p_epoch_jd => 2458849.5, p_a_km => 20421400, p_ecc => 0.395,
    p_incl_deg => 169.2, p_node_deg => 223.9, p_argp_deg => 166.9, p_mean_anom_deg => 239.9126,
    p_period_days => 2761.72400998, p_apsis_period_yr => 747, p_node_period_yr => 1001,
    p_pole_ra_deg => NULL, p_pole_dec_deg => NULL, p_quality => 'FIT', p_current => 'J',
    p_fit_max_deg => 11.102, p_fit_mean_deg => 2.913,
    p_note => 'Form JPL URA117; mittlere Anomalie und Periode an 19 Horizons-Positionen 1975-2030 angeglichen');
END;
/

COMMIT;
