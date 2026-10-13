package hospital.exceptions;


public class InvalidPatientDataException extends AdmissionException {
    public InvalidPatientDataException(String message) {
        super(message);
    }
}