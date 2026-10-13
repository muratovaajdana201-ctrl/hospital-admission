package hospital.exceptions;

/** Аптека отказала: лекарство небезопасно для пациента или закончилось. Сбой шага C. */
public class PharmacyRejectedException extends AdmissionException {
    public PharmacyRejectedException(String message) {
        super(message);
    }
}