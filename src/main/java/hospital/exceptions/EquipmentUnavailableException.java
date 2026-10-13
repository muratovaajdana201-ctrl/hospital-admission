package hospital.exceptions;


public class EquipmentUnavailableException extends AdmissionException {
    public EquipmentUnavailableException(String message) {
        super(message);
    }
}