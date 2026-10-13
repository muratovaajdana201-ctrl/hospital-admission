package hospital.facade;

import hospital.clinical.OrderStatus;
import hospital.facade.AdmissionResult.Outcome;
import hospital.registration.RegistrationStatus;
import hospital.resources.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class HospitalAdmissionFacadeTest {

    @TempDir
    Path tempDir;

    private AdmissionScenario hospital;

    @BeforeEach
    void setUp() {
        hospital = AdmissionScenario.inHospital(tempDir);
    }



    @Test
    void healthyPatientGoesThroughAllThreeSteps() {
        hospital.whenAdmitted()
                .thenOutcomeIs(Outcome.COMPLETED)
                .thenChainIs(ChainState.COMPLETED)
                .thenRegistrationIs(RegistrationStatus.REGISTERED)
                .thenBookingIs(BookingStatus.ALLOCATED)
                .thenOrderIs(OrderStatus.CONFIRMED);
    }



    @Test
    void expiredInsuranceIsRejectedAtStepAAndNothingIsStored() {
        hospital.patientWithPolicy("POL-EXPIRED")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.REJECTED)
                .thenChainIs(ChainState.REJECTED)
                .thenNoRegistration()
                .thenNoBooking()
                .thenNoOrder();
    }



    @Test
    void noIcuBed_afterA_compensatesOnlyA() {
        hospital.givenIcuBedTaken()
                .needsWard("ICU")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.COMPENSATED)
                .thenUndoneStepsAre("A")
                .thenChainIs(ChainState.COMPENSATED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenNoBooking()
                .thenNoOrder();
    }

    @Test
    void noVentilator_afterA_rollsBackBedTransactionAndCompensatesA() {
        hospital.givenNoVentilators()
                .needsWard("ICU").needsEquipment("VENTILATOR")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.COMPENSATED)
                .thenUndoneStepsAre("A")
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenNoBooking()
                .thenFreeBeds("ICU", 1);
    }

    @Test
    void unknownWard_afterA_isStillCompensated() {

        hospital.needsWard("VIP")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.COMPENSATED)
                .thenUndoneStepsAre("A")
                .thenRegistrationIs(RegistrationStatus.CANCELLED);
    }



    @Test
    void allergyConflict_afterAandB_compensatesBThenA() {
        hospital.needsWard("ICU").needsEquipment("VENTILATOR")
                .prescribed("AMOXICILLIN").allergicTo("PENICILLIN")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.COMPENSATED)
                .thenUndoneStepsAre("B", "A")
                .thenChainIs(ChainState.COMPENSATED)
                .thenBookingIs(BookingStatus.RELEASED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenNoOrder()
                .thenFreeBeds("ICU", 1)
                .thenEquipmentLeft("VENTILATOR", 1)
                .thenStockLeft("AMOXICILLIN", 5);
    }

    @Test
    void pharmacyOutOfStock_afterAandB_compensatesBThenA() {
        hospital.givenPharmacyOutOf("PARACETAMOL")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.COMPENSATED)
                .thenUndoneStepsAre("B", "A")
                .thenBookingIs(BookingStatus.RELEASED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenNoOrder()
                .thenFreeBeds("GENERAL", 2);
    }

    @Test
    void unknownLabTest_afterAandB_compensatesBThenA() {
        hospital.withLabTest("MAGIC")
                .whenAdmitted()
                .thenOutcomeIs(Outcome.COMPENSATED)
                .thenUndoneStepsAre("B", "A");
    }



    @Test
    void crashAfterB_chainSurvivesRestartAndIsRecoveredFromDisk() {
        hospital.crashingAfter("B")
                .whenAdmissionCrashes()
                .thenChainIs(ChainState.PENDING_C)
                .thenBookingIs(BookingStatus.ALLOCATED)

                .whenSystemRestarts()
                .thenChainIs(ChainState.PENDING_C)
                .thenBookingIs(BookingStatus.ALLOCATED)

                .whenRecovering()
                .thenRecoveredChains(1)
                .thenChainIs(ChainState.COMPENSATED)
                .thenBookingIs(BookingStatus.RELEASED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenFreeBeds("GENERAL", 2);
    }

    @Test
    void crashAfterA_recoveryCancelsOnlyRegistration() {
        hospital.crashingAfter("A")
                .whenAdmissionCrashes()
                .thenChainIs(ChainState.PENDING_B)
                .whenSystemRestarts()
                .whenRecovering()
                .thenRecoveredChains(1)
                .thenChainIs(ChainState.COMPENSATED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenNoBooking();
    }

    @Test
    void crashInTheMiddleOfCompensation_isFinishedAfterRestartWithoutDoubleRelease() {
        hospital.needsWard("ICU").needsEquipment("VENTILATOR")
                .prescribed("AMOXICILLIN").allergicTo("PENICILLIN")
                .crashingAfter("undo-B")                       // C упал, B освобождён, потом "краш"
                .whenAdmissionCrashes()
                .thenChainIs(ChainState.COMPENSATING)
                .thenBookingIs(BookingStatus.RELEASED)
                .thenRegistrationIs(RegistrationStatus.REGISTERED)   // A ещё не отменён

                .whenSystemRestarts()
                .whenRecovering()
                .thenChainIs(ChainState.COMPENSATED)
                .thenRegistrationIs(RegistrationStatus.CANCELLED)
                .thenEquipmentLeft("VENTILATOR", 1);           // ИВЛ вернули ровно один раз
    }

    @Test
    void recoveryLeavesFinishedChainsAlone() {
        hospital.whenAdmitted()
                .whenRecovering()
                .thenRecoveredChains(0)
                .thenChainIs(ChainState.COMPLETED)
                .thenBookingIs(BookingStatus.ALLOCATED);
    }
}