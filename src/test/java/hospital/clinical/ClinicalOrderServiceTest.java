package hospital.clinical;

import hospital.exceptions.InvalidPatientDataException;
import hospital.exceptions.PharmacyRejectedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalOrderServiceTest {

    @TempDir
    Path tempDir;

    private ClinicalOrderRepository repository;
    private ClinicalOrderService service;

    @BeforeEach
    void setUp() {
        repository = new ClinicalOrderRepository(tempDir.resolve("clinical.db"));
        service = new ClinicalOrderService(repository, new DrugSafetyChecker());
        repository.setStock("PARACETAMOL", 2);
        repository.setStock("AMOXICILLIN", 2);
    }



    private ClinicalOrder orderParacetamol(String chainId) {
        return service.place(chainId, "BLOOD", "PARACETAMOL", List.of());
    }

    private void assertOrderIs(String chainId, OrderStatus expected) {
        assertEquals(expected, repository.findByChainId(chainId).orElseThrow().getStatus());
    }

    private void assertNoOrderFor(String chainId) {
        assertTrue(repository.findByChainId(chainId).isEmpty());
    }

    private void assertStockLeft(String medication, int expected) {
        assertEquals(expected, repository.stockOf(medication));
    }



    @Test
    void confirmedOrderIsPersisted() {
        orderParacetamol("c1");
        assertOrderIs("c1", OrderStatus.CONFIRMED);
    }

    @Test
    void orderSurvivesRestart() {
        orderParacetamol("c1");

        ClinicalOrderRepository afterRestart =
                new ClinicalOrderRepository(tempDir.resolve("clinical.db"));

        assertEquals(OrderStatus.CONFIRMED,
                afterRestart.findByChainId("c1").orElseThrow().getStatus());
        assertEquals(1, afterRestart.stockOf("PARACETAMOL"));
    }

    @Test
    void stockIsDecreasedWhenOrderIsPlaced() {
        orderParacetamol("c1");
        assertStockLeft("PARACETAMOL", 1);
    }

    @Test
    void orderWithoutMedicationDoesNotTouchStock() {
        service.place("c1", "XRAY", "NONE", List.of());
        assertStockLeft("PARACETAMOL", 2);
        assertOrderIs("c1", OrderStatus.CONFIRMED);
    }

    @Test
    void allergyConflictIsRejectedAndNothingIsStored() {
        assertThrows(PharmacyRejectedException.class,
                () -> service.place("c1", "BLOOD", "AMOXICILLIN", List.of("PENICILLIN")));
        assertNoOrderFor("c1");
        assertStockLeft("AMOXICILLIN", 2);   // склад не тронут
    }

    @Test
    void emptyPharmacyIsRejectedAndNothingIsStored() {
        repository.setStock("PARACETAMOL", 0);
        assertThrows(PharmacyRejectedException.class, () -> orderParacetamol("c1"));
        assertNoOrderFor("c1");
        assertStockLeft("PARACETAMOL", 0);
    }

    @Test
    void cancelReturnsMedicationToStock() {
        orderParacetamol("c1");
        service.cancelByChain("c1");
        assertOrderIs("c1", OrderStatus.CANCELLED);
        assertStockLeft("PARACETAMOL", 2);
    }

    @Test
    void cancelIsIdempotent() {
        orderParacetamol("c1");
        service.cancelByChain("c1");

        assertDoesNotThrow(() -> service.cancelByChain("c1"));

        assertStockLeft("PARACETAMOL", 2);   // лекарство не вернулось дважды
        assertOrderIs("c1", OrderStatus.CANCELLED);
    }

    @Test
    void cancelOfUnknownChainDoesNothing() {
        assertDoesNotThrow(() -> service.cancelByChain("no-such-chain"));
    }

    @Test
    void invalidLabTestIsRejectedBeforeAnythingIsStored() {
        assertThrows(InvalidPatientDataException.class,
                () -> service.place("c1", "MAGIC", "PARACETAMOL", List.of()));
        assertNoOrderFor("c1");
        assertStockLeft("PARACETAMOL", 2);
    }

    @Test
    void findMethodsReturnStoredOrders() {
        orderParacetamol("c1");
        orderParacetamol("c2");
        assertTrue(service.findByChain("c1").isPresent());
        assertTrue(service.findByChain("zzz").isEmpty());
        assertEquals(2, service.findAll().size());
    }
}