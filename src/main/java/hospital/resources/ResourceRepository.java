package hospital.resources;

import hospital.exceptions.EquipmentUnavailableException;
import hospital.exceptions.NoBedAvailableException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persistence gateway подсистемы B. Койка, оборудование и бронь меняются в ОДНОЙ транзакции. */
public class ResourceRepository {
    private final String url;

    public ResourceRepository(Path dbFile) {
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
                    CREATE TABLE IF NOT EXISTS beds (
                        bed_id TEXT PRIMARY KEY,
                        ward_type TEXT NOT NULL,
                        occupied INTEGER NOT NULL DEFAULT 0
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS equipment_stock (
                        equipment_type TEXT PRIMARY KEY,
                        available INTEGER NOT NULL
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS bookings (
                        id TEXT PRIMARY KEY,
                        chain_id TEXT NOT NULL,
                        bed_id TEXT NOT NULL,
                        equipment_type TEXT NOT NULL,
                        status TEXT NOT NULL
                    )""");
        } catch (SQLException e) {
            throw new PersistenceException("Cannot initialise resources schema", e);
        }
    }

    // ---------- справочные данные (койки, остатки) ----------

    public void saveBed(String bedId, String wardType) {
        execute("INSERT OR REPLACE INTO beds (bed_id, ward_type, occupied) VALUES (?, ?, 0)",
                bedId, wardType);
    }

    public void setEquipmentStock(String equipmentType, int available) {
        String sql = "INSERT OR REPLACE INTO equipment_stock (equipment_type, available) VALUES (?, ?)";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, equipmentType);
            ps.setInt(2, available);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot set equipment stock", e);
        }
    }

    public List<String> freeBedIds(String wardType) {
        String sql = "SELECT bed_id FROM beds WHERE ward_type = ? AND occupied = 0";
        List<String> result = new ArrayList<>();
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, wardType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("bed_id"));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read free beds", e);
        }
    }

    public boolean isBedOccupied(String bedId) {
        String sql = "SELECT occupied FROM beds WHERE bed_id = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, bedId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("occupied") == 1;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read bed", e);
        }
    }

    public int equipmentAvailable(String equipmentType) {
        String sql = "SELECT available FROM equipment_stock WHERE equipment_type = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, equipmentType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("available") : 0;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read equipment stock", e);
        }
    }

    // ---------- бронирование (транзакции) ----------

    /** Занять койку + списать оборудование + записать бронь. Всё или ничего. */
    public void allocate(ResourceBooking booking) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                int bedRows = update(c,
                        "UPDATE beds SET occupied = 1 WHERE bed_id = ? AND occupied = 0",
                        booking.getBedId());
                if (bedRows != 1) {
                    throw new NoBedAvailableException(
                            "Bed " + booking.getBedId() + " is already taken");
                }
                if (booking.usesEquipment()) {
                    int stockRows = update(c,
                            "UPDATE equipment_stock SET available = available - 1 "
                                    + "WHERE equipment_type = ? AND available > 0",
                            booking.getEquipmentType());
                    if (stockRows != 1) {
                        throw new EquipmentUnavailableException(
                                "No " + booking.getEquipmentType() + " available");
                    }
                }
                insertBooking(c, booking);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();   // откатываем всё, что успели изменить в этой транзакции
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot allocate resources", e);
        }
    }

    /** Освободить койку + вернуть оборудование + пометить бронь RELEASED. Всё или ничего. */
    public void release(ResourceBooking booking) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                update(c, "UPDATE beds SET occupied = 0 WHERE bed_id = ?", booking.getBedId());
                if (booking.usesEquipment()) {
                    update(c, "UPDATE equipment_stock SET available = available + 1 "
                            + "WHERE equipment_type = ?", booking.getEquipmentType());
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookings SET status = ? WHERE id = ?")) {
                    ps.setString(1, booking.getStatus().name());
                    ps.setString(2, booking.getId());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot release resources", e);
        }
    }

    // ---------- чтение броней ----------

    public Optional<ResourceBooking> findByChainId(String chainId) {
        String sql = "SELECT * FROM bookings WHERE chain_id = ?";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chainId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot read booking", e);
        }
    }

    public List<ResourceBooking> findAll() {
        List<ResourceBooking> result = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM bookings")) {
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new PersistenceException("Cannot list bookings", e);
        }
    }

    // ---------- вспомогательные методы ----------

    private void insertBooking(Connection c, ResourceBooking b) throws SQLException {
        String sql = "INSERT INTO bookings (id, chain_id, bed_id, equipment_type, status) "
                + "VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, b.getId());
            ps.setString(2, b.getChainId());
            ps.setString(3, b.getBedId());
            ps.setString(4, b.getEquipmentType());
            ps.setString(5, b.getStatus().name());
            ps.executeUpdate();
        }
    }

    private int update(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            return ps.executeUpdate();
        }
    }

    private void execute(String sql, String p1, String p2) {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p1);
            ps.setString(2, p2);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Cannot execute statement", e);
        }
    }

    private ResourceBooking map(ResultSet rs) throws SQLException {
        return ResourceBooking.restore(
                rs.getString("id"),
                rs.getString("chain_id"),
                rs.getString("bed_id"),
                rs.getString("equipment_type"),
                BookingStatus.valueOf(rs.getString("status")));
    }
}