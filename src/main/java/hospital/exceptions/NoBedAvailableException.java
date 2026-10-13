package hospital.exceptions;


public class NoBedAvailableException extends AdmissionException {
    public NoBedAvailableException(String message) {
        super(message);
    }
}