package hospital.registration;

import java.util.UUID;


public class Registration {
    private final String id;
    private final String chainId;
    private final String patientName;
    private final int age;
    private final String policyNumber;
    private final double estimatedCost;
    private RegistrationStatus status;

    public Registration(String chainId, String patientName, int age,
                        String policyNumber, double estimatedCost) {
        this(UUID.randomUUID().toString(), chainId, patientName, age,
                policyNumber, estimatedCost, RegistrationStatus.PENDING);
    }

    private Registration(String id, String chainId, String patientName, int age,
                         String policyNumber, double estimatedCost, RegistrationStatus status) {
        this.id = id;
        this.chainId = chainId;
        this.patientName = patientName;
        this.age = age;
        this.policyNumber = policyNumber;
        this.estimatedCost = estimatedCost;
        this.status = status;
    }

    /** Восстановление из БД, когда статус уже известен. */
    public static Registration restore(String id, String chainId, String patientName, int age,
                                       String policyNumber, double estimatedCost,
                                       RegistrationStatus status) {
        return new Registration(id, chainId, patientName, age, policyNumber, estimatedCost, status);
    }

    public void moveTo(RegistrationStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Illegal transition " + status + " -> " + next);
        }
        status = next;
    }

    public String getId() { return id; }
    public String getChainId() { return chainId; }
    public String getPatientName() { return patientName; }
    public int getAge() { return age; }
    public String getPolicyNumber() { return policyNumber; }
    public double getEstimatedCost() { return estimatedCost; }
    public RegistrationStatus getStatus() { return status; }
}