package hospital.resources;


public enum BookingStatus {
    PENDING,
    ALLOCATED,
    RELEASED;

    public boolean canMoveTo(BookingStatus next) {
        return switch (this) {
            case PENDING   -> next == ALLOCATED || next == RELEASED;
            case ALLOCATED -> next == RELEASED;
            case RELEASED  -> false;
        };
    }
}