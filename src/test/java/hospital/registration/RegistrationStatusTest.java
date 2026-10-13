package hospital.registration;

import org.junit.jupiter.api.Test;

import static hospital.registration.RegistrationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistrationStatusTest {

    @Test
    void pendingCanBecomeRegisteredOrCancelled() {
        assertTrue(PENDING.canMoveTo(REGISTERED));
        assertTrue(PENDING.canMoveTo(CANCELLED));
    }

    @Test
    void registeredCanOnlyBeCancelled() {
        assertTrue(REGISTERED.canMoveTo(CANCELLED));
        assertFalse(REGISTERED.canMoveTo(PENDING));
        assertFalse(REGISTERED.canMoveTo(REGISTERED));
    }

    @Test
    void cancelledIsFinal() {
        assertFalse(CANCELLED.canMoveTo(PENDING));
        assertFalse(CANCELLED.canMoveTo(REGISTERED));
        assertFalse(CANCELLED.canMoveTo(CANCELLED));
    }
}