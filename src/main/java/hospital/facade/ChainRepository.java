package hospital.facade;

import hospital.exceptions.PersistenceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class ChainRepository {
    private final String url;

    public ChainRepository(Path dbFile) {
        try {
            Path parent = dbFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new PersistenceException("Cannot create data directory", e);
        }
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        initSchema();
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void initSchema() {
        try (Connection c = open(); Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS chains (
                        chain_id TEXT PRIMARY KEY,
                        state TEXT NOT NULL,
                        patient_name TEXT NOT NULL,
                        failure_reason TEXT,
                        updated_at TEXT NOT NULL
                    )""");
        } catch (SQLException e) {
            throw new PersistenceException("Cannot initialise chains schema", e);
        }
    }

    public void create(String chainId, String patientName) {
        String sql = "INSERT INTO chains (chain_id, state, patient_name, failure_reason, updated_at) "
                + "VALUES (?, ?, ?, NULL, ?)";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chainId);
            ps.setString(2, ChainState.STARTED.name());
            ps.setString(3, patientName == null ? "" : patientName);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot create chain", e);
        }
    }

    public void setState(String chainId, ChainState state, String reason) {
        String sql = "UPDATE chains SET state = ?, failure_reason = ?, updated_at = ? WHERE chain_id = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, state.name());
            ps.setString(2, reason);
            ps.setString(3, Instant.now().toString());
            ps.setString(4, chainId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot update chain state", e);
        }
    }

    public Optional<ChainRecord> find(String chainId) {
        String sql = "SELECT * FROM chains WHERE chain_id = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chainId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read chain", e);
        }
    }

    public List<ChainRecord> findAll() {
        List<ChainRecord> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM chains ORDER BY rowid")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("Cannot list chains", e);
        }
    }


    public List<ChainRecord> findUnfinished() {
        return findAll().stream().filter(r -> !r.state().isFinished()).toList();
    }

    private ChainRecord map(ResultSet rs) throws SQLException {
        return new ChainRecord(
                rs.getString("chain_id"),
                ChainState.valueOf(rs.getString("state")),
                rs.getString("patient_name"),
                rs.getString("failure_reason"),
                rs.getString("updated_at"));
    }
}