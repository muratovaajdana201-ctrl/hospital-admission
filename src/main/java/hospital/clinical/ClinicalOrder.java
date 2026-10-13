package hospital.clinical;

import java.util.UUID;

/** Сущность подсистемы C. Сама защищает переходы статусов. */
public class ClinicalOrder {
    public static final String NONE = "NONE";

    private final String id;
    private final String chainId;
    private final String labTest;      // "NONE", если анализ не нужен
    private final String medication;   // "NONE", если лекарство не нужно
    private OrderStatus status;

    public ClinicalOrder(String chainId, String labTest, String medication) {
        this(UUID.randomUUID().toString(), chainId, labTest, medication, OrderStatus.PENDING);
    }

    private ClinicalOrder(String id, String chainId, String labTest,
                          String medication, OrderStatus status) {
        this.id = id;
        this.chainId = chainId;
        this.labTest = labTest;
        this.medication = medication;
        this.status = status;
    }

    /** Восстановление из БД. */
    public static ClinicalOrder restore(String id, String chainId, String labTest,
                                        String medication, OrderStatus status) {
        return new ClinicalOrder(id, chainId, labTest, medication, status);
    }

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Illegal transition " + status + " -> " + next);
        }
        status = next;
    }

    public boolean usesMedication() {
        return !NONE.equals(medication);
    }

    public String getId() { return id; }
    public String getChainId() { return chainId; }
    public String getLabTest() { return labTest; }
    public String getMedication() { return medication; }
    public OrderStatus getStatus() { return status; }
}