-- =====================================================================
-- Uranus / Schema DEMO
-- 02 - Typ T_URA_VEC und Package URA_API (einzige Schnittstelle für Java)
--
-- Spec und Body stehen immer zusammen in diesem Skript: CREATE OR REPLACE
-- PACKAGE verwirft den zugehörigen Body (Fallstrick aus FCurvedField).
-- =====================================================================

CREATE OR REPLACE TYPE t_ura_vec AS OBJECT (x NUMBER, y NUMBER, z NUMBER);
/

CREATE OR REPLACE PACKAGE ura_api AS
  c_version CONSTANT VARCHAR2(10) := '1.2';

  -- Lesen ------------------------------------------------------------------
  -- Uranus und die Monde, sortiert (Planet zuerst)
  FUNCTION bodies RETURN SYS_REFCURSOR;
  -- Bahnen: ohne Quelle die gültige je Mond, sonst alle Bahnen dieser Quelle
  FUNCTION orbits (p_source IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR;
  FUNCTION rings RETURN SYS_REFCURSOR;
  -- Ort eines Mondes in Weltkoordinaten (km, Uranusmitte) zum Zeitpunkt p_jd_tdb.
  -- Welt: y = IAU-Nordpol, x = aufsteigender Knoten des Äquators auf dem ICRF-Äquator.
  -- Dieselbe Rechnung wie OrbitElements.position in Java; der Durchstich prüft beide gegeneinander.
  FUNCTION position_at (p_body   IN VARCHAR2,
                        p_jd_tdb IN NUMBER,
                        p_source IN VARCHAR2 DEFAULT NULL) RETURN t_ura_vec;
  -- Einzeiler zum Stand
  FUNCTION info RETURN VARCHAR2;

  -- Geschichten (Phase 6) -------------------------------------------------
  -- Ereignisse nach Zeit; ohne Art alle
  FUNCTION events (p_kind IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR;
  FUNCTION crafts RETURN SYS_REFCURSOR;
  -- Horizons-Vektoren einer Sonde, wie geliefert (ICRF, TDB)
  FUNCTION craft_states (p_craft IN VARCHAR2) RETURN SYS_REFCURSOR;
  FUNCTION tours RETURN SYS_REFCURSOR;
  -- Stationen einer Fahrt, ohne Code alle (nach Fahrt und Reihenfolge)
  FUNCTION tour_steps (p_code IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR;

  -- Spielwiese (Phase 7) --------------------------------------------------
  -- Szenarien: eingebaute zuerst, dann eigene in der Reihenfolge des Speicherns
  FUNCTION scenarios RETURN SYS_REFCURSOR;
  -- Körper eines Szenarios, ohne Code alle (nach Szenario und Reihenfolge)
  FUNCTION scenario_bodies (p_code IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR;
  -- Energie in Joule, uranuszentriert mit Punktmassen — dieselbe Formel wie Scenario.energyJ in Java
  FUNCTION energy (p_code IN VARCHAR2) RETURN NUMBER;
  -- Schreiben (auch aus der App): legt an oder ersetzt, die Körper werden geleert und neu geschrieben.
  -- Ein eingebautes Szenario lässt sich nicht als eigenes überschreiben (ORA-20005).
  PROCEDURE put_scenario (p_code IN VARCHAR2, p_title IN VARCHAR2, p_description IN VARCHAR2,
                          p_start_jd IN NUMBER, p_rate IN NUMBER, p_origin IN VARCHAR2,
                          p_compare IN VARCHAR2, p_note IN VARCHAR2 DEFAULT NULL);
  PROCEDURE put_scenario_body (p_code IN VARCHAR2, p_seq IN NUMBER, p_name IN VARCHAR2, p_role IN VARCHAR2,
                               p_mass_kg IN NUMBER, p_estimated IN VARCHAR2, p_radius_km IN NUMBER,
                               p_x IN NUMBER, p_y IN NUMBER, p_z IN NUMBER,
                               p_vx IN NUMBER, p_vy IN NUMBER, p_vz IN NUMBER);
  -- Löscht nur eigene Szenarien; eingebaute: ORA-20005, unbekannte: ORA-20001
  PROCEDURE drop_scenario (p_code IN VARCHAR2);

  -- Laden (nur für die Einrichtungsskripte) ---------------------------------
  PROCEDURE put_source (p_code IN VARCHAR2, p_title IN VARCHAR2, p_url IN VARCHAR2,
                        p_ephemeris IN VARCHAR2, p_retrieved IN DATE, p_sha256 IN VARCHAR2,
                        p_note IN VARCHAR2);
  PROCEDURE put_body (p_name IN VARCHAR2, p_naif IN NUMBER, p_group IN VARCHAR2, p_parent IN VARCHAR2,
                      p_radius_km IN NUMBER, p_gm_km3s2 IN NUMBER, p_albedo IN NUMBER, p_year IN NUMBER,
                      p_by IN VARCHAR2, p_namesake IN VARCHAR2,
                      p_pole_ra IN NUMBER DEFAULT NULL, p_pole_dec IN NUMBER DEFAULT NULL,
                      p_w0 IN NUMBER DEFAULT NULL, p_wdot IN NUMBER DEFAULT NULL,
                      p_sort IN NUMBER DEFAULT NULL, p_density IN NUMBER DEFAULT NULL);
  -- Legt die Bahn an oder ersetzt sie (Schlüssel: Körper + Quelle).
  -- p_current = 'J' macht sie zur gültigen und nimmt der bisherigen das 'J'.
  PROCEDURE put_orbit (p_body IN VARCHAR2, p_source IN VARCHAR2, p_ephemeris IN VARCHAR2,
                       p_frame IN VARCHAR2, p_epoch_jd IN NUMBER, p_a_km IN NUMBER, p_ecc IN NUMBER,
                       p_incl_deg IN NUMBER, p_node_deg IN NUMBER, p_argp_deg IN NUMBER,
                       p_mean_anom_deg IN NUMBER, p_period_days IN NUMBER,
                       p_apsis_period_yr IN NUMBER, p_node_period_yr IN NUMBER,
                       p_pole_ra_deg IN NUMBER, p_pole_dec_deg IN NUMBER,
                       p_quality IN VARCHAR2, p_current IN VARCHAR2,
                       p_fit_max_deg IN NUMBER, p_fit_mean_deg IN NUMBER, p_note IN VARCHAR2);
  PROCEDURE put_ring (p_name IN VARCHAR2, p_a_km IN NUMBER, p_width_min_km IN NUMBER, p_width_max_km IN NUMBER,
                      p_ecc IN NUMBER, p_incl_deg IN NUMBER, p_tau_min IN NUMBER, p_tau_max IN NUMBER,
                      p_dusty IN VARCHAR2, p_source IN VARCHAR2, p_note IN VARCHAR2,
                      p_year IN NUMBER DEFAULT NULL, p_by IN VARCHAR2 DEFAULT NULL);
  PROCEDURE put_event (p_jd IN NUMBER, p_kind IN VARCHAR2, p_prec IN VARCHAR2, p_title IN VARCHAR2,
                       p_text IN VARCHAR2, p_body IN VARCHAR2, p_ring IN VARCHAR2, p_source IN VARCHAR2);
  PROCEDURE put_craft (p_name IN VARCHAR2, p_naif IN NUMBER, p_tp_jd IN NUMBER, p_q_km IN NUMBER, p_ecc IN NUMBER,
                       p_incl_deg IN NUMBER, p_node_deg IN NUMBER, p_argp_deg IN NUMBER, p_gm_km3s2 IN NUMBER,
                       p_valid_from_jd IN NUMBER, p_valid_to_jd IN NUMBER, p_fit_max_km IN NUMBER,
                       p_source IN VARCHAR2, p_note IN VARCHAR2);
  PROCEDURE put_craft_state (p_craft IN VARCHAR2, p_jd IN NUMBER, p_x IN NUMBER, p_y IN NUMBER, p_z IN NUMBER,
                             p_vx IN NUMBER, p_vy IN NUMBER, p_vz IN NUMBER);
  -- Legt die Fahrt an oder ersetzt sie; ihre Stationen werden dabei geleert und neu geschrieben
  PROCEDURE put_tour (p_code IN VARCHAR2, p_title IN VARCHAR2, p_overlay IN VARCHAR2, p_mode IN VARCHAR2,
                      p_sort IN NUMBER, p_description IN VARCHAR2);
  PROCEDURE put_tour_step (p_code IN VARCHAR2, p_seq IN NUMBER, p_at_s IN NUMBER, p_jd IN NUMBER,
                           p_yaw IN NUMBER, p_pitch IN NUMBER, p_dist IN NUMBER, p_target IN VARCHAR2,
                           p_layers IN VARCHAR2, p_title IN VARCHAR2, p_caption IN VARCHAR2);
END ura_api;
/

CREATE OR REPLACE PACKAGE BODY ura_api AS

  TYPE t_v IS RECORD (x BINARY_DOUBLE, y BINARY_DOUBLE, z BINARY_DOUBLE);

  c_pi   CONSTANT BINARY_DOUBLE := ACOS(-1d);
  c_deg  CONSTANT BINARY_DOUBLE := ACOS(-1d) / 180d;
  -- Schiefe der Ekliptik J2000 (wie Ephemeris.OBLIQUITY_ECLIPTIC)
  c_eps  CONSTANT BINARY_DOUBLE := 23.43928d * ACOS(-1d) / 180d;

  -- Weltbasis, einmal je Sitzung aus der Uranus-Zeile gebaut
  g_ready BOOLEAN := FALSE;
  g_pole  t_v;
  g_xw    t_v;
  g_yw    t_v;
  g_zw    t_v;

  -- ------------------------------------------------------------ Vektoren

  FUNCTION v (p_x BINARY_DOUBLE, p_y BINARY_DOUBLE, p_z BINARY_DOUBLE) RETURN t_v IS
    r t_v;
  BEGIN
    r.x := p_x; r.y := p_y; r.z := p_z;
    RETURN r;
  END v;

  FUNCTION vradec (p_ra BINARY_DOUBLE, p_dec BINARY_DOUBLE) RETURN t_v IS
  BEGIN
    RETURN v(COS(p_dec * c_deg) * COS(p_ra * c_deg),
             COS(p_dec * c_deg) * SIN(p_ra * c_deg),
             SIN(p_dec * c_deg));
  END vradec;

  FUNCTION vdot (a t_v, b t_v) RETURN BINARY_DOUBLE IS
  BEGIN
    RETURN a.x * b.x + a.y * b.y + a.z * b.z;
  END vdot;

  FUNCTION vcross (a t_v, b t_v) RETURN t_v IS
  BEGIN
    RETURN v(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
  END vcross;

  FUNCTION vunit (a t_v) RETURN t_v IS
    l BINARY_DOUBLE := SQRT(a.x * a.x + a.y * a.y + a.z * a.z);
  BEGIN
    RETURN v(a.x / l, a.y / l, a.z / l);
  END vunit;

  FUNCTION vneg (a t_v) RETURN t_v IS
  BEGIN
    RETURN v(-a.x, -a.y, -a.z);
  END vneg;

  -- Ekliptik J2000 -> ICRF (Drehung um x)
  FUNCTION ecl (p_x BINARY_DOUBLE, p_y BINARY_DOUBLE, p_z BINARY_DOUBLE) RETURN t_v IS
  BEGIN
    RETURN v(p_x, p_y * COS(c_eps) - p_z * SIN(c_eps), p_y * SIN(c_eps) + p_z * COS(c_eps));
  END ecl;

  PROCEDURE ensure_world IS
    v_ra  NUMBER;
    v_dec NUMBER;
  BEGIN
    IF g_ready THEN
      RETURN;
    END IF;
    SELECT pole_ra_deg, pole_dec_deg INTO v_ra, v_dec
      FROM ura_body WHERE body_group = 'PLANET';
    g_pole := vradec(v_ra, v_dec);
    g_xw := vunit(vcross(v(0d, 0d, 1d), g_pole));
    g_yw := g_pole;
    g_zw := vcross(g_xw, g_yw);
    g_ready := TRUE;
  END ensure_world;

  -- ------------------------------------------------------------ Lesen

  FUNCTION bodies RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT b.name, b.naif_id, b.body_group, p.name AS parent, b.radius_km, b.gm_km3s2, b.albedo,
             b.discovered_year, b.discoverer, b.namesake,
             b.pole_ra_deg, b.pole_dec_deg, b.w0_deg, b.wdot_deg_day
        FROM ura_body b
        LEFT JOIN ura_body p ON p.body_id = b.parent_id
       ORDER BY b.sort_no, b.name;
    RETURN c;
  END bodies;

  FUNCTION orbits (p_source IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT b.name, o.quality, s.code AS source_code, o.ephemeris, o.frame, o.epoch_jd,
             o.a_km, o.ecc, o.incl_deg, o.node_deg, o.argp_deg, o.mean_anom_deg, o.period_days,
             o.apsis_period_yr, o.node_period_yr, o.pole_ra_deg, o.pole_dec_deg,
             o.fit_max_deg, o.fit_mean_deg, o.is_current
        FROM ura_orbit o
        JOIN ura_body b   ON b.body_id = o.body_id
        JOIN ura_source s ON s.source_id = o.source_id
       WHERE (p_source IS NULL AND o.is_current = 'J')
          OR s.code = p_source
       ORDER BY b.sort_no, b.name;
    RETURN c;
  END orbits;

  FUNCTION rings RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT name, a_km, width_min_km, width_max_km, ecc, incl_deg, tau_min, tau_max, dusty,
             discovered_year, discoverer
        FROM ura_ring
       ORDER BY a_km;
    RETURN c;
  END rings;

  FUNCTION position_at (p_body   IN VARCHAR2,
                        p_jd_tdb IN NUMBER,
                        p_source IN VARCHAR2 DEFAULT NULL) RETURN t_ura_vec IS
    v_frame  ura_orbit.frame%TYPE;
    v_ep     BINARY_DOUBLE;
    v_a      BINARY_DOUBLE;
    v_e      BINARY_DOUBLE;
    v_i      BINARY_DOUBLE;
    v_node   BINARY_DOUBLE;
    v_argp   BINARY_DOUBLE;
    v_m0     BINARY_DOUBLE;
    v_p      BINARY_DOUBLE;
    v_pa     BINARY_DOUBLE;
    v_pn     BINARY_DOUBLE;
    v_ra     BINARY_DOUBLE;
    v_dec    BINARY_DOUBLE;
    wdot     BINARY_DOUBLE := 0;
    ndot     BINARY_DOUBLE := 0;
    mdot     BINARY_DOUBLE;
    dt       BINARY_DOUBLE;
    m        BINARY_DOUBLE;
    w        BINARY_DOUBLE;
    om       BINARY_DOUBLE;
    inc      BINARY_DOUBLE;
    ea       BINARY_DOUBLE;
    d        BINARY_DOUBLE;
    xp       BINARY_DOUBLE;
    yp       BINARY_DOUBLE;
    x        BINARY_DOUBLE;
    y        BINARY_DOUBLE;
    z        BINARY_DOUBLE;
    b1       t_v;
    b2       t_v;
    b3       t_v;
    q        t_v;
    icrf     t_v;
  BEGIN
    ensure_world;
    -- nur die gebrauchten Spalten, kein SELECT * (Fallstrick virtuelle Spalten)
    SELECT o.frame, o.epoch_jd, o.a_km, o.ecc, o.incl_deg, o.node_deg, o.argp_deg, o.mean_anom_deg,
           o.period_days, NVL(o.apsis_period_yr, 0), NVL(o.node_period_yr, 0), o.pole_ra_deg, o.pole_dec_deg
      INTO v_frame, v_ep, v_a, v_e, v_i, v_node, v_argp, v_m0, v_p, v_pa, v_pn, v_ra, v_dec
      FROM ura_orbit o
      JOIN ura_body b   ON b.body_id = o.body_id
      JOIN ura_source s ON s.source_id = o.source_id
     WHERE b.name = p_body
       AND ((p_source IS NULL AND o.is_current = 'J') OR s.code = p_source);

    -- Präzession: Perizentrum vorwärts, Knoten bei i < 90° rückwärts, darüber vorwärts
    IF v_pa > 0 THEN
      wdot := 360d / (v_pa * 365.25d);
    END IF;
    IF v_pn > 0 THEN
      ndot := 360d / (v_pn * 365.25d);
      IF v_i <= 90 THEN
        ndot := -ndot;
      END IF;
    END IF;
    mdot := 360d / v_p - wdot - ndot;

    dt  := TO_BINARY_DOUBLE(p_jd_tdb) - v_ep;
    m   := MOD(v_m0 + mdot * dt, 360d) * c_deg;
    w   := (v_argp + wdot * dt) * c_deg;
    om  := (v_node + ndot * dt) * c_deg;
    inc := v_i * c_deg;

    -- Kepler-Gleichung per Newton, M auf -pi..pi
    IF m > c_pi THEN
      m := m - 2 * c_pi;
    ELSIF m < -c_pi THEN
      m := m + 2 * c_pi;
    END IF;
    IF v_e < 0.8 THEN
      ea := m + v_e * SIN(m);
    ELSIF m < 0 THEN
      ea := -c_pi;
    ELSE
      ea := c_pi;
    END IF;
    FOR k IN 1 .. 40 LOOP
      d  := (ea - v_e * SIN(ea) - m) / (1 - v_e * COS(ea));
      ea := ea - d;
      EXIT WHEN ABS(d) < 1e-14d;
    END LOOP;
    xp := v_a * (COS(ea) - v_e);
    yp := v_a * SQRT(1 - v_e * v_e) * SIN(ea);
    x := (COS(w) * COS(om) - SIN(w) * SIN(om) * COS(inc)) * xp + (-SIN(w) * COS(om) - COS(w) * SIN(om) * COS(inc)) * yp;
    y := (COS(w) * SIN(om) + SIN(w) * COS(om) * COS(inc)) * xp + (-SIN(w) * SIN(om) + COS(w) * COS(om) * COS(inc)) * yp;
    z := (SIN(w) * SIN(inc)) * xp + (COS(w) * SIN(inc)) * yp;

    -- Basis der Bezugsebene im ICRF
    IF v_frame = 'ECLIPTIC' THEN
      b1 := ecl(1d, 0d, 0d);
      b2 := ecl(0d, 1d, 0d);
      b3 := ecl(0d, 0d, 1d);
    ELSE
      IF v_frame = 'LAPLACE' THEN
        q := vradec(v_ra, v_dec);
      ELSE
        q := vneg(g_pole);                 -- Äquator: Bahnpol gegen den IAU-Nordpol
      END IF;
      b1 := vunit(vcross(v(0d, 0d, 1d), q));
      b2 := vcross(q, b1);
      b3 := q;
    END IF;
    icrf := v(x * b1.x + y * b2.x + z * b3.x,
              x * b1.y + y * b2.y + z * b3.y,
              x * b1.z + y * b2.z + z * b3.z);
    RETURN t_ura_vec(vdot(icrf, g_xw), vdot(icrf, g_yw), vdot(icrf, g_zw));
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20002, 'Keine Bahn für ' || p_body
        || CASE WHEN p_source IS NULL THEN ' (gültige Bahn)' ELSE ' aus Quelle ' || p_source END);
  END position_at;

  FUNCTION info RETURN VARCHAR2 IS
    v_bodies NUMBER;
    v_cur    NUMBER;
    v_fit    NUMBER;
    v_rows   NUMBER;
    v_rings  NUMBER;
    v_events NUMBER;
    v_tours  NUMBER;
    v_scen   NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_bodies FROM ura_body;
    SELECT COUNT(*), NVL(SUM(CASE WHEN is_current = 'J' THEN 1 ELSE 0 END), 0),
           NVL(SUM(CASE WHEN is_current = 'J' AND quality = 'FIT' THEN 1 ELSE 0 END), 0)
      INTO v_rows, v_cur, v_fit FROM ura_orbit;
    SELECT COUNT(*) INTO v_rings FROM ura_ring;
    SELECT COUNT(*) INTO v_events FROM ura_event;
    SELECT COUNT(*) INTO v_tours FROM ura_tour;
    SELECT COUNT(*) INTO v_scen FROM ura_scenario;
    RETURN 'URA_API ' || c_version || ' · ' || v_bodies || ' Körper · ' || v_cur || ' gültige Bahnen ('
        || v_fit || ' angeglichen) aus ' || v_rows || ' · ' || v_rings || ' Ringe · '
        || v_events || ' Ereignisse · ' || v_tours || ' Fahrten · ' || v_scen || ' Szenarien';
  END info;

  -- ------------------------------------------------------------ Geschichten

  FUNCTION events (p_kind IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT e.event_jd, e.kind, e.time_prec, e.title, e.event_text, b.name AS body, r.name AS ring, s.code AS source_code
        FROM ura_event e
        LEFT JOIN ura_body b   ON b.body_id = e.body_id
        LEFT JOIN ura_ring r   ON r.ring_id = e.ring_id
        LEFT JOIN ura_source s ON s.source_id = e.source_id
       WHERE p_kind IS NULL OR e.kind = p_kind
       ORDER BY e.event_jd, e.event_id;          -- gleiche Zeit (Titania/Oberon 1787): Reihenfolge wie geladen
    RETURN c;
  END events;

  FUNCTION crafts RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT name, naif_id, tp_jd, q_km, ecc, incl_deg, node_deg, argp_deg, gm_km3s2,
             valid_from_jd, valid_to_jd, fit_max_km, note
        FROM ura_craft
       ORDER BY name;
    RETURN c;
  END crafts;

  FUNCTION craft_states (p_craft IN VARCHAR2) RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT s.jd_tdb, s.x_km, s.y_km, s.z_km, s.vx_kms, s.vy_kms, s.vz_kms
        FROM ura_craft_state s
        JOIN ura_craft k ON k.craft_id = s.craft_id
       WHERE k.name = p_craft
       ORDER BY s.jd_tdb;
    RETURN c;
  END craft_states;

  FUNCTION tours RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT code, title, overlay, time_mode, description
        FROM ura_tour
       ORDER BY sort_no, code;
    RETURN c;
  END tours;

  FUNCTION tour_steps (p_code IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT t.code, s.seq, s.at_s, s.jd_utc, s.yaw_deg, s.pitch_deg, s.dist, s.target, s.layers, s.title, s.caption
        FROM ura_tour_step s
        JOIN ura_tour t ON t.tour_id = s.tour_id
       WHERE p_code IS NULL OR t.code = p_code
       ORDER BY t.sort_no, t.code, s.seq;
    RETURN c;
  END tour_steps;

  -- ------------------------------------------------------------ Laden

  FUNCTION id_of_body (p_name IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT body_id INTO v_id FROM ura_body WHERE name = p_name;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unbekannter Körper: ' || p_name);
  END id_of_body;

  FUNCTION id_of_source (p_code IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT source_id INTO v_id FROM ura_source WHERE code = p_code;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unbekannte Quelle: ' || p_code);
  END id_of_source;

  PROCEDURE put_source (p_code IN VARCHAR2, p_title IN VARCHAR2, p_url IN VARCHAR2,
                        p_ephemeris IN VARCHAR2, p_retrieved IN DATE, p_sha256 IN VARCHAR2,
                        p_note IN VARCHAR2) IS
  BEGIN
    MERGE INTO ura_source t
    USING (SELECT p_code AS code FROM dual) q ON (t.code = q.code)
    WHEN MATCHED THEN UPDATE SET
      t.title = p_title, t.url = p_url, t.ephemeris = p_ephemeris,
      t.retrieved_on = p_retrieved, t.sha256 = p_sha256, t.note = p_note
    WHEN NOT MATCHED THEN INSERT (code, title, url, ephemeris, retrieved_on, sha256, note)
      VALUES (p_code, p_title, p_url, p_ephemeris, p_retrieved, p_sha256, p_note);
  END put_source;

  PROCEDURE put_body (p_name IN VARCHAR2, p_naif IN NUMBER, p_group IN VARCHAR2, p_parent IN VARCHAR2,
                      p_radius_km IN NUMBER, p_gm_km3s2 IN NUMBER, p_albedo IN NUMBER, p_year IN NUMBER,
                      p_by IN VARCHAR2, p_namesake IN VARCHAR2,
                      p_pole_ra IN NUMBER DEFAULT NULL, p_pole_dec IN NUMBER DEFAULT NULL,
                      p_w0 IN NUMBER DEFAULT NULL, p_wdot IN NUMBER DEFAULT NULL,
                      p_sort IN NUMBER DEFAULT NULL, p_density IN NUMBER DEFAULT NULL) IS
    v_parent NUMBER;
  BEGIN
    IF p_parent IS NOT NULL THEN
      v_parent := id_of_body(p_parent);
    END IF;
    MERGE INTO ura_body t
    USING (SELECT p_name AS name FROM dual) q ON (t.name = q.name)
    WHEN MATCHED THEN UPDATE SET
      t.naif_id = p_naif, t.body_group = p_group, t.parent_id = v_parent, t.radius_km = p_radius_km,
      t.gm_km3s2 = p_gm_km3s2, t.albedo = p_albedo, t.discovered_year = p_year, t.discoverer = p_by,
      t.namesake = p_namesake, t.pole_ra_deg = p_pole_ra, t.pole_dec_deg = p_pole_dec,
      t.w0_deg = p_w0, t.wdot_deg_day = p_wdot, t.sort_no = p_sort, t.density_assumed = p_density
    WHEN NOT MATCHED THEN INSERT (name, naif_id, body_group, parent_id, radius_km, gm_km3s2, albedo,
                                  discovered_year, discoverer, namesake, pole_ra_deg, pole_dec_deg,
                                  w0_deg, wdot_deg_day, sort_no, density_assumed)
      VALUES (p_name, p_naif, p_group, v_parent, p_radius_km, p_gm_km3s2, p_albedo, p_year, p_by,
              p_namesake, p_pole_ra, p_pole_dec, p_w0, p_wdot, p_sort, p_density);
    g_ready := FALSE;                     -- der Pol kann sich geändert haben
  END put_body;

  PROCEDURE put_orbit (p_body IN VARCHAR2, p_source IN VARCHAR2, p_ephemeris IN VARCHAR2,
                       p_frame IN VARCHAR2, p_epoch_jd IN NUMBER, p_a_km IN NUMBER, p_ecc IN NUMBER,
                       p_incl_deg IN NUMBER, p_node_deg IN NUMBER, p_argp_deg IN NUMBER,
                       p_mean_anom_deg IN NUMBER, p_period_days IN NUMBER,
                       p_apsis_period_yr IN NUMBER, p_node_period_yr IN NUMBER,
                       p_pole_ra_deg IN NUMBER, p_pole_dec_deg IN NUMBER,
                       p_quality IN VARCHAR2, p_current IN VARCHAR2,
                       p_fit_max_deg IN NUMBER, p_fit_mean_deg IN NUMBER, p_note IN VARCHAR2) IS
    v_body NUMBER := id_of_body(p_body);
    v_src  NUMBER := id_of_source(p_source);
  BEGIN
    -- erst der bisherigen gültigen Bahn das 'J' nehmen, sonst greift der Unique-Index
    IF p_current = 'J' THEN
      UPDATE ura_orbit SET is_current = 'N'
       WHERE body_id = v_body AND source_id <> v_src AND is_current = 'J';
    END IF;
    MERGE INTO ura_orbit t
    USING (SELECT v_body AS body_id, v_src AS source_id FROM dual) q
       ON (t.body_id = q.body_id AND t.source_id = q.source_id)
    WHEN MATCHED THEN UPDATE SET
      t.ephemeris = p_ephemeris, t.frame = p_frame, t.epoch_jd = p_epoch_jd, t.a_km = p_a_km,
      t.ecc = p_ecc, t.incl_deg = p_incl_deg, t.node_deg = p_node_deg, t.argp_deg = p_argp_deg,
      t.mean_anom_deg = p_mean_anom_deg, t.period_days = p_period_days,
      t.apsis_period_yr = p_apsis_period_yr, t.node_period_yr = p_node_period_yr,
      t.pole_ra_deg = p_pole_ra_deg, t.pole_dec_deg = p_pole_dec_deg, t.quality = p_quality,
      t.is_current = NVL(p_current, 'N'), t.fit_max_deg = p_fit_max_deg,
      t.fit_mean_deg = p_fit_mean_deg, t.note = p_note
    WHEN NOT MATCHED THEN INSERT (body_id, source_id, ephemeris, frame, epoch_jd, a_km, ecc, incl_deg,
                                  node_deg, argp_deg, mean_anom_deg, period_days, apsis_period_yr,
                                  node_period_yr, pole_ra_deg, pole_dec_deg, quality, is_current,
                                  fit_max_deg, fit_mean_deg, note)
      VALUES (v_body, v_src, p_ephemeris, p_frame, p_epoch_jd, p_a_km, p_ecc, p_incl_deg, p_node_deg,
              p_argp_deg, p_mean_anom_deg, p_period_days, p_apsis_period_yr, p_node_period_yr,
              p_pole_ra_deg, p_pole_dec_deg, p_quality, NVL(p_current, 'N'), p_fit_max_deg,
              p_fit_mean_deg, p_note);
  END put_orbit;

  PROCEDURE put_ring (p_name IN VARCHAR2, p_a_km IN NUMBER, p_width_min_km IN NUMBER, p_width_max_km IN NUMBER,
                      p_ecc IN NUMBER, p_incl_deg IN NUMBER, p_tau_min IN NUMBER, p_tau_max IN NUMBER,
                      p_dusty IN VARCHAR2, p_source IN VARCHAR2, p_note IN VARCHAR2,
                      p_year IN NUMBER DEFAULT NULL, p_by IN VARCHAR2 DEFAULT NULL) IS
    v_src NUMBER;
  BEGIN
    IF p_source IS NOT NULL THEN
      v_src := id_of_source(p_source);
    END IF;
    MERGE INTO ura_ring t
    USING (SELECT p_name AS name FROM dual) q ON (t.name = q.name)
    WHEN MATCHED THEN UPDATE SET
      t.a_km = p_a_km, t.width_min_km = p_width_min_km, t.width_max_km = p_width_max_km,
      t.ecc = p_ecc, t.incl_deg = p_incl_deg, t.tau_min = p_tau_min, t.tau_max = p_tau_max,
      t.dusty = p_dusty, t.source_id = v_src, t.note = p_note,
      t.discovered_year = p_year, t.discoverer = p_by
    WHEN NOT MATCHED THEN INSERT (name, a_km, width_min_km, width_max_km, ecc, incl_deg, tau_min,
                                  tau_max, dusty, source_id, note, discovered_year, discoverer)
      VALUES (p_name, p_a_km, p_width_min_km, p_width_max_km, p_ecc, p_incl_deg, p_tau_min, p_tau_max,
              p_dusty, v_src, p_note, p_year, p_by);
  END put_ring;

  FUNCTION id_of_ring (p_name IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT ring_id INTO v_id FROM ura_ring WHERE name = p_name;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unbekannter Ring: ' || p_name);
  END id_of_ring;

  FUNCTION id_of_craft (p_name IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT craft_id INTO v_id FROM ura_craft WHERE name = p_name;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unbekannte Sonde: ' || p_name);
  END id_of_craft;

  FUNCTION id_of_tour (p_code IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT tour_id INTO v_id FROM ura_tour WHERE code = p_code;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unbekannte Fahrt: ' || p_code);
  END id_of_tour;

  PROCEDURE put_event (p_jd IN NUMBER, p_kind IN VARCHAR2, p_prec IN VARCHAR2, p_title IN VARCHAR2,
                       p_text IN VARCHAR2, p_body IN VARCHAR2, p_ring IN VARCHAR2, p_source IN VARCHAR2) IS
    v_body NUMBER;
    v_ring NUMBER;
    v_src  NUMBER;
  BEGIN
    IF p_body IS NOT NULL THEN
      v_body := id_of_body(p_body);
    END IF;
    IF p_ring IS NOT NULL THEN
      v_ring := id_of_ring(p_ring);
    END IF;
    IF p_source IS NOT NULL THEN
      v_src := id_of_source(p_source);
    END IF;
    MERGE INTO ura_event t
    USING (SELECT p_title AS title FROM dual) q ON (t.title = q.title)
    WHEN MATCHED THEN UPDATE SET
      t.event_jd = p_jd, t.kind = p_kind, t.time_prec = p_prec, t.event_text = p_text,
      t.body_id = v_body, t.ring_id = v_ring, t.source_id = v_src
    WHEN NOT MATCHED THEN INSERT (event_jd, kind, time_prec, title, event_text, body_id, ring_id, source_id)
      VALUES (p_jd, p_kind, p_prec, p_title, p_text, v_body, v_ring, v_src);
  END put_event;

  PROCEDURE put_craft (p_name IN VARCHAR2, p_naif IN NUMBER, p_tp_jd IN NUMBER, p_q_km IN NUMBER, p_ecc IN NUMBER,
                       p_incl_deg IN NUMBER, p_node_deg IN NUMBER, p_argp_deg IN NUMBER, p_gm_km3s2 IN NUMBER,
                       p_valid_from_jd IN NUMBER, p_valid_to_jd IN NUMBER, p_fit_max_km IN NUMBER,
                       p_source IN VARCHAR2, p_note IN VARCHAR2) IS
    v_src NUMBER;
  BEGIN
    IF p_source IS NOT NULL THEN
      v_src := id_of_source(p_source);
    END IF;
    MERGE INTO ura_craft t
    USING (SELECT p_name AS name FROM dual) q ON (t.name = q.name)
    WHEN MATCHED THEN UPDATE SET
      t.naif_id = p_naif, t.tp_jd = p_tp_jd, t.q_km = p_q_km, t.ecc = p_ecc, t.incl_deg = p_incl_deg,
      t.node_deg = p_node_deg, t.argp_deg = p_argp_deg, t.gm_km3s2 = p_gm_km3s2,
      t.valid_from_jd = p_valid_from_jd, t.valid_to_jd = p_valid_to_jd, t.fit_max_km = p_fit_max_km,
      t.source_id = v_src, t.note = p_note
    WHEN NOT MATCHED THEN INSERT (name, naif_id, tp_jd, q_km, ecc, incl_deg, node_deg, argp_deg, gm_km3s2,
                                  valid_from_jd, valid_to_jd, fit_max_km, source_id, note)
      VALUES (p_name, p_naif, p_tp_jd, p_q_km, p_ecc, p_incl_deg, p_node_deg, p_argp_deg, p_gm_km3s2,
              p_valid_from_jd, p_valid_to_jd, p_fit_max_km, v_src, p_note);
  END put_craft;

  PROCEDURE put_craft_state (p_craft IN VARCHAR2, p_jd IN NUMBER, p_x IN NUMBER, p_y IN NUMBER, p_z IN NUMBER,
                             p_vx IN NUMBER, p_vy IN NUMBER, p_vz IN NUMBER) IS
    v_craft NUMBER := id_of_craft(p_craft);
  BEGIN
    MERGE INTO ura_craft_state t
    USING (SELECT v_craft AS craft_id, p_jd AS jd_tdb FROM dual) q
       ON (t.craft_id = q.craft_id AND t.jd_tdb = q.jd_tdb)
    WHEN MATCHED THEN UPDATE SET
      t.x_km = p_x, t.y_km = p_y, t.z_km = p_z, t.vx_kms = p_vx, t.vy_kms = p_vy, t.vz_kms = p_vz
    WHEN NOT MATCHED THEN INSERT (craft_id, jd_tdb, x_km, y_km, z_km, vx_kms, vy_kms, vz_kms)
      VALUES (v_craft, p_jd, p_x, p_y, p_z, p_vx, p_vy, p_vz);
  END put_craft_state;

  PROCEDURE put_tour (p_code IN VARCHAR2, p_title IN VARCHAR2, p_overlay IN VARCHAR2, p_mode IN VARCHAR2,
                      p_sort IN NUMBER, p_description IN VARCHAR2) IS
    v_tour NUMBER;
  BEGIN
    MERGE INTO ura_tour t
    USING (SELECT p_code AS code FROM dual) q ON (t.code = q.code)
    WHEN MATCHED THEN UPDATE SET
      t.title = p_title, t.overlay = p_overlay, t.time_mode = p_mode, t.sort_no = p_sort,
      t.description = p_description
    WHEN NOT MATCHED THEN INSERT (code, title, overlay, time_mode, sort_no, description)
      VALUES (p_code, p_title, p_overlay, p_mode, p_sort, p_description);
    -- eine Fahrt wird immer ganz geschrieben: alte Stationen fallen
    -- (private Funktionen gehen nicht in SQL: erst in eine Variable)
    v_tour := id_of_tour(p_code);
    DELETE FROM ura_tour_step WHERE tour_id = v_tour;
  END put_tour;

  PROCEDURE put_tour_step (p_code IN VARCHAR2, p_seq IN NUMBER, p_at_s IN NUMBER, p_jd IN NUMBER,
                           p_yaw IN NUMBER, p_pitch IN NUMBER, p_dist IN NUMBER, p_target IN VARCHAR2,
                           p_layers IN VARCHAR2, p_title IN VARCHAR2, p_caption IN VARCHAR2) IS
    v_tour NUMBER := id_of_tour(p_code);
  BEGIN
    INSERT INTO ura_tour_step (tour_id, seq, at_s, jd_utc, yaw_deg, pitch_deg, dist, target, layers, title, caption)
    VALUES (v_tour, p_seq, p_at_s, p_jd, p_yaw, p_pitch, p_dist, p_target, p_layers, p_title, p_caption);
  END put_tour_step;

  -- ------------------------------------------------------------ Spielwiese

  FUNCTION id_of_scenario (p_code IN VARCHAR2) RETURN NUMBER IS
    v_id NUMBER;
  BEGIN
    SELECT scenario_id INTO v_id FROM ura_scenario WHERE code = p_code;
    RETURN v_id;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RAISE_APPLICATION_ERROR(-20001, 'Unbekanntes Szenario: ' || p_code);
  END id_of_scenario;

  FUNCTION scenarios RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT code, title, description, start_jd, rate_days_s, origin, compare_eph, created_on
        FROM ura_scenario
       ORDER BY CASE origin WHEN 'BUILTIN' THEN 0 ELSE 1 END, scenario_id;
    RETURN c;
  END scenarios;

  FUNCTION scenario_bodies (p_code IN VARCHAR2 DEFAULT NULL) RETURN SYS_REFCURSOR IS
    c SYS_REFCURSOR;
  BEGIN
    OPEN c FOR
      SELECT s.code, b.seq, b.name, b.role, b.mass_kg, b.mass_estimated, b.radius_km,
             b.x_km, b.y_km, b.z_km, b.vx_kms, b.vy_kms, b.vz_kms
        FROM ura_scenario_body b
        JOIN ura_scenario s ON s.scenario_id = b.scenario_id
       WHERE p_code IS NULL OR s.code = p_code
       ORDER BY s.scenario_id, b.seq;
    RETURN c;
  END scenario_bodies;

  FUNCTION energy (p_code IN VARCHAR2) RETURN NUMBER IS
    c_g   CONSTANT BINARY_DOUBLE := 6.6743E-20d;         -- km³/(kg s²), wie UranusSystem.G_KM
    c_mu  CONSTANT BINARY_DOUBLE := 8.681E25d;           -- kg, wie UranusSystem.URANUS_MASS_KG
    v_id  NUMBER := id_of_scenario(p_code);
    v_e   BINARY_DOUBLE := 0d;
  BEGIN
    FOR a IN (SELECT seq, mass_kg, x_km, y_km, z_km, vx_kms, vy_kms, vz_kms FROM ura_scenario_body
               WHERE scenario_id = v_id AND role <> 'TEST' AND mass_kg > 0) LOOP
      v_e := v_e + 0.5d * a.mass_kg * (TO_BINARY_DOUBLE(a.vx_kms) * a.vx_kms + TO_BINARY_DOUBLE(a.vy_kms) * a.vy_kms
                                       + TO_BINARY_DOUBLE(a.vz_kms) * a.vz_kms)
                 - c_g * c_mu * a.mass_kg / SQRT(TO_BINARY_DOUBLE(a.x_km) * a.x_km + TO_BINARY_DOUBLE(a.y_km) * a.y_km
                                                 + TO_BINARY_DOUBLE(a.z_km) * a.z_km);
      FOR b IN (SELECT mass_kg, x_km, y_km, z_km FROM ura_scenario_body
                 WHERE scenario_id = v_id AND role <> 'TEST' AND mass_kg > 0 AND seq > a.seq) LOOP
        v_e := v_e - c_g * a.mass_kg * b.mass_kg
               / SQRT(POWER(TO_BINARY_DOUBLE(a.x_km) - b.x_km, 2) + POWER(TO_BINARY_DOUBLE(a.y_km) - b.y_km, 2)
                      + POWER(TO_BINARY_DOUBLE(a.z_km) - b.z_km, 2));
      END LOOP;
    END LOOP;
    RETURN v_e * 1E6d;                                   -- km²/s² · kg → J
  END energy;

  PROCEDURE put_scenario (p_code IN VARCHAR2, p_title IN VARCHAR2, p_description IN VARCHAR2,
                          p_start_jd IN NUMBER, p_rate IN NUMBER, p_origin IN VARCHAR2,
                          p_compare IN VARCHAR2, p_note IN VARCHAR2 DEFAULT NULL) IS
    v_origin VARCHAR2(8);
    v_id     NUMBER;
  BEGIN
    BEGIN
      SELECT origin, scenario_id INTO v_origin, v_id FROM ura_scenario WHERE code = p_code;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN v_origin := NULL;
    END;
    IF v_origin = 'BUILTIN' AND p_origin <> 'BUILTIN' THEN
      RAISE_APPLICATION_ERROR(-20005, 'Eingebautes Szenario kann nicht überschrieben werden: ' || p_code);
    END IF;
    MERGE INTO ura_scenario t
    USING (SELECT p_code AS code FROM dual) q ON (t.code = q.code)
    WHEN MATCHED THEN UPDATE SET
      t.title = p_title, t.description = p_description, t.start_jd = p_start_jd, t.rate_days_s = p_rate,
      t.origin = p_origin, t.compare_eph = p_compare, t.created_on = SYSDATE, t.note = p_note
    WHEN NOT MATCHED THEN INSERT (code, title, description, start_jd, rate_days_s, origin, compare_eph, note)
      VALUES (p_code, p_title, p_description, p_start_jd, p_rate, p_origin, p_compare, p_note);
    v_id := id_of_scenario(p_code);
    DELETE FROM ura_scenario_body WHERE scenario_id = v_id;
  END put_scenario;

  PROCEDURE put_scenario_body (p_code IN VARCHAR2, p_seq IN NUMBER, p_name IN VARCHAR2, p_role IN VARCHAR2,
                               p_mass_kg IN NUMBER, p_estimated IN VARCHAR2, p_radius_km IN NUMBER,
                               p_x IN NUMBER, p_y IN NUMBER, p_z IN NUMBER,
                               p_vx IN NUMBER, p_vy IN NUMBER, p_vz IN NUMBER) IS
    v_id   NUMBER := id_of_scenario(p_code);
    v_body NUMBER;
  BEGIN
    IF p_role <> 'GUEST' THEN
      BEGIN
        SELECT body_id INTO v_body FROM ura_body WHERE name = p_name;
      EXCEPTION
        WHEN NO_DATA_FOUND THEN v_body := NULL;
      END;
    END IF;
    INSERT INTO ura_scenario_body (scenario_id, seq, name, body_id, role, mass_kg, mass_estimated, radius_km,
                                   x_km, y_km, z_km, vx_kms, vy_kms, vz_kms)
    VALUES (v_id, p_seq, p_name, v_body, p_role, p_mass_kg, p_estimated, p_radius_km, p_x, p_y, p_z, p_vx, p_vy, p_vz);
  END put_scenario_body;

  PROCEDURE drop_scenario (p_code IN VARCHAR2) IS
    v_origin VARCHAR2(8);
    v_id     NUMBER := id_of_scenario(p_code);
  BEGIN
    SELECT origin INTO v_origin FROM ura_scenario WHERE scenario_id = v_id;
    IF v_origin = 'BUILTIN' THEN
      RAISE_APPLICATION_ERROR(-20005, 'Eingebautes Szenario kann nicht gelöscht werden: ' || p_code);
    END IF;
    DELETE FROM ura_scenario WHERE scenario_id = v_id;
  END drop_scenario;

END ura_api;
/
