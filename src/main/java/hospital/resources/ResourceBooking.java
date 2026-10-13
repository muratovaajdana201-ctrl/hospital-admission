package hospital.resources;

import java.util.UUID;

public class ResourceBooking {
    public static final String NO_EQUIPMENT = "NONE";

    private final String id;
    private final String chainId;
    private final String bedId;
    private final String equipmentType;
    private BookingStatus status;

    public ResourceBooking(String chainId, String bedId, String equipmentType) {
        this(UUID.randomUUID().toString(), chainId, bedId, equipmentType, BookingStatus.PENDING);
    }

    private ResourceBooking(String id, String chainId, String bedId,
                            String equipmentType, BookingStatus status) {
        this.id = id;
        this.chainId = chainId;
        this.bedId = bedId;
        this.equipmentType = equipmentType;
        this.status = status;
    }


    public static ResourceBooking restore(String id, String chainId, String bedId,
                                          String equipmentType, BookingStatus status) {
        return new ResourceBooking(id, chainId, bedId, equipmentType, status);
    }

    public void moveTo(BookingStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Illegal transition " + status + " -> " + next);
        }
        status = next;
    }

    public boolean usesEquipment() {
        return !NO_EQUIPMENT.equals(equipmentType);
    }

    public String getId() { return id; }
    public String getChainId() { return chainId; }
    public String getBedId() { return bedId; }
    public String getEquipmentType() { return equipmentType; }
    public BookingStatus getStatus() { return status; }
}