package hospital.clinical;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalOrderTest {

    private ClinicalOrder newOrder() {
        return new ClinicalOrder("chain-1", "BLOOD", "PARACETAMOL");
    }

    @Test
    void newOrderStartsPending() {
        assertEquals(OrderStatus.PENDING, newOrder().getStatus());
    }

    @Test
    void legalTransitionChangesStatus() {
        ClinicalOrder o = newOrder();
        o.moveTo(OrderStatus.CONFIRMED);
        assertEquals(OrderStatus.CONFIRMED, o.getStatus());
    }

    @Test
    void illegalTransitionIsRejected() {
        ClinicalOrder o = newOrder();
        o.moveTo(OrderStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> o.moveTo(OrderStatus.CONFIRMED));
    }

    @Test
    void orderWithoutMedicationDoesNotUseMedication() {
        ClinicalOrder o = new ClinicalOrder("c", "BLOOD", ClinicalOrder.NONE);
        assertFalse(o.usesMedication());
        assertTrue(newOrder().usesMedication());
    }
}