package hospital.clinical;

import hospital.exceptions.PersistenceException;
import hospital.exceptions.PharmacyRejectedException;

import java.io.IOException;
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
import java.util.Optional;


public class ClinicalOrderRepository {
    private final String url;

    public ClinicalOrderRepository(Path dbFile) {
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
                    CREATE TABLE IF NOT EXISTS pharmacy_stock (
                        medication TEXT PRIMARY KEY,
                        available INTEGER NOT NULL
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id TEXT PRIMARY KEY,
                        chain_id TEXT NOT NULL,
                        lab_test TEXT NOT NULL,
                        medication TEXT NOT NULL,
                        status TEXT NOT NULL
                    )""");
        } catch (SQLException e) {
            throw new PersistenceException("Cannot initialise clinical schema", e);
        }
    }



    public void setStock(String medication, int available) {
        String sql = "INSERT OR REPLACE INTO pharmacy_stock (medication, available) VALUES (?, ?)";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, medication);
            ps.setInt(2, available);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot set pharmacy stock", e);
        }
    }

    public int stockOf(String medication) {
        String sql = "SELECT available FROM pharmacy_stock WHERE medication = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, medication);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("available") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read pharmacy stock", e);
        }
    }




    public void place(ClinicalOrder order) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                if (order.usesMedication()) {
                    int rows = updateOne(c,
                            "UPDATE pharmacy_stock SET available = available - 1 "
                                    + "WHERE medication = ? AND available > 0",
                            order.getMedication());
                    if (rows != 1) {
                        throw new PharmacyRejectedException(
                                "Pharmacy is out of " + order.getMedication());
                    }
                }
                insertOrder(c, order);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot place clinical order", e);
        }
    }


    public void cancel(ClinicalOrder order) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                if (order.usesMedication()) {
                    updateOne(c, "UPDATE pharmacy_stock SET available = available + 1 "
                            + "WHERE medication = ?", order.getMedication());
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE orders SET status = ? WHERE id = ?")) {
                    ps.setString(1, order.getStatus().name());
                    ps.setString(2, order.getId());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot cancel clinical order", e);
        }
    }



    public Optional<ClinicalOrder> findByChainId(String chainId) {
        String sql = "SELECT * FROM orders WHERE chain_id = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chainId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read clinical order", e);
        }
    }

    public List<ClinicalOrder> findAll() {
        List<ClinicalOrder> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM orders")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("Cannot list clinical orders", e);
        }
    }



    private void insertOrder(Connection c, ClinicalOrder o) throws SQLException {
        String sql = "INSERT INTO orders (id, chain_id, lab_test, medication, status) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, o.getId());
            ps.setString(2, o.getChainId());
            ps.setString(3, o.getLabTest());
            ps.setString(4, o.getMedication());
            ps.setString(5, o.getStatus().name());
            ps.executeUpdate();
        }
    }

    private int updateOne(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            return ps.executeUpdate();
        }
    }

    private ClinicalOrder map(ResultSet rs) throws SQLException {
        return ClinicalOrder.restore(
                rs.getString("id"),
                rs.getString("chain_id"),
                rs.getString("lab_test"),
                rs.getString("medication"),
                OrderStatus.valueOf(rs.getString("status")));
    }
}