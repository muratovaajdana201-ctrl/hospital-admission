package hospital.facade;

import java.util.List;


public record AdmissionResult(String chainId, Outcome outcome, String message, List<String> undone) {

    public enum Outcome {
        COMPLETED,
        REJECTED,
        COMPENSATED
    }

    static AdmissionResult completed(String chainId) {
        return new AdmissionResult(chainId, Outcome.COMPLETED, "Patient admitted", List.of());
    }

    static AdmissionResult rejected(String chainId, String reason) {
        return new AdmissionResult(chainId, Outcome.REJECTED, reason, List.of());
    }

    static AdmissionResult compensated(String chainId, String reason, List<String> undone) {
        return new AdmissionResult(chainId, Outcome.COMPENSATED, reason, undone);
    }
}