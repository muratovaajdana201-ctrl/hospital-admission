package hospital.exceptions;


public class InsuranceRejectedException extends AdmissionException {
    public InsuranceRejectedException(String message) {
        super(message);
    }
}