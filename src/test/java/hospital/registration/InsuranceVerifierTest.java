package hospital.registration;

import hospital.exceptions.InsuranceRejectedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class InsuranceVerifierTest {

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC);
    private final InsuranceVerifier verifier = new InsuranceVerifier(fixedClock);

    @Test
    void acceptsValidPolicyCoveringTheCost() {
        assertDoesNotThrow(() ->
                verifier.verify(5000.0, LocalDate.of(2027, 1, 1), 3000.0));
    }

    @Test
    void acceptsCostEqualToLimit() {
        assertDoesNotThrow(() ->
                verifier.verify(3000.0, LocalDate.of(2027, 1, 1), 3000.0));
    }

    @Test
    void rejectsExpiredPolicy() {
        InsuranceRejectedException e = assertThrows(InsuranceRejectedException.class, () ->
                verifier.verify(5000.0, LocalDate.of(2026, 10, 12), 100.0));
        assertTrue(e.getMessage().contains("expired"));
    }

    @Test
    void rejectsWhenCostExceedsCoverage() {
        assertThrows(InsuranceRejectedException.class, () ->
                verifier.verify(1000.0, LocalDate.of(2027, 1, 1), 1000.01));
    }
}