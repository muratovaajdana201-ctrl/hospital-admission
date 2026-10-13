package hospital.resources;

import hospital.exceptions.InvalidPatientDataException;
import hospital.exceptions.NoBedAvailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BedAllocatorTest {

    private final BedAllocator allocator = new BedAllocator();

    @Test
    void choosesFirstBedAlphabetically() {
        assertEquals("G1", allocator.choose("GENERAL", List.of("G3", "G1", "G2")));
    }

    @Test
    void throwsWhenNoFreeBeds() {
        assertThrows(NoBedAvailableException.class,
                () -> allocator.choose("ICU", List.of()));
    }

    @Test
    void acceptsKnownWardAndEquipment() {
        assertDoesNotThrow(() -> allocator.validate("ICU", "VENTILATOR"));
        assertDoesNotThrow(() -> allocator.validate("GENERAL", "NONE"));
    }

    @Test
    void rejectsUnknownWard() {
        assertThrows(InvalidPatientDataException.class,
                () -> allocator.validate("VIP", "NONE"));
        assertThrows(InvalidPatientDataException.class,
                () -> allocator.validate(null, "NONE"));
    }

    @Test
    void rejectsUnknownEquipment() {
        assertThrows(InvalidPatientDataException.class,
                () -> allocator.validate("ICU", "TELEPORT"));
        assertThrows(InvalidPatientDataException.class,
                () -> allocator.validate("ICU", null));
    }
}