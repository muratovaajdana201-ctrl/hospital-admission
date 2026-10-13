package hospital.clinical;

import hospital.exceptions.InvalidPatientDataException;
import hospital.exceptions.PharmacyRejectedException;

import java.util.List;
import java.util.Map;
import java.util.Set;


public class DrugSafetyChecker {


    private static final Map<String, String> DRUG_CLASS = Map.of(
            "AMOXICILLIN", "PENICILLIN",
            "PARACETAMOL", "ANALGESIC",
            "IBUPROFEN", "NSAID",
            "ASPIRIN", "NSAID");

    private static final Set<String> LAB_TESTS =
            Set.of(ClinicalOrder.NONE, "BLOOD", "XRAY", "MRI");

    public void validate(String labTest, String medication) {
        if (labTest == null || !LAB_TESTS.contains(labTest)) {
            throw new InvalidPatientDataException("Unknown lab test: " + labTest);
        }
        boolean knownMedication = ClinicalOrder.NONE.equals(medication)
                || (medication != null && DRUG_CLASS.containsKey(medication));
        if (!knownMedication) {
            throw new InvalidPatientDataException("Unknown medication: " + medication);
        }
    }

    public void checkSafe(String medication, List<String> allergies) {
        if (ClinicalOrder.NONE.equals(medication) || allergies == null) {
            return;
        }
        String drugClass = DRUG_CLASS.get(medication);
        for (String allergy : allergies) {
            String normalized = allergy.trim().toUpperCase();
            if (normalized.equals(drugClass) || normalized.equals(medication)) {
                throw new PharmacyRejectedException(
                        "Patient is allergic to " + normalized + ", cannot dispense " + medication);
            }
        }
    }
}