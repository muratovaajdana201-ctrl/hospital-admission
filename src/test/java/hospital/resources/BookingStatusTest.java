package hospital.resources;

import org.junit.jupiter.api.Test;

import static hospital.resources.BookingStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BookingStatusTest {

    @Test
    void pendingCanBeAllocatedOrReleased() {
        assertTrue(PENDING.canMoveTo(ALLOCATED));
        assertTrue(PENDING.canMoveTo(RELEASED));
    }

    @Test
    void allocatedCanOnlyBeReleased() {
        assertTrue(ALLOCATED.canMoveTo(RELEASED));
        assertFalse(ALLOCATED.canMoveTo(PENDING));
        assertFalse(ALLOCATED.canMoveTo(ALLOCATED));
    }

    @Test
    void releasedIsFinal() {
        assertFalse(RELEASED.canMoveTo(PENDING));
        assertFalse(RELEASED.canMoveTo(ALLOCATED));
        assertFalse(RELEASED.canMoveTo(RELEASED));
    }
}