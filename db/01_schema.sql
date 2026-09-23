-- =====================================================================
-- Uranus / Schema DEMO
-- 01 - Tabellen, Sequenzen, Trigger
--
--   URA_SOURCE  woher jeder Messwert stammt (mit SHA-256 der gesicherten Datei)
--   URA_BODY    Uranus und seine 29 Monde - nur Fakten, keine Darstellung
--   URA_ORBIT   Bahnelemente; je Mond mehrere Quellen möglich, genau eine gültig
--   URA_RING    13 Ringe als schmale Ellipsen
--
-- Farbe und Oberflächentyp stehen bewusst nicht hier, sondern im Code
-- (Entscheidung 13: die Datenbank hält, was wahr ist, nicht was zu sehen ist).
-- Kein Fremdschlüssel auf SOLAR_BODY: FCurvedField baut diese Tabelle mit
-- CASCADE CONSTRAINTS neu auf, ein Schlüssel verschwände dabei ohne Meldung.
-- Schlüsselvergabe über Sequence + BEFORE-INSERT-Trigger (Hauskonvention).
-- =====================================================================

CREATE SEQUENCE ura_source_seq START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_body_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_orbit_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_ring_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_event_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_craft_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_state_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_tour_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_step_seq   START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_scen_seq  START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE ura_sb_seq    START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---------------------------------------------------------------------
-- Quellen
-- ---------------------------------------------------------------------
CREATE TABLE ura_source (
  source_id     NUMBER,
  code          VARCHAR2(20)  NOT NULL,
  title         VARCHAR2(200) NOT NULL,
  url           VARCHAR2(400),
  ephemeris     VARCHAR2(60),
  retrieved_on  DATE,
  sha256        VARCHAR2(64),
  note          VARCHAR2(400),
  CONSTRAINT ura_source_pk      PRIMARY KEY (source_id),
  CONSTRAINT ura_source_code_uq UNIQUE (code),
  CONSTRAINT ura_source_sha_ck  CHECK (sha256 IS NULL OR REGEXP_LIKE(sha256, '^[0-9a-f]{64}$'))
);

COMMENT ON TABLE  ura_source IS 'Herkunft der Messwerte. sha256 = Prüfsumme der gesicherten Datei in db/quellen.';

CREATE OR REPLACE TRIGGER ura_source_bi
  BEFORE INSERT ON ura_source FOR EACH ROW
BEGIN
  IF :NEW.source_id IS NULL THEN
    :NEW.source_id := ura_source_seq.NEXTVAL;
  END IF;
END;
/

-- ---------------------------------------------------------------------
-- Körper
-- ---------------------------------------------------------------------
CREATE TABLE ura_body (
  body_id          NUMBER,
  name             VARCHAR2(40)  NOT NULL,
  naif_id          NUMBER(8),
  body_group       VARCHAR2(10)  NOT NULL,
  parent_id        NUMBER,
  radius_km        NUMBER        NOT NULL,
  gm_km3s2         NUMBER,
  albedo           NUMBER,
  discovered_year  NUMBER(4),
  discoverer       VARCHAR2(80),
  namesake         VARCHAR2(200),
  -- nur beim Planeten: Pol und Rotation nach IAU
  pole_ra_deg      NUMBER,
  pole_dec_deg     NUMBER,
  w0_deg           NUMBER,
  wdot_deg_day     NUMBER,
  sort_no          NUMBER(4),
  -- Spielwiese (Phase 7): angenommene Dichte in g/cm³, nur wo keine Masse gemessen ist
  density_assumed  NUMBER,
  -- Virtuell: Masse aus GM. Liefert NULL, wenn GM fehlt - scheitert nie
  -- (eine virtuelle Spalte wird bei jedem SELECT * gerechnet).
  mass_kg          NUMBER GENERATED ALWAYS AS (gm_km3s2 / 6.6743E-20) VIRTUAL,
  CONSTRAINT ura_body_pk        PRIMARY KEY (body_id),
  CONSTRAINT ura_body_name_uq   UNIQUE (name),
  CONSTRAINT ura_body_naif_uq   UNIQUE (naif_id),
  CONSTRAINT ura_body_parent_fk FOREIGN KEY (parent_id) REFERENCES ura_body (body_id),
  CONSTRAINT ura_body_group_ck  CHECK (body_group IN ('PLANET', 'MAJOR', 'INNER', 'IRREGULAR')),
  CONSTRAINT ura_body_rad_ck    CHECK (radius_km > 0),
  CONSTRAINT ura_body_gm_ck     CHECK (gm_km3s2 IS NULL OR gm_km3s2 > 0),
  CONSTRAINT ura_body_alb_ck    CHECK (albedo IS NULL OR albedo BETWEEN 0 AND 1),
  CONSTRAINT ura_body_parent_ck CHECK ((body_group = 'PLANET' AND parent_id IS NULL)
                                    OR (body_group <> 'PLANET' AND parent_id IS NOT NULL)),
  CONSTRAINT ura_body_pole_ck   CHECK (body_group = 'PLANET'
                                    OR (pole_ra_deg IS NULL AND pole_dec_deg IS NULL AND w0_deg IS NULL AND wdot_deg_day IS NULL)),
  CONSTRAINT ura_body_ra_ck     CHECK (pole_ra_deg IS NULL OR (pole_ra_deg >= 0 AND pole_ra_deg < 360)),
  CONSTRAINT ura_body_dec_ck    CHECK (pole_dec_deg IS NULL OR pole_dec_deg BETWEEN -90 AND 90),
  CONSTRAINT ura_body_dens_ck   CHECK (density_assumed IS NULL OR (gm_km3s2 IS NULL AND density_assumed > 0))
);

COMMENT ON TABLE  ura_body IS 'Uranus und seine Monde. naif_id = JPL-Satellitennummer.';
COMMENT ON COLUMN ura_body.mass_kg IS 'Virtuell: gm_km3s2 / G';

CREATE OR REPLACE TRIGGER ura_body_bi
  BEFORE INSERT ON ura_body FOR EACH ROW
BEGIN
  IF :NEW.body_id IS NULL THEN
    :NEW.body_id := ura_body_seq.NEXTVAL;
  END IF;
END;
/

-- ---------------------------------------------------------------------
-- Bahnen
-- ---------------------------------------------------------------------
CREATE TABLE ura_orbit (
  orbit_id         NUMBER,
  body_id          NUMBER        NOT NULL,
  source_id        NUMBER        NOT NULL,
  ephemeris        VARCHAR2(20),
  frame            VARCHAR2(10)  NOT NULL,
  epoch_jd         NUMBER        NOT NULL,
  a_km             NUMBER        NOT NULL,
  ecc              NUMBER        NOT NULL,
  incl_deg         NUMBER        NOT NULL,
  node_deg         NUMBER        NOT NULL,
  argp_deg         NUMBER        NOT NULL,
  mean_anom_deg    NUMBER        NOT NULL,
  period_days      NUMBER        NOT NULL,
  apsis_period_yr  NUMBER,
  node_period_yr   NUMBER,
  pole_ra_deg      NUMBER,
  pole_dec_deg     NUMBER,
  quality          VARCHAR2(6)   NOT NULL,
  is_current       CHAR(1)       DEFAULT 'N' NOT NULL,
  fit_max_deg      NUMBER,
  fit_mean_deg     NUMBER,
  note             VARCHAR2(400),
  CONSTRAINT ura_orbit_pk       PRIMARY KEY (orbit_id),
  CONSTRAINT ura_orbit_body_fk  FOREIGN KEY (body_id)   REFERENCES ura_body (body_id),
  CONSTRAINT ura_orbit_src_fk   FOREIGN KEY (source_id) REFERENCES ura_source (source_id),
  CONSTRAINT ura_orbit_uq       UNIQUE (body_id, source_id),
  CONSTRAINT ura_orbit_frame_ck CHECK (frame IN ('EQUATORIAL', 'LAPLACE', 'ECLIPTIC')),
  CONSTRAINT ura_orbit_a_ck     CHECK (a_km > 0),
  CONSTRAINT ura_orbit_e_ck     CHECK (ecc >= 0 AND ecc < 1),
  CONSTRAINT ura_orbit_i_ck     CHECK (incl_deg BETWEEN 0 AND 180),
  CONSTRAINT ura_orbit_ang_ck   CHECK (node_deg >= 0 AND node_deg < 360
                                   AND argp_deg >= 0 AND argp_deg < 360
                                   AND mean_anom_deg >= 0 AND mean_anom_deg < 360),
  CONSTRAINT ura_orbit_p_ck     CHECK (period_days > 0),
  CONSTRAINT ura_orbit_prec_ck  CHECK ((apsis_period_yr IS NULL OR apsis_period_yr > 0)
                                   AND (node_period_yr IS NULL OR node_period_yr > 0)),
  CONSTRAINT ura_orbit_pole_ck  CHECK (frame <> 'LAPLACE' OR (pole_ra_deg IS NOT NULL AND pole_dec_deg IS NOT NULL)),
  CONSTRAINT ura_orbit_q_ck     CHECK (quality IN ('MEAN', 'FIT')),
  CONSTRAINT ura_orbit_cur_ck   CHECK (is_current IN ('J', 'N'))
);

-- Je Mond höchstens eine gültige Bahn: der Index kennt nur die Zeilen mit 'J'.
CREATE UNIQUE INDEX ura_orbit_cur_ux ON ura_orbit (CASE WHEN is_current = 'J' THEN body_id END);

COMMENT ON TABLE  ura_orbit IS 'Bahnelemente als präzedierende Ellipse. Winkel in Grad, Epoche JD (TDB).';
COMMENT ON COLUMN ura_orbit.frame IS 'EQUATORIAL: Uranusäquator, Bahnpol gegen IAU-Nordpol; LAPLACE: Pol in pole_ra/dec; ECLIPTIC: J2000';
COMMENT ON COLUMN ura_orbit.period_days IS 'Siderische Periode (mittlere Länge)';
COMMENT ON COLUMN ura_orbit.fit_max_deg IS 'Größte Abweichung von Horizons 1975-2030 in Grad';

CREATE OR REPLACE TRIGGER ura_orbit_bi
  BEFORE INSERT ON ura_orbit FOR EACH ROW
BEGIN
  IF :NEW.orbit_id IS NULL THEN
    :NEW.orbit_id := ura_orbit_seq.NEXTVAL;
  END IF;
END;
/

-- ---------------------------------------------------------------------
-- Ringe
-- ---------------------------------------------------------------------
CREATE TABLE ura_ring (
  ring_id       NUMBER,
  name          VARCHAR2(10)  NOT NULL,
  a_km          NUMBER        NOT NULL,
  width_min_km  NUMBER        NOT NULL,
  width_max_km  NUMBER        NOT NULL,
  ecc           NUMBER,
  incl_deg      NUMBER,
  tau_min       NUMBER,
  tau_max       NUMBER,
  dusty         CHAR(1)       DEFAULT 'N' NOT NULL,
  discovered_year NUMBER(4),
  discoverer    VARCHAR2(160),
  source_id     NUMBER,
  note          VARCHAR2(400),
  CONSTRAINT ura_ring_pk      PRIMARY KEY (ring_id),
  CONSTRAINT ura_ring_name_uq UNIQUE (name),
  CONSTRAINT ura_ring_src_fk  FOREIGN KEY (source_id) REFERENCES ura_source (source_id),
  CONSTRAINT ura_ring_a_ck    CHECK (a_km > 0),
  CONSTRAINT ura_ring_w_ck    CHECK (width_min_km > 0 AND width_max_km >= width_min_km),
  CONSTRAINT ura_ring_e_ck    CHECK (ecc IS NULL OR (ecc >= 0 AND ecc < 1)),
  CONSTRAINT ura_ring_tau_ck  CHECK (tau_min IS NULL OR tau_max >= tau_min),
  CONSTRAINT ura_ring_dust_ck CHECK (dusty IN ('J', 'N'))
);

COMMENT ON TABLE ura_ring IS 'Ringe: große Halbachse, kleinste/größte Breite, Exzentrizität, optische Tiefe.';

CREATE OR REPLACE TRIGGER ura_ring_bi
  BEFORE INSERT ON ura_ring FOR EACH ROW
BEGIN
  IF :NEW.ring_id IS NULL THEN
    :NEW.ring_id := ura_ring_seq.NEXTVAL;
  END IF;
END;
/

-- =====================================================================
-- Phase 6: Geschichten (Schaukasten, Teil 1)
--   URA_EVENT        Ereignisse auf der Zeitachse (Zeit als JD in UTC)
--   URA_CRAFT        Sonde auf einer Hyperbel um Uranus (Elemente zum ICRF-Äquator)
--   URA_CRAFT_STATE  Horizons-Vektoren der Sonde, wie geliefert (ICRF, TDB)
--   URA_TOUR / URA_TOUR_STEP   Kamerafahrten mit Stationen
-- =====================================================================

CREATE TABLE ura_event (
  event_id    NUMBER,
  event_jd    NUMBER        NOT NULL,
  kind        VARCHAR2(12)  NOT NULL,
  time_prec   VARCHAR2(8)   NOT NULL,
  title       VARCHAR2(200) NOT NULL,
  event_text  VARCHAR2(1000),
  body_id     NUMBER,
  ring_id     NUMBER,
  source_id   NUMBER,
  CONSTRAINT ura_event_pk       PRIMARY KEY (event_id),
  CONSTRAINT ura_event_title_uq UNIQUE (title),
  CONSTRAINT ura_event_body_fk  FOREIGN KEY (body_id)   REFERENCES ura_body (body_id),
  CONSTRAINT ura_event_ring_fk  FOREIGN KEY (ring_id)   REFERENCES ura_ring (ring_id),
  CONSTRAINT ura_event_src_fk   FOREIGN KEY (source_id) REFERENCES ura_source (source_id),
  CONSTRAINT ura_event_kind_ck  CHECK (kind IN ('DISCOVERY', 'FLYBY', 'SEASON', 'OBSERVATION')),
  CONSTRAINT ura_event_prec_ck  CHECK (time_prec IN ('YEAR', 'DAY', 'MINUTE')),
  CONSTRAINT ura_event_one_ck   CHECK (body_id IS NULL OR ring_id IS NULL)
);

COMMENT ON TABLE  ura_event IS 'Zeitachse der Geschichten. event_jd = Julianisches Datum in UTC (wie die Uhr der App).';

CREATE OR REPLACE TRIGGER ura_event_bi
  BEFORE INSERT ON ura_event FOR EACH ROW
BEGIN
  IF :NEW.event_id IS NULL THEN
    :NEW.event_id := ura_event_seq.NEXTVAL;
  END IF;
END;
/

CREATE TABLE ura_craft (
  craft_id       NUMBER,
  name           VARCHAR2(40)  NOT NULL,
  naif_id        NUMBER(8),
  tp_jd          NUMBER        NOT NULL,
  q_km           NUMBER        NOT NULL,
  ecc            NUMBER        NOT NULL,
  incl_deg       NUMBER        NOT NULL,
  node_deg       NUMBER        NOT NULL,
  argp_deg       NUMBER        NOT NULL,
  gm_km3s2       NUMBER        NOT NULL,
  valid_from_jd  NUMBER        NOT NULL,
  valid_to_jd    NUMBER        NOT NULL,
  fit_max_km     NUMBER,
  source_id      NUMBER,
  note           VARCHAR2(400),
  CONSTRAINT ura_craft_pk      PRIMARY KEY (craft_id),
  CONSTRAINT ura_craft_name_uq UNIQUE (name),
  CONSTRAINT ura_craft_src_fk  FOREIGN KEY (source_id) REFERENCES ura_source (source_id),
  CONSTRAINT ura_craft_e_ck    CHECK (ecc > 1),
  CONSTRAINT ura_craft_q_ck    CHECK (q_km > 0 AND gm_km3s2 > 0),
  CONSTRAINT ura_craft_t_ck    CHECK (valid_to_jd > valid_from_jd)
);

COMMENT ON TABLE ura_craft IS 'Vorbeiflug als Hyperbel: Periapsiszeit (JD, TDB), Periapsisabstand, e > 1, Winkel zum ICRF-Äquator.';

CREATE OR REPLACE TRIGGER ura_craft_bi
  BEFORE INSERT ON ura_craft FOR EACH ROW
BEGIN
  IF :NEW.craft_id IS NULL THEN
    :NEW.craft_id := ura_craft_seq.NEXTVAL;
  END IF;
END;
/

CREATE TABLE ura_craft_state (
  state_id  NUMBER,
  craft_id  NUMBER NOT NULL,
  jd_tdb    NUMBER NOT NULL,
  x_km      NUMBER NOT NULL,
  y_km      NUMBER NOT NULL,
  z_km      NUMBER NOT NULL,
  vx_kms    NUMBER NOT NULL,
  vy_kms    NUMBER NOT NULL,
  vz_kms    NUMBER NOT NULL,
  CONSTRAINT ura_state_pk       PRIMARY KEY (state_id),
  CONSTRAINT ura_state_craft_fk FOREIGN KEY (craft_id) REFERENCES ura_craft (craft_id) ON DELETE CASCADE,
  CONSTRAINT ura_state_uq       UNIQUE (craft_id, jd_tdb)
);

COMMENT ON TABLE ura_craft_state IS 'Horizons-Vektoren relativ zu Uranus, ICRF, km und km/s, Zeit als JD (TDB).';

CREATE OR REPLACE TRIGGER ura_state_bi
  BEFORE INSERT ON ura_craft_state FOR EACH ROW
BEGIN
  IF :NEW.state_id IS NULL THEN
    :NEW.state_id := ura_state_seq.NEXTVAL;
  END IF;
END;
/

CREATE TABLE ura_tour (
  tour_id      NUMBER,
  code         VARCHAR2(20)  NOT NULL,
  title        VARCHAR2(120) NOT NULL,
  overlay      VARCHAR2(10)  NOT NULL,
  time_mode    VARCHAR2(6)   NOT NULL,
  sort_no      NUMBER(4),
  description  VARCHAR2(400),
  CONSTRAINT ura_tour_pk      PRIMARY KEY (tour_id),
  CONSTRAINT ura_tour_code_uq UNIQUE (code),
  CONSTRAINT ura_tour_ov_ck   CHECK (overlay IN ('NONE', 'VOYAGER', 'SEASONS', 'DISCOVERY')),
  CONSTRAINT ura_tour_mode_ck CHECK (time_mode IN ('CLOCK', 'STORY'))
);

CREATE OR REPLACE TRIGGER ura_tour_bi
  BEFORE INSERT ON ura_tour FOR EACH ROW
BEGIN
  IF :NEW.tour_id IS NULL THEN
    :NEW.tour_id := ura_tour_seq.NEXTVAL;
  END IF;
END;
/

CREATE TABLE ura_tour_step (
  step_id    NUMBER,
  tour_id    NUMBER        NOT NULL,
  seq        NUMBER(4)     NOT NULL,
  at_s       NUMBER        NOT NULL,
  jd_utc     NUMBER,
  yaw_deg    NUMBER        NOT NULL,
  pitch_deg  NUMBER        NOT NULL,
  dist       NUMBER        NOT NULL,
  target     VARCHAR2(40)  NOT NULL,
  layers     VARCHAR2(200),
  title      VARCHAR2(200),
  caption    VARCHAR2(1000),
  CONSTRAINT ura_step_pk       PRIMARY KEY (step_id),
  CONSTRAINT ura_step_tour_fk  FOREIGN KEY (tour_id) REFERENCES ura_tour (tour_id) ON DELETE CASCADE,
  CONSTRAINT ura_step_uq       UNIQUE (tour_id, seq),
  CONSTRAINT ura_step_at_ck    CHECK (at_s >= 0),
  CONSTRAINT ura_step_cam_ck   CHECK (dist > 0 AND pitch_deg BETWEEN -90 AND 90)
);

COMMENT ON TABLE ura_tour_step IS 'Station einer Kamerafahrt: Zeitpunkt in der Fahrt (s), Uhr/Erzählzeit (JD, UTC), Blick, Ziel, Ebenen, Text.';

CREATE OR REPLACE TRIGGER ura_step_bi
  BEFORE INSERT ON ura_tour_step FOR EACH ROW
BEGIN
  IF :NEW.step_id IS NULL THEN
    :NEW.step_id := ura_step_seq.NEXTVAL;
  END IF;
END;
/

-- ---------------------------------------------------------------------
-- Spielwiese (Phase 7): Szenarien mit vollständigen Zustandsvektoren
-- ---------------------------------------------------------------------

CREATE TABLE ura_scenario (
  scenario_id  NUMBER,
  code         VARCHAR2(30)   NOT NULL,
  title        VARCHAR2(120)  NOT NULL,
  description  VARCHAR2(1000),
  start_jd     NUMBER         NOT NULL,
  rate_days_s  NUMBER         NOT NULL,
  origin       VARCHAR2(8)    NOT NULL,
  compare_eph  CHAR(1)        DEFAULT 'N' NOT NULL,
  created_on   DATE           DEFAULT SYSDATE NOT NULL,
  note         VARCHAR2(400),
  CONSTRAINT ura_scen_pk        PRIMARY KEY (scenario_id),
  CONSTRAINT ura_scen_code_uq   UNIQUE (code),
  CONSTRAINT ura_scen_origin_ck CHECK (origin IN ('BUILTIN', 'USER')),
  CONSTRAINT ura_scen_cmp_ck    CHECK (compare_eph IN ('J', 'N')),
  CONSTRAINT ura_scen_rate_ck   CHECK (rate_days_s > 0)
);

COMMENT ON TABLE ura_scenario IS 'Szenario der Spielwiese: Start (JD, UTC), Tempo (Tage je Sekunde), eingebaut oder vom Nutzer gespeichert.';

CREATE OR REPLACE TRIGGER ura_scen_bi
  BEFORE INSERT ON ura_scenario FOR EACH ROW
BEGIN
  IF :NEW.scenario_id IS NULL THEN
    :NEW.scenario_id := ura_scen_seq.NEXTVAL;
  END IF;
END;
/

CREATE TABLE ura_scenario_body (
  sb_id           NUMBER,
  scenario_id     NUMBER        NOT NULL,
  seq             NUMBER(4)     NOT NULL,
  name            VARCHAR2(40)  NOT NULL,
  body_id         NUMBER,
  role            VARCHAR2(6)   NOT NULL,
  mass_kg         NUMBER        NOT NULL,
  mass_estimated  CHAR(1)       DEFAULT 'N' NOT NULL,
  radius_km       NUMBER        NOT NULL,
  x_km            NUMBER        NOT NULL,
  y_km            NUMBER        NOT NULL,
  z_km            NUMBER        NOT NULL,
  vx_kms          NUMBER        NOT NULL,
  vy_kms          NUMBER        NOT NULL,
  vz_kms          NUMBER        NOT NULL,
  CONSTRAINT ura_sb_pk        PRIMARY KEY (sb_id),
  CONSTRAINT ura_sb_scen_fk   FOREIGN KEY (scenario_id) REFERENCES ura_scenario (scenario_id) ON DELETE CASCADE,
  CONSTRAINT ura_sb_body_fk   FOREIGN KEY (body_id) REFERENCES ura_body (body_id),
  CONSTRAINT ura_sb_seq_uq    UNIQUE (scenario_id, seq),
  CONSTRAINT ura_sb_name_uq   UNIQUE (scenario_id, name),
  CONSTRAINT ura_sb_role_ck   CHECK (role IN ('MOON', 'TEST', 'GUEST')),
  CONSTRAINT ura_sb_guest_ck  CHECK (role <> 'GUEST' OR body_id IS NULL),
  CONSTRAINT ura_sb_mass_ck   CHECK (mass_kg >= 0),
  CONSTRAINT ura_sb_est_ck    CHECK (mass_estimated IN ('J', 'N')),
  CONSTRAINT ura_sb_rad_ck    CHECK (radius_km > 0)
);

COMMENT ON TABLE ura_scenario_body IS 'Körper eines Szenarios: Zustand relativ zur Uranusmitte im ICRF (km, km/s), wie bei Horizons.';

CREATE OR REPLACE TRIGGER ura_sb_bi
  BEFORE INSERT ON ura_scenario_body FOR EACH ROW
BEGIN
  IF :NEW.sb_id IS NULL THEN
    :NEW.sb_id := ura_sb_seq.NEXTVAL;
  END IF;
END;
/
