package hospital.clinical;

import org.junit.jupiter.api.Test;

import static hospital.clinical.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderStatusTest {

    @Test
    void pendingCanBeConfirmedOrCancelled() {
        assertTrue(PENDING.canMoveTo(CONFIRMED));
        assertTrue(PENDING.canMoveTo(CANCELLED));
    }

    @Test
    void confirmedCanOnlyBeCancelled() {
        assertTrue(CONFIRMED.canMoveTo(CANCELLED));
        assertFalse(CONFIRMED.canMoveTo(PENDING));
        assertFalse(CONFIRMED.canMoveTo(CONFIRMED));
    }

    @Test
    void cancelledIsFinal() {
        assertFalse(CANCELLED.canMoveTo(PENDING));
        assertFalse(CANCELLED.canMoveTo(CONFIRMED));
        assertFalse(CANCELLED.canMoveTo(CANCELLED));
    }
}