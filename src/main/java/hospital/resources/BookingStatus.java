package hospital.resources;

/** Жизненный цикл брони ресурсов. */
public enum BookingStatus {
    PENDING,     // создана, ресурсы ещё не заняты
    ALLOCATED,   // койка и оборудование заняты
    RELEASED;    // ресурсы освобождены (финальный статус)

    public boolean canMoveTo(BookingStatus next) {
        return switch (this) {
            case PENDING   -> next == ALLOCATED || next == RELEASED;
            case ALLOCATED -> next == RELEASED;
            case RELEASED  -> false;
        };
    }
}