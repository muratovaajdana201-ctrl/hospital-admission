package hospital.resources;

import hospital.exceptions.EquipmentUnavailableException;
import hospital.exceptions.InvalidPatientDataException;
import hospital.exceptions.NoBedAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourceServiceTest {

    @TempDir
    Path tempDir;

    private ResourceRepository repository;
    private ResourceService service;

    @BeforeEach
    void setUp() {
        repository = new ResourceRepository(tempDir.resolve("resources.db"));
        service = new ResourceService(repository, new BedAllocator());
        repository.saveBed("G1", "GENERAL");
        repository.saveBed("G2", "GENERAL");
        repository.saveBed("I1", "ICU");
        repository.setEquipmentStock("VENTILATOR", 1);
        repository.setEquipmentStock("MONITOR", 2);
    }

    // ---------- маленький тестовый язык (DSL) ----------

    private ResourceBooking allocateGeneralBed(String chainId) {
        return service.allocate(chainId, "GENERAL", "NONE");
    }

    private ResourceBooking allocateIcuWithVentilator(String chainId) {
        return service.allocate(chainId, "ICU", "VENTILATOR");
    }

    private void assertBedOccupied(String bedId) {
        assertTrue(repository.isBedOccupied(bedId), "bed " + bedId + " should be occupied");
    }

    private void assertBedFree(String bedId) {
        assertFalse(repository.isBedOccupied(bedId), "bed " + bedId + " should be free");
    }

    private void assertEquipmentLeft(String type, int expected) {
        assertEquals(expected, repository.equipmentAvailable(type));
    }

    private void assertBookingIs(String chainId, BookingStatus expected) {
        assertEquals(expected, repository.findByChainId(chainId).orElseThrow().getStatus());
    }

    private void assertNoBookingFor(String chainId) {
        assertTrue(repository.findByChainId(chainId).isEmpty());
    }

    // ---------- тесты ----------

    @Test
    void generalBedIsAllocatedAndMarkedOccupied() {
        ResourceBooking b = allocateGeneralBed("c1");
        assertEquals("G1", b.getBedId());
        assertBedOccupied("G1");
        assertBookingIs("c1", BookingStatus.ALLOCATED);
    }

    @Test
    void allocationSurvivesRestart() {
        allocateIcuWithVentilator("c1");

        // "Перезапуск": новый репозиторий на том же файле
        ResourceRepository afterRestart = new ResourceRepository(tempDir.resolve("resources.db"));

        assertEquals(BookingStatus.ALLOCATED,
                afterRestart.findByChainId("c1").orElseThrow().getStatus());
        assertTrue(afterRestart.isBedOccupied("I1"));
        assertEquals(0, afterRestart.equipmentAvailable("VENTILATOR"));
    }

    @Test
    void equipmentStockIsDecreasedOnAllocation() {
        allocateIcuWithVentilator("c1");
        assertEquipmentLeft("VENTILATOR", 0);
    }

    @Test
    void bookingWithoutEquipmentDoesNotTouchStock() {
        allocateGeneralBed("c1");
        assertEquipmentLeft("VENTILATOR", 1);
        assertEquipmentLeft("MONITOR", 2);
    }

    @Test
    void secondIcuPatientFindsNoBedAndNothingIsStored() {
        allocateIcuWithVentilator("c1");
        assertThrows(NoBedAvailableException.class,
                () -> service.allocate("c2", "ICU", "MONITOR"));
        assertNoBookingFor("c2");
        assertEquipmentLeft("MONITOR", 2);   // оборудование не тронуто
    }

    @Test
    void missingEquipmentRollsBackTheBedInTheSameTransaction() {
        repository.setEquipmentStock("VENTILATOR", 0);

        assertThrows(EquipmentUnavailableException.class,
                () -> allocateIcuWithVentilator("c1"));

        assertBedFree("I1");        // койку НЕ оставили занятой: транзакция откатилась
        assertNoBookingFor("c1");
    }

    @Test
    void releaseFreesBedAndReturnsEquipment() {
        allocateIcuWithVentilator("c1");

        service.releaseByChain("c1");

        assertBedFree("I1");
        assertEquipmentLeft("VENTILATOR", 1);
        assertBookingIs("c1", BookingStatus.RELEASED);
    }

    @Test
    void releaseIsIdempotent() {
        allocateIcuWithVentilator("c1");
        service.releaseByChain("c1");

        assertDoesNotThrow(() -> service.releaseByChain("c1"));

        assertEquipmentLeft("VENTILATOR", 1);   // оборудование не вернулось дважды
        assertBookingIs("c1", BookingStatus.RELEASED);
    }

    @Test
    void releaseOfUnknownChainDoesNothing() {
        assertDoesNotThrow(() -> service.releaseByChain("no-such-chain"));
    }

    @Test
    void releasedBedCanBeAllocatedAgain() {
        allocateIcuWithVentilator("c1");
        service.releaseByChain("c1");

        ResourceBooking again = allocateIcuWithVentilator("c2");

        assertEquals("I1", again.getBedId());
    }

    @Test
    void unknownWardIsRejectedBeforeAnythingIsStored() {
        assertThrows(InvalidPatientDataException.class,
                () -> service.allocate("c1", "VIP", "NONE"));
        assertNoBookingFor("c1");
    }

    @Test
    void findMethodsReturnStoredBookings() {
        allocateGeneralBed("c1");
        allocateGeneralBed("c2");
        assertTrue(service.findByChain("c1").isPresent());
        assertTrue(service.findByChain("zzz").isEmpty());
        assertEquals(2, service.findAll().size());
    }
}