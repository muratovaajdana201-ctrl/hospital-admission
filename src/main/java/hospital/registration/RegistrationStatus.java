package hospital.registration;

public enum RegistrationStatus {
    PENDING,
    REGISTERED,
    CANCELLED;

    public boolean canMoveTo(RegistrationStatus next) {
        return switch (this) {
            case PENDING    -> next == REGISTERED || next == CANCELLED;
            case REGISTERED -> next == CANCELLED;
            case CANCELLED  -> false;
        };
    }
}