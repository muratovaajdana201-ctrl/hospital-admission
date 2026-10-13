package hospital.registration;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class RegistrationRepository {

    public record PolicyRecord(String policyNumber, double coverageLimit, LocalDate validUntil) {}

    private final String url;

    public RegistrationRepository(Path dbFile) {
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
                    CREATE TABLE IF NOT EXISTS policies (
                        policy_number TEXT PRIMARY KEY,
                        coverage_limit REAL NOT NULL,
                        valid_until TEXT NOT NULL
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS registrations (
                        id TEXT PRIMARY KEY,
                        chain_id TEXT NOT NULL,
                        patient_name TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        policy_number TEXT NOT NULL,
                        estimated_cost REAL NOT NULL,
                        status TEXT NOT NULL
                    )""");
        } catch (SQLException e) {
            throw new PersistenceException("Cannot initialise registration schema", e);
        }
    }

    public void savePolicy(PolicyRecord policy) {
        String sql = "INSERT OR REPLACE INTO policies (policy_number, coverage_limit, valid_until) VALUES (?,?,?)";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, policy.policyNumber());
            ps.setDouble(2, policy.coverageLimit());
            ps.setString(3, policy.validUntil().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot save policy", e);
        }
    }

    public Optional<PolicyRecord> findPolicy(String policyNumber) {
        String sql = "SELECT policy_number, coverage_limit, valid_until FROM policies WHERE policy_number = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, policyNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PolicyRecord(
                        rs.getString("policy_number"),
                        rs.getDouble("coverage_limit"),
                        LocalDate.parse(rs.getString("valid_until"))));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read policy", e);
        }
    }

    public void save(Registration r) {
        String sql = """
                INSERT OR REPLACE INTO registrations
                (id, chain_id, patient_name, age, policy_number, estimated_cost, status)
                VALUES (?,?,?,?,?,?,?)""";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getId());
            ps.setString(2, r.getChainId());
            ps.setString(3, r.getPatientName());
            ps.setInt(4, r.getAge());
            ps.setString(5, r.getPolicyNumber());
            ps.setDouble(6, r.getEstimatedCost());
            ps.setString(7, r.getStatus().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot save registration", e);
        }
    }

    public Optional<Registration> findByChainId(String chainId) {
        String sql = "SELECT * FROM registrations WHERE chain_id = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chainId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read registration", e);
        }
    }

    public List<Registration> findAll() {
        List<Registration> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM registrations")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("Cannot list registrations", e);
        }
    }

    private Registration map(ResultSet rs) throws SQLException {
        return Registration.restore(
                rs.getString("id"),
                rs.getString("chain_id"),
                rs.getString("patient_name"),
                rs.getInt("age"),
                rs.getString("policy_number"),
                rs.getDouble("estimated_cost"),
                RegistrationStatus.valueOf(rs.getString("status")));
    }
}