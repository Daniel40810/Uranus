package com.dan.uranus.db;

import com.dan.uranus.model.Body;
import com.dan.uranus.model.BodyGroup;
import com.dan.uranus.model.Craft;
import com.dan.uranus.model.StoryEvent;
import com.dan.uranus.model.Tour;
import com.dan.uranus.model.OrbitElements;
import com.dan.uranus.model.OrbitQuality;
import com.dan.uranus.model.Ring;
import com.dan.uranus.model.Scenario;
import com.dan.uranus.model.UranusSystem;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liest Uranus, Monde, Bahnen und Ringe über URA_API — die einzige Schnittstelle zur Datenbank.
 * <p>
 * Die Fakten kommen aus der Datenbank, die Darstellung (Farbe, Oberflächentyp, Ringlook) aus dem
 * Code. Ein Mond, den es nur in der Datenbank gibt, erscheint mit neutralem Aussehen.
 */
public final class UranusDao {

    /** Ergebnis eines Ladeversuchs: immer ein Datensatz, dazu die Meldung für die Statuszeile. */
    public static final class Result {
        public final UranusSystem system;
        public final boolean fromDatabase;
        public final String message;
        Result(UranusSystem system, boolean fromDatabase, String message) {
            this.system = system; this.fromDatabase = fromDatabase; this.message = message;
        }
    }

    private final Connection con;

    public UranusDao(Connection con) { this.con = con; }

    /**
     * Lädt aus der Datenbank; scheitert das (keine Verbindungsdatei, Listener aus, Kennwort falsch,
     * Schema fehlt), kommt der eingebaute Datensatz mit dem Grund zurück. Wirft nie.
     */
    public static Result loadOrBuiltIn(UranusDb db) {
        if (db == null) return new Result(UranusSystem.builtIn(), false, "keine uranus-db.properties");
        try (Connection c = db.open()) {
            UranusSystem s = new UranusDao(c).load();
            return new Result(s, true, s.origin);
        } catch (SQLException e) {
            return new Result(UranusSystem.builtIn(), false, firstLine(e));
        } catch (RuntimeException | LinkageError e) {
            return new Result(UranusSystem.builtIn(), false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    static String firstLine(SQLException e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        int k = m.indexOf('\n');
        return (k > 0 ? m.substring(0, k) : m).trim();
    }

    /** Der ganze Datensatz aus URA_API. */
    public UranusSystem load() throws SQLException {
        Map<String, Object[]> facts = new HashMap<>();
        List<String> order = new ArrayList<>();
        try (Cursor c = call("ura_api.bodies")) {
            ResultSet rs = c.rs;
            while (rs.next()) {
                String group = rs.getString("BODY_GROUP");
                if ("PLANET".equals(group)) continue;
                String name = rs.getString("NAME");
                order.add(name);
                facts.put(name, new Object[]{rs.getInt("NAIF_ID"), BodyGroup.valueOf(group), rs.getDouble("RADIUS_KM"),
                        num(rs, "GM_KM3S2"), num(rs, "ALBEDO"), rs.getInt("DISCOVERED_YEAR"),
                        str(rs, "DISCOVERER"), str(rs, "NAMESAKE")});
            }
        }
        Map<String, Body> bodies = new HashMap<>();
        try (Cursor c = call("ura_api.orbits")) {
            ResultSet rs = c.rs;
            while (rs.next()) {
                String name = rs.getString("NAME");
                Object[] f = facts.get(name);
                if (f == null) continue;
                OrbitElements o = orbitOf(rs);
                OrbitQuality q = OrbitQuality.valueOf(rs.getString("QUALITY"));
                String src = "JPL " + str(rs, "EPHEMERIS") + (q == OrbitQuality.FIT ? " · an Horizons angeglichen" : " · mittlere Elemente");
                bodies.put(name, body(name, f, o, q, src, num(rs, "FIT_MAX_DEG")));
            }
        }
        List<Body> list = new ArrayList<>();
        for (String n : order) {
            Body b = bodies.get(n);
            if (b == null) throw new SQLException("Keine gültige Bahn für " + n + " in URA_ORBIT");
            list.add(b);
        }
        List<Ring> rings = new ArrayList<>();
        try (Cursor c = call("ura_api.rings")) {
            ResultSet rs = c.rs;
            while (rs.next()) {
                String n = rs.getString("NAME");
                float[] look = UranusSystem.ringLook(n);
                double ecc = num(rs, "ECC"), inc = num(rs, "INCL_DEG");
                rings.add(new Ring(n, rs.getDouble("A_KM"), rs.getDouble("WIDTH_MIN_KM"), rs.getDouble("WIDTH_MAX_KM"),
                        Double.isNaN(ecc) ? 0 : ecc, Double.isNaN(inc) ? 0 : inc, num(rs, "TAU_MIN"), num(rs, "TAU_MAX"),
                        "J".equals(rs.getString("DUSTY")), look[0], look[1], look[2], look[3],
                        rs.getInt("DISCOVERED_YEAR"), str(rs, "DISCOVERER")));
            }
        }
        String info;
        try (CallableStatement cs = con.prepareCall("{ ? = call ura_api.info }")) {
            cs.registerOutParameter(1, Types.VARCHAR);
            cs.execute();
            info = cs.getString(1);
        }
        return new UranusSystem(list, rings, events(), crafts(), tours(), scenarios(),
                "Datenbank " + con.getMetaData().getUserName() + " · " + info);
    }

    /** Ereignisse aus URA_EVENT. */
    public List<StoryEvent> events() throws SQLException {
        List<StoryEvent> out = new ArrayList<>();
        try (Cursor c = call("ura_api.events")) {
            ResultSet rs = c.rs;
            while (rs.next())
                out.add(new StoryEvent(rs.getDouble("EVENT_JD"), StoryEvent.Kind.valueOf(rs.getString("KIND")),
                        StoryEvent.Precision.valueOf(rs.getString("TIME_PREC")), rs.getString("TITLE"), str(rs, "EVENT_TEXT"),
                        rs.getString("BODY"), rs.getString("RING")));
        }
        return out;
    }

    /** Sonden aus URA_CRAFT. */
    public List<Craft> crafts() throws SQLException {
        List<Craft> out = new ArrayList<>();
        try (Cursor c = call("ura_api.crafts")) {
            ResultSet rs = c.rs;
            while (rs.next())
                out.add(new Craft(rs.getString("NAME"), rs.getInt("NAIF_ID"), rs.getDouble("TP_JD"), rs.getDouble("Q_KM"),
                        rs.getDouble("ECC"), rs.getDouble("INCL_DEG"), rs.getDouble("NODE_DEG"), rs.getDouble("ARGP_DEG"),
                        rs.getDouble("GM_KM3S2"), rs.getDouble("VALID_FROM_JD"), rs.getDouble("VALID_TO_JD"),
                        num(rs, "FIT_MAX_KM"), str(rs, "NOTE")));
        }
        return out;
    }

    /** Kamerafahrten aus URA_TOUR und URA_TOUR_STEP. */
    public List<Tour> tours() throws SQLException {
        Map<String, List<Tour.Step>> steps = new HashMap<>();
        try (Cursor c = call("ura_api.tour_steps")) {
            ResultSet rs = c.rs;
            while (rs.next())
                steps.computeIfAbsent(rs.getString("CODE"), k -> new ArrayList<>()).add(new Tour.Step(rs.getDouble("AT_S"),
                        num(rs, "JD_UTC"), rs.getDouble("YAW_DEG"), rs.getDouble("PITCH_DEG"), rs.getDouble("DIST"),
                        rs.getString("TARGET"), str(rs, "LAYERS"), str(rs, "TITLE"), str(rs, "CAPTION")));
        }
        List<Tour> out = new ArrayList<>();
        try (Cursor c = call("ura_api.tours")) {
            ResultSet rs = c.rs;
            while (rs.next()) {
                String code = rs.getString("CODE");
                List<Tour.Step> st = steps.get(code);
                if (st == null || st.size() < 2) continue;              // eine Fahrt ohne Stationen ist keine
                out.add(new Tour(code, rs.getString("TITLE"), str(rs, "DESCRIPTION"), Tour.Overlay.valueOf(rs.getString("OVERLAY")),
                        Tour.TimeMode.valueOf(rs.getString("TIME_MODE")), st));
            }
        }
        return out;
    }

    /** Die gespeicherten Horizons-Vektoren einer Sonde: {jd, x, y, z, vx, vy, vz} (ICRF). */
    public List<double[]> craftStates(String craft) throws SQLException {
        List<double[]> out = new ArrayList<>();
        try (CallableStatement cs = con.prepareCall("{ ? = call ura_api.craft_states(?) }")) {
            registerCursor(cs);
            cs.setString(2, craft);
            cs.execute();
            try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                while (rs.next()) {
                    double[] s = new double[7];
                    for (int k = 0; k < 7; k++) s[k] = rs.getDouble(k + 1);
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Alle Bahnen einer Quelle (für Prüfungen und den Vergleich der Quellen). */
    /** Szenarien der Spielwiese mit allen Körpern (eingebaute zuerst). */
    public List<Scenario> scenarios() throws SQLException {
        Map<String, List<Scenario.Item>> items = new HashMap<>();
        try (Cursor c = call("ura_api.scenario_bodies")) {
            ResultSet rs = c.rs;
            while (rs.next())
                items.computeIfAbsent(rs.getString("CODE"), k -> new ArrayList<>()).add(new Scenario.Item(rs.getString("NAME"),
                        Scenario.Role.valueOf(rs.getString("ROLE")), rs.getDouble("MASS_KG"), "J".equals(rs.getString("MASS_ESTIMATED")),
                        rs.getDouble("RADIUS_KM"), new double[]{rs.getDouble("X_KM"), rs.getDouble("Y_KM"), rs.getDouble("Z_KM")},
                        new double[]{rs.getDouble("VX_KMS"), rs.getDouble("VY_KMS"), rs.getDouble("VZ_KMS")}));
        }
        List<Scenario> out = new ArrayList<>();
        try (Cursor c = call("ura_api.scenarios")) {
            ResultSet rs = c.rs;
            while (rs.next()) {
                String code = rs.getString("CODE");
                out.add(new Scenario(code, rs.getString("TITLE"), str(rs, "DESCRIPTION"), rs.getDouble("START_JD"),
                        rs.getDouble("RATE_DAYS_S"), Scenario.Origin.valueOf(rs.getString("ORIGIN")), "J".equals(rs.getString("COMPARE_EPH")),
                        items.getOrDefault(code, new ArrayList<>())));
            }
        }
        return out;
    }

    /**
     * Speichert ein Szenario über URA_API (put_scenario, dann put_scenario_body je Körper) in einer
     * Transaktion. Ein eingebautes lässt sich so nicht überschreiben (ORA-20005 aus dem Package).
     */
    public void saveScenario(Scenario sc) throws SQLException {
        boolean auto = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (CallableStatement cs = con.prepareCall("{ call ura_api.put_scenario(?, ?, ?, ?, ?, ?, ?, NULL) }")) {
                cs.setString(1, sc.code);
                cs.setString(2, sc.title);
                cs.setString(3, sc.description);
                cs.setDouble(4, sc.startJd);
                cs.setDouble(5, sc.rateDaysPerS);
                cs.setString(6, sc.origin.name());
                cs.setString(7, sc.compareEphemeris ? "J" : "N");
                cs.execute();
            }
            try (CallableStatement cs = con.prepareCall("{ call ura_api.put_scenario_body(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) }")) {
                int k = 1;
                for (Scenario.Item it : sc.items()) {
                    cs.setString(1, sc.code);
                    cs.setInt(2, k++);
                    cs.setString(3, it.name);
                    cs.setString(4, it.role.name());
                    cs.setDouble(5, it.massKg);
                    cs.setString(6, it.massEstimated ? "J" : "N");
                    cs.setDouble(7, it.radiusKm);
                    for (int j = 0; j < 3; j++) { cs.setDouble(8 + j, it.r[j]); cs.setDouble(11 + j, it.v[j]); }
                    cs.addBatch();
                }
                cs.executeBatch();
            }
            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(auto);
        }
    }

    /** Löscht ein eigenes Szenario (eingebaute: ORA-20005). */
    public void dropScenario(String code) throws SQLException {
        try (CallableStatement cs = con.prepareCall("{ call ura_api.drop_scenario(?) }")) {
            cs.setString(1, code);
            cs.execute();
        }
        if (!con.getAutoCommit()) con.commit();
    }

    /** Energie eines Szenarios, in PL/SQL gerechnet (J). */
    public double energy(String code) throws SQLException {
        try (CallableStatement cs = con.prepareCall("{ ? = call ura_api.energy(?) }")) {
            cs.registerOutParameter(1, Types.DOUBLE);
            cs.setString(2, code);
            cs.execute();
            return cs.getDouble(1);
        }
    }

    /** Für die App: speichern und die Szenarien neu lesen; wirft nie, meldet stattdessen. */
    public static String saveAndReload(UranusDb db, Scenario sc, List<Scenario> out) {
        if (db == null) return "keine Datenbank (uranus-db.properties fehlt)";
        try (Connection c = db.open()) {
            UranusDao d = new UranusDao(c);
            d.saveScenario(sc);
            out.addAll(d.scenarios());
            return null;
        } catch (SQLException e) {
            return firstLine(e);
        } catch (RuntimeException | LinkageError e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public Map<String, OrbitElements> orbitsOf(String sourceCode) throws SQLException {
        Map<String, OrbitElements> out = new HashMap<>();
        try (CallableStatement cs = con.prepareCall("{ ? = call ura_api.orbits(?) }")) {
            registerCursor(cs);
            cs.setString(2, sourceCode);
            cs.execute();
            try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                while (rs.next()) out.put(rs.getString("NAME"), orbitOf(rs));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Abbildung

    static OrbitElements orbitOf(ResultSet rs) throws SQLException {
        double pa = num(rs, "APSIS_PERIOD_YR"), pn = num(rs, "NODE_PERIOD_YR");
        return new OrbitElements(OrbitElements.Frame.valueOf(rs.getString("FRAME")), rs.getDouble("EPOCH_JD"),
                rs.getDouble("A_KM"), rs.getDouble("ECC"), rs.getDouble("INCL_DEG"), rs.getDouble("NODE_DEG"),
                rs.getDouble("ARGP_DEG"), rs.getDouble("MEAN_ANOM_DEG"), rs.getDouble("PERIOD_DAYS"),
                Double.isNaN(pa) ? 0 : pa, Double.isNaN(pn) ? 0 : pn, num(rs, "POLE_RA_DEG"), num(rs, "POLE_DEC_DEG"));
    }

    private static Body body(String name, Object[] f, OrbitElements o, OrbitQuality q, String src, double fitMax) {
        UranusSystem.Facts look = null;
        for (UranusSystem.Facts x : UranusSystem.facts()) if (x.name.equals(name)) look = x;
        BodyGroup g = (BodyGroup) f[1];
        double gm = (Double) f[3];
        double mass = Double.isNaN(gm) ? 0 : gm / UranusSystem.G_KM;
        double albedo = Double.isNaN((Double) f[4]) ? 0.05 : (Double) f[4];
        float r = look != null ? look.red : .55f, gr = look != null ? look.green : .55f, b = look != null ? look.blue : .57f;
        Body.Surface s = look != null ? look.surface : Body.Surface.SMALL_DARK;
        return new Body(name, (Integer) f[0], g, (Double) f[2], mass, albedo, r, gr, b, s, (Integer) f[5],
                (String) f[6], (String) f[7], o, q, src, fitMax);
    }

    static double num(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? Double.NaN : v;
    }

    static String str(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        return v == null ? "" : v;
    }

    // ------------------------------------------------------------------ Cursor

    /** Oracle-Cursor als AutoCloseable (Anweisung und Ergebnis zusammen). */
    private static final class Cursor implements AutoCloseable {
        final CallableStatement cs; final ResultSet rs;
        Cursor(CallableStatement cs, ResultSet rs) { this.cs = cs; this.rs = rs; }
        @Override public void close() throws SQLException {
            try { rs.close(); } finally { cs.close(); }
        }
    }

    private Cursor call(String fn) throws SQLException {
        CallableStatement cs = con.prepareCall("{ ? = call " + fn + " }");
        try {
            registerCursor(cs);
            cs.execute();
            return new Cursor(cs, (ResultSet) cs.getObject(1));
        } catch (SQLException e) {
            cs.close();
            throw e;
        }
    }

    /** JDBC 4.2 kennt REF_CURSOR; ältere Treiber nur die Oracle-Nummer −10. */
    private static void registerCursor(CallableStatement cs) throws SQLException {
        try {
            cs.registerOutParameter(1, Types.REF_CURSOR);
        } catch (SQLFeatureNotSupportedException e) {
            cs.registerOutParameter(1, -10);
        }
    }
}
