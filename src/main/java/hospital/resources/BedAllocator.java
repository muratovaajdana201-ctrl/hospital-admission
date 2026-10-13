package hospital.resources;

import hospital.exceptions.InvalidPatientDataException;
import hospital.exceptions.NoBedAvailableException;

import java.util.List;
import java.util.Set;

/** Чистая логика: проверяет запрос и выбирает койку. Не знает про БД. */
public class BedAllocator {
    public static final String GENERAL = "GENERAL";
    public static final String ICU = "ICU";

    private static final Set<String> WARDS = Set.of(GENERAL, ICU);
    private static final Set<String> EQUIPMENT =
            Set.of(ResourceBooking.NO_EQUIPMENT, "VENTILATOR", "MONITOR", "WHEELCHAIR");

    public void validate(String wardType, String equipmentType) {
        if (wardType == null || !WARDS.contains(wardType)) {
            throw new InvalidPatientDataException("Unknown ward type: " + wardType);
        }
        if (equipmentType == null || !EQUIPMENT.contains(equipmentType)) {
            throw new InvalidPatientDataException("Unknown equipment type: " + equipmentType);
        }
    }

    /** Из свободных коек нужной палаты берёт первую по алфавиту. Пусто, значит, исключение. */
    public String choose(String wardType, List<String> freeBedIds) {
        return freeBedIds.stream()
                .sorted()
                .findFirst()
                .orElseThrow(() -> new NoBedAvailableException("No free " + wardType + " bed"));
    }
}