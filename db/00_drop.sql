-- =====================================================================
-- Uranus / Schema DEMO
-- 00 - Aufräumen. Fällt nicht um, wenn noch nichts existiert.
--
-- Nur Objekte mit dem Präfix URA_ (LIKE 'URA\_%' ESCAPE '\'), damit
-- nichts von FCurvedField (FCF_, SOLAR_) oder anderen Projekten fällt.
-- =====================================================================

BEGIN
  FOR o IN (SELECT object_name, object_type
              FROM user_objects
             WHERE object_type IN ('PACKAGE', 'FUNCTION', 'PROCEDURE', 'VIEW')
               AND object_name LIKE 'URA\_%' ESCAPE '\') LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP ' || o.object_type || ' ' || o.object_name;
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

BEGIN
  FOR t IN (SELECT table_name FROM user_tables
             WHERE table_name IN ('URA_SCENARIO_BODY', 'URA_SCENARIO', 'URA_TOUR_STEP', 'URA_TOUR', 'URA_CRAFT_STATE', 'URA_CRAFT', 'URA_EVENT',
                                  'URA_ORBIT', 'URA_RING', 'URA_BODY', 'URA_SOURCE')) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS PURGE';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

-- Typen nach dem Package: das Package hängt am Typ, nicht umgekehrt.
BEGIN
  FOR t IN (SELECT type_name FROM user_types WHERE type_name LIKE 'T\_URA\_%' ESCAPE '\') LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TYPE ' || t.type_name || ' FORCE';
    EXCEPTION WHEN OTHERS THEN NULL;
    END;
  END LOOP;
END;
/

-- Sequenzen: die Trigger fallen mit ihren Tabellen, die Sequenzen nicht.
BEGIN
  FOR q IN (SELECT sequence_name FROM user_sequences
             WHERE sequence_name IN ('URA_SOURCE_SEQ', 'URA_BODY_SEQ', 'URA_ORBIT_SEQ', 'URA_RING_SEQ',
                                     'URA_EVENT_SEQ', 'URA_CRAFT_SEQ', 'URA_STATE_SEQ',
                                     'URA_TOUR_SEQ', 'URA_STEP_SEQ', 'URA_SCEN_SEQ', 'URA_SB_SEQ')) LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || q.sequence_name;
  END LOOP;
END;
/
