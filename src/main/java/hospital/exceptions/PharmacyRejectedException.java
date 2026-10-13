package hospital.exceptions;


public class PharmacyRejectedException extends AdmissionException {
    public PharmacyRejectedException(String message) {
        super(message);
    }
}