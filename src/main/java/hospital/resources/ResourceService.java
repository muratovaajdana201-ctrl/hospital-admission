package hospital.resources;

import java.util.List;
import java.util.Optional;


public class ResourceService {
    private final ResourceRepository repository;
    private final BedAllocator allocator;

    public ResourceService(ResourceRepository repository, BedAllocator allocator) {
        this.repository = repository;
        this.allocator = allocator;
    }


    public ResourceBooking allocate(String chainId, String wardType, String equipmentType) {
        allocator.validate(wardType, equipmentType);

        String bedId = allocator.choose(wardType, repository.freeBedIds(wardType));

        ResourceBooking booking = new ResourceBooking(chainId, bedId, equipmentType);
        booking.moveTo(BookingStatus.ALLOCATED);
        repository.allocate(booking);
        return booking;
    }


    public void releaseByChain(String chainId) {
        repository.findByChainId(chainId).ifPresent(booking -> {
            if (booking.getStatus() == BookingStatus.ALLOCATED) {
                booking.moveTo(BookingStatus.RELEASED);
                repository.release(booking);
            }
        });
    }

    public Optional<ResourceBooking> findByChain(String chainId) {
        return repository.findByChainId(chainId);
    }

    public List<ResourceBooking> findAll() {
        return repository.findAll();
    }



    public void addBed(String bedId, String wardType) {
        repository.saveBed(bedId, wardType);
    }

    public void setEquipmentStock(String equipmentType, int available) {
        repository.setEquipmentStock(equipmentType, available);
    }

    public int freeBeds(String wardType) {
        return repository.freeBedIds(wardType).size();
    }

    public int equipmentLeft(String equipmentType) {
        return repository.equipmentAvailable(equipmentType);
    }
}