package hospital.facade;

import hospital.exceptions.ChainNotFoundException;
import hospital.facade.AdmissionResult.Outcome;
import hospital.registration.RegistrationStatus;
import hospital.resources.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdmissionAdminFacadeTest {

    @TempDir
    Path tempDir;

    private AdmissionScenario hospital;

    @BeforeEach
    void setUp() {
        hospital = AdmissionScenario.inHospital(tempDir);
    }

    @Test
    void inspectShowsStatusInAllThreeSubsystems() {
        hospital.whenAdmitted();

        AdmissionAdminFacade.ChainReport report = hospital.admin().inspect(hospital.chainId());

        assertEquals(ChainState.COMPLETED, report.chain().state());
        assertEquals("REGISTERED", report.registration());
        assertEquals("ALLOCATED", report.booking());
        assertEquals("CONFIRMED", report.order());
    }

    @Test
    void inspectShowsDashForMissingRecords() {
        hospital.patientWithPolicy("POL-EXPIRED").whenAdmitted();

        AdmissionAdminFacade.ChainReport report = hospital.admin().inspect(hospital.chainId());

        assertEquals("-", report.registration());
        assertEquals("-", report.booking());
        assertEquals("-", report.order());
    }

    @Test
    void inspectUnknownChainFails() {
        assertThrows(ChainNotFoundException.class, () -> hospital.admin().inspect("nope"));
    }

    @Test
    void manualCompensationUndoesCompletedAdmissionInReverseOrder() {
        hospital.whenAdmitted();
        String id = hospital.chainId();

        List<String> undone = hospital.admin().compensateManually(id);

        assertEquals(3, undone.size());
        assertTrue(undone.get(0).startsWith("C"));
        assertTrue(undone.get(1).startsWith("B"));
        assertTrue(undone.get(2).startsWith("A"));
        hospital.thenChainIs(ChainState.COMPENSATED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenBookingIs(BookingStatus.RELEASED)
                .thenStockLeft("PARACETAMOL", 5);
    }

    @Test
    void manualCompensationIsIdempotent() {
        hospital.whenAdmitted();
        String id = hospital.chainId();
        hospital.admin().compensateManually(id);

        assertTrue(hospital.admin().compensateManually(id).isEmpty());
        hospital.thenChainIs(ChainState.COMPENSATED);
    }

    @Test
    void manualCompensationOfRejectedChainDoesNothing() {
        hospital.patientWithPolicy("POL-EXPIRED").whenAdmitted();

        assertTrue(hospital.admin().compensateManually(hospital.chainId()).isEmpty());
        hospital.thenChainIs(ChainState.REJECTED);
    }

    @Test
    void manualCompensationOfUnknownChainFails() {
        assertThrows(ChainNotFoundException.class,
                () -> hospital.admin().compensateManually("nope"));
    }

    @Test
    void listChainsReturnsEveryAdmission() {
        hospital.whenAdmitted().whenAdmitted();
        assertEquals(2, hospital.admin().listChains().size());
    }

    @Test
    void inventoryReflectsBedsEquipmentAndPharmacy() {
        Map<String, Integer> before = hospital.admin().inventory();
        assertEquals(2, before.get("Free GENERAL beds"));
        assertEquals(1, before.get("Free ICU beds"));
        assertEquals(1, before.get("VENTILATOR"));
        assertEquals(5, before.get("PARACETAMOL"));
    }

    @Test
    void policySeedingIsVisible() {
        assertTrue(hospital.admin().hasPolicy("POL-OK"));
        assertFalse(hospital.admin().hasPolicy("POL-NOPE"));
    }

    @Test
    void outcomeOfCompletedAdmissionIsCompleted() {
        hospital.whenAdmitted().thenOutcomeIs(Outcome.COMPLETED);
    }
}