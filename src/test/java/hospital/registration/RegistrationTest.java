package hospital.registration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationTest {

    private Registration newRegistration() {
        return new Registration("chain-1", "Aigerim", 30, "POL-1", 1000.0);
    }

    @Test
    void newRegistrationStartsPending() {
        assertEquals(RegistrationStatus.PENDING, newRegistration().getStatus());
    }

    @Test
    void legalTransitionChangesStatus() {
        Registration r = newRegistration();
        r.moveTo(RegistrationStatus.REGISTERED);
        assertEquals(RegistrationStatus.REGISTERED, r.getStatus());
    }

    @Test
    void illegalTransitionIsRejected() {
        Registration r = newRegistration();
        r.moveTo(RegistrationStatus.CANCELLED);
        assertThrows(IllegalStateException.class,
                () -> r.moveTo(RegistrationStatus.REGISTERED));
    }
}