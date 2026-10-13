package hospital.clinical;

/** Жизненный цикл клинического заказа (анализ + лекарство). */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case PENDING   -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == CANCELLED;
            case CANCELLED -> false;
        };
    }
}