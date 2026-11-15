package fr.cobblepulse.battlepass.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.cobblepulse.battlepass.config.Cfg;
import fr.cobblepulse.battlepass.config.TomlFile;
import fr.cobblepulse.battlepass.core.Json;
import fr.cobblepulse.battlepass.data.PlayerData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL (pool HikariCP, peut partager la base de CobblePulse) ou SQLite (fichier local).
 * Tables : cp_bp_players (colonnes lisibles + dossier complet en JSON) et cp_bp_kv.
 */
public final class SqlStorage implements Storage {
    private final boolean sqlite;
    private final TomlFile cfg;
    private final String p;
    private HikariDataSource ds;
    private Connection sqliteConn;
    private String desc = "";

    public SqlStorage(TomlFile cfg, boolean sqlite) {
        this.cfg = cfg;
        this.sqlite = sqlite;
        this.p = cfg.str("mysql.table_prefix", "cp_bp_").replaceAll("[^A-Za-z0-9_]", "");
    }

    @Override
    public String name() {
        return desc;
    }

    private Connection conn() throws SQLException {
        if (sqlite) return sqliteConn;
        return ds.getConnection();
    }

    private void release(Connection c) throws SQLException {
        if (!sqlite && c != null) c.close();
    }

    @Override
    public void init() throws Exception {
        if (sqlite) {
            Class.forName("org.sqlite.JDBC");
            Path file = Cfg.root().resolve(cfg.str("sqlite.file", "data/battlepass.db"));
            Files.createDirectories(file.getParent());
            sqliteConn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = sqliteConn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
            }
            desc = "SQLite (" + file + ")";
        } else {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException ignored) {
            }
            TomlFile src = cfg;
            if (cfg.b("mysql.share_cobblepulse", false)) {
                Path cp = Cfg.cobblePulseRoot().resolve("storage.toml");
                if (Files.exists(cp)) src = TomlFile.loadPlain(cp);
            }
            String host = src.str("mysql.host", "localhost");
            String db = src.str("mysql.database", "cobblepulse");
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:mysql://" + host + ":" + src.i("mysql.port", 3306) + "/" + db + "?useSSL=" + src.b("mysql.use_ssl", false)
                    + "&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC");
            hc.setUsername(src.str("mysql.username", "root"));
            hc.setPassword(src.str("mysql.password", ""));
            hc.setMaximumPoolSize(Math.max(2, cfg.i("mysql.pool_size", 4)));
            hc.setPoolName("BattlePass-MySQL");
            hc.setConnectionTimeout(10_000);
            ds = new HikariDataSource(hc);
            desc = "MySQL (" + host + "/" + db + ")";
        }
        String charset = sqlite ? "" : " CHARACTER SET utf8mb4";
        Connection c = conn();
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + p + "players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(32), season INT DEFAULT 0,"
                    + " points BIGINT DEFAULT 0, premium INT DEFAULT 0, updated_at BIGINT DEFAULT 0, data MEDIUMTEXT)" + charset);
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + p + "kv (k VARCHAR(128) PRIMARY KEY, v MEDIUMTEXT)" + charset);
        } finally {
            release(c);
        }
    }

    @Override
    public void close() {
        try {
            if (ds != null) ds.close();
            if (sqliteConn != null) sqliteConn.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public List<PlayerData> loadAllPlayers() throws SQLException {
        List<PlayerData> out = new ArrayList<>();
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name, data FROM " + p + "players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String json = rs.getString(3);
                PlayerData d = null;
                try {
                    if (json != null && !json.isBlank()) d = Json.GSON.fromJson(json, PlayerData.class);
                } catch (Exception ignored) {
                }
                if (d == null) d = new PlayerData(rs.getString(1), rs.getString(2));
                if (d.uuid == null) d.uuid = rs.getString(1);
                d.normalize();
                out.add(d);
            }
        } finally {
            release(c);
        }
        return out;
    }

    @Override
    public void savePlayer(PlayerData d) throws SQLException {
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "players (uuid, name, season, points, premium, updated_at, data)"
                + " VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, d.uuid);
            ps.setString(2, d.name);
            ps.setInt(3, d.saison);
            ps.setLong(4, d.points);
            ps.setInt(5, d.premium ? 1 : 0);
            ps.setLong(6, System.currentTimeMillis());
            ps.setString(7, Json.COMPACT.toJson(d));
            ps.executeUpdate();
        } finally {
            release(c);
        }
    }

    @Override
    public String loadKv(String key) throws SQLException {
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM " + p + "kv WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } finally {
            release(c);
        }
    }

    @Override
    public void saveKv(String key, String json) throws SQLException {
        Connection c = conn();
        try (PreparedStatement ps = c.prepareStatement("REPLACE INTO " + p + "kv (k, v) VALUES (?,?)")) {
            ps.setString(1, key);
            ps.setString(2, json);
            ps.executeUpdate();
        } finally {
            release(c);
        }
    }
}
