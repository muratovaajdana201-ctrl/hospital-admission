package hospital.facade;

import java.util.List;


public record AdmissionRequest(String patientName, int age, String policyNumber,
                               double estimatedCost, String wardType, String equipmentType,
                               String labTest, String medication, List<String> allergies) {
}