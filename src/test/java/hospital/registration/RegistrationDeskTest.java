package hospital.registration;

import hospital.exceptions.InsuranceRejectedException;
import hospital.exceptions.InvalidPatientDataException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationDeskTest {

    @TempDir
    Path tempDir;

    private final Clock clock =
            Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC);

    private RegistrationRepository repository;
    private RegistrationDesk desk;

    @BeforeEach
    void setUp() {
        repository = new RegistrationRepository(tempDir.resolve("registration.db"));
        desk = new RegistrationDesk(repository, new InsuranceVerifier(clock));
        repository.savePolicy(new RegistrationRepository.PolicyRecord(
                "POL-OK", 10_000.0, LocalDate.of(2027, 12, 31)));
        repository.savePolicy(new RegistrationRepository.PolicyRecord(
                "POL-EXPIRED", 10_000.0, LocalDate.of(2025, 1, 1)));
        repository.savePolicy(new RegistrationRepository.PolicyRecord(
                "POL-SMALL", 500.0, LocalDate.of(2027, 12, 31)));
    }



    private Registration registerValidPatient(String chainId) {
        return desk.register(chainId, "Aigerim", 30, "POL-OK", 2_000.0);
    }

    private void assertRegistrationIs(String chainId, RegistrationStatus expected) {
        Registration stored = repository.findByChainId(chainId).orElseThrow();
        assertEquals(expected, stored.getStatus());
    }

    private void assertNothingStoredFor(String chainId) {
        assertTrue(repository.findByChainId(chainId).isEmpty());
    }



    @Test
    void validPatientBecomesRegisteredAndIsPersisted() {
        registerValidPatient("c1");
        assertRegistrationIs("c1", RegistrationStatus.REGISTERED);
    }

    @Test
    void registeredDataSurvivesRestart() {
        registerValidPatient("c1");

        RegistrationRepository afterRestart =
                new RegistrationRepository(tempDir.resolve("registration.db"));

        Registration stored = afterRestart.findByChainId("c1").orElseThrow();
        assertEquals(RegistrationStatus.REGISTERED, stored.getStatus());
        assertEquals("Aigerim", stored.getPatientName());
    }

    @Test
    void expiredPolicyIsRejectedAndNothingIsStored() {
        assertThrows(InsuranceRejectedException.class,
                () -> desk.register("c2", "Aigerim", 30, "POL-EXPIRED", 100.0));
        assertNothingStoredFor("c2");
    }

    @Test
    void costAboveLimitIsRejectedAndNothingIsStored() {
        assertThrows(InsuranceRejectedException.class,
                () -> desk.register("c3", "Aigerim", 30, "POL-SMALL", 5_000.0));
        assertNothingStoredFor("c3");
    }

    @Test
    void unknownPolicyIsRejected() {
        assertThrows(InsuranceRejectedException.class,
                () -> desk.register("c4", "Aigerim", 30, "POL-NOPE", 100.0));
        assertNothingStoredFor("c4");
    }

    @Test
    void invalidInputIsRejectedBeforeAnythingIsStored() {
        assertThrows(InvalidPatientDataException.class,
                () -> desk.register("c5", "  ", 30, "POL-OK", 100.0));
        assertThrows(InvalidPatientDataException.class,
                () -> desk.register("c5", "Aigerim", -1, "POL-OK", 100.0));
        assertThrows(InvalidPatientDataException.class,
                () -> desk.register("c5", "Aigerim", 30, "", 100.0));
        assertThrows(InvalidPatientDataException.class,
                () -> desk.register("c5", "Aigerim", 30, "POL-OK", 0.0));
        assertNothingStoredFor("c5");
    }

    @Test
    void cancelByChainCancelsRegistration() {
        registerValidPatient("c6");
        desk.cancelByChain("c6");
        assertRegistrationIs("c6", RegistrationStatus.CANCELLED);
    }

    @Test
    void cancelByChainIsIdempotent() {
        registerValidPatient("c7");
        desk.cancelByChain("c7");
        assertDoesNotThrow(() -> desk.cancelByChain("c7"));
        assertRegistrationIs("c7", RegistrationStatus.CANCELLED);
    }

    @Test
    void cancelByChainForUnknownChainDoesNothing() {
        assertDoesNotThrow(() -> desk.cancelByChain("no-such-chain"));
    }

    @Test
    void findByChainReturnsStoredRegistration() {
        registerValidPatient("c8");
        assertTrue(desk.findByChain("c8").isPresent());
        assertTrue(desk.findByChain("zzz").isEmpty());
    }
}