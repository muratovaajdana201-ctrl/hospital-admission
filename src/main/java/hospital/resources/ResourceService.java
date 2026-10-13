package hospital.resources;

import java.util.List;
import java.util.Optional;

/** Сервис подсистемы B: выделяет и освобождает койки и оборудование. */
public class ResourceService {
    private final ResourceRepository repository;
    private final BedAllocator allocator;

    public ResourceService(ResourceRepository repository, BedAllocator allocator) {
        this.repository = repository;
        this.allocator = allocator;
    }

    /** Шаг B цепочки. */
    public ResourceBooking allocate(String chainId, String wardType, String equipmentType) {
        allocator.validate(wardType, equipmentType);

        String bedId = allocator.choose(wardType, repository.freeBedIds(wardType));

        ResourceBooking booking = new ResourceBooking(chainId, bedId, equipmentType);
        booking.moveTo(BookingStatus.ALLOCATED);
        repository.allocate(booking);   // транзакция: койка + оборудование + бронь
        return booking;
    }

    /** Компенсация шага B. Идемпотентна: нет брони или уже освобождена, значит ничего не делаем. */
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
}