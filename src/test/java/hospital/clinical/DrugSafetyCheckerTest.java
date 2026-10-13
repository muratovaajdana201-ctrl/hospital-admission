package hospital.clinical;

import hospital.exceptions.InvalidPatientDataException;
import hospital.exceptions.PharmacyRejectedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DrugSafetyCheckerTest {

    private final DrugSafetyChecker checker = new DrugSafetyChecker();

    @Test
    void acceptsKnownLabTestAndMedication() {
        assertDoesNotThrow(() -> checker.validate("BLOOD", "PARACETAMOL"));
    }

    @Test
    void acceptsNoneForBoth() {
        assertDoesNotThrow(() -> checker.validate("NONE", "NONE"));
    }

    @Test
    void rejectsUnknownLabTest() {
        assertThrows(InvalidPatientDataException.class,
                () -> checker.validate("MAGIC", "NONE"));
        assertThrows(InvalidPatientDataException.class,
                () -> checker.validate(null, "NONE"));
    }

    @Test
    void rejectsUnknownMedication() {
        assertThrows(InvalidPatientDataException.class,
                () -> checker.validate("NONE", "CANDY"));
        assertThrows(InvalidPatientDataException.class,
                () -> checker.validate("NONE", null));
    }

    @Test
    void rejectsDrugWhoseClassMatchesAllergy() {
        assertThrows(PharmacyRejectedException.class,
                () -> checker.checkSafe("AMOXICILLIN", List.of("PENICILLIN")));
    }

    @Test
    void rejectsDrugNamedDirectlyInAllergies() {
        assertThrows(PharmacyRejectedException.class,
                () -> checker.checkSafe("IBUPROFEN", List.of("IBUPROFEN")));
    }

    @Test
    void allergyCheckIgnoresCaseAndSpaces() {
        assertThrows(PharmacyRejectedException.class,
                () -> checker.checkSafe("AMOXICILLIN", List.of("  penicillin ")));
    }

    @Test
    void acceptsUnrelatedAllergy() {
        assertDoesNotThrow(() -> checker.checkSafe("PARACETAMOL", List.of("PENICILLIN")));
    }

    @Test
    void noMedicationIsAlwaysSafe() {
        assertDoesNotThrow(() -> checker.checkSafe("NONE", List.of("PENICILLIN")));
    }

    @Test
    void nullAllergiesAreTreatedAsEmpty() {
        assertDoesNotThrow(() -> checker.checkSafe("AMOXICILLIN", null));
    }
}