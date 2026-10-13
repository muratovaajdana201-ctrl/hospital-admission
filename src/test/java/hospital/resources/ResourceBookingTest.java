package hospital.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceBookingTest {

    private ResourceBooking newBooking() {
        return new ResourceBooking("chain-1", "G1", "MONITOR");
    }

    @Test
    void newBookingStartsPending() {
        assertEquals(BookingStatus.PENDING, newBooking().getStatus());
    }

    @Test
    void legalTransitionChangesStatus() {
        ResourceBooking b = newBooking();
        b.moveTo(BookingStatus.ALLOCATED);
        assertEquals(BookingStatus.ALLOCATED, b.getStatus());
    }

    @Test
    void illegalTransitionIsRejected() {
        ResourceBooking b = newBooking();
        b.moveTo(BookingStatus.RELEASED);
        assertThrows(IllegalStateException.class, () -> b.moveTo(BookingStatus.ALLOCATED));
    }

    @Test
    void noEquipmentBookingDoesNotUseEquipment() {
        ResourceBooking b = new ResourceBooking("c", "G1", ResourceBooking.NO_EQUIPMENT);
        assertFalse(b.usesEquipment());
        assertTrue(newBooking().usesEquipment());
    }
}