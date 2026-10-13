package hospital.exceptions;

/** Нужное оборудование закончилось. Сбой шага B. */
public class EquipmentUnavailableException extends AdmissionException {
    public EquipmentUnavailableException(String message) {
        super(message);
    }
}