package hospital.facade;

import hospital.clinical.ClinicalOrderRepository;
import hospital.clinical.ClinicalOrderService;
import hospital.clinical.DrugSafetyChecker;
import hospital.clinical.OrderStatus;
import hospital.registration.InsuranceVerifier;
import hospital.registration.RegistrationDesk;
import hospital.registration.RegistrationRepository;
import hospital.registration.RegistrationStatus;
import hospital.resources.BedAllocator;
import hospital.resources.BookingStatus;
import hospital.resources.ResourceRepository;
import hospital.resources.ResourceService;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


final class AdmissionScenario {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-13T00:00:00Z"), ZoneOffset.UTC);

    private final Path dir;
    private CrashPoint crashPoint = CrashPoint.NONE;

    private RegistrationDesk registration;
    private ResourceService resources;
    private ClinicalOrderService clinical;
    private ChainRepository chains;
    private HospitalAdmissionFacade facade;
    private AdmissionAdminFacade admin;


    private String patient = "Aigerim";
    private int age = 30;
    private String policy = "POL-OK";
    private double cost = 2_000.0;
    private String ward = "GENERAL";
    private String equipment = "NONE";
    private String labTest = "BLOOD";
    private String medication = "PARACETAMOL";
    private List<String> allergies = List.of();

    private AdmissionResult result;
    private List<String> recovered = List.of();

    private AdmissionScenario(Path dir) {
        this.dir = dir;
    }

    static AdmissionScenario inHospital(Path dir) {
        AdmissionScenario scenario = new AdmissionScenario(dir);
        scenario.wire();
        scenario.seed();
        return scenario;
    }

    private void wire() {
        registration = new RegistrationDesk(
                new RegistrationRepository(dir.resolve("registration.db")),
                new InsuranceVerifier(CLOCK));
        resources = new ResourceService(
                new ResourceRepository(dir.resolve("resources.db")), new BedAllocator());
        clinical = new ClinicalOrderService(
                new ClinicalOrderRepository(dir.resolve("clinical.db")), new DrugSafetyChecker());
        chains = new ChainRepository(dir.resolve("chains.db"));
        facade = new HospitalAdmissionFacade(registration, resources, clinical, chains, crashPoint);
        admin = new AdmissionAdminFacade(registration, resources, clinical, chains);
    }

    private void seed() {
        admin.addPolicy("POL-OK", 10_000.0, LocalDate.of(2027, 12, 31));
        admin.addPolicy("POL-EXPIRED", 10_000.0, LocalDate.of(2025, 1, 1));
        admin.addPolicy("POL-SMALL", 500.0, LocalDate.of(2027, 12, 31));
        admin.addBed("G1", "GENERAL");
        admin.addBed("G2", "GENERAL");
        admin.addBed("I1", "ICU");
        admin.setEquipmentStock("VENTILATOR", 1);
        admin.setEquipmentStock("MONITOR", 2);
        admin.setPharmacyStock("PARACETAMOL", 5);
        admin.setPharmacyStock("AMOXICILLIN", 5);
    }



    AdmissionScenario patientWithPolicy(String policyNumber) {
        this.policy = policyNumber;
        return this;
    }

    AdmissionScenario needsWard(String wardType) {
        this.ward = wardType;
        return this;
    }

    AdmissionScenario needsEquipment(String equipmentType) {
        this.equipment = equipmentType;
        return this;
    }

    AdmissionScenario prescribed(String drug) {
        this.medication = drug;
        return this;
    }

    AdmissionScenario withLabTest(String test) {
        this.labTest = test;
        return this;
    }

    AdmissionScenario allergicTo(String... substances) {
        this.allergies = List.of(substances);
        return this;
    }


    AdmissionScenario givenIcuBedTaken() {
        resources.allocate("someone-else", "ICU", "NONE");
        return this;
    }

    AdmissionScenario givenNoVentilators() {
        admin.setEquipmentStock("VENTILATOR", 0);
        return this;
    }

    AdmissionScenario givenPharmacyOutOf(String drug) {
        admin.setPharmacyStock(drug, 0);
        return this;
    }


    AdmissionScenario crashingAfter(String step) {
        this.crashPoint = CrashPoint.throwAfter(step);
        wire();
        return this;
    }



    AdmissionScenario whenAdmitted() {
        result = facade.admitPatient(request());
        return this;
    }

    AdmissionScenario whenAdmissionCrashes() {
        assertThrows(SimulatedCrashException.class, () -> facade.admitPatient(request()));
        return this;
    }

    AdmissionScenario whenSystemRestarts() {
        crashPoint = CrashPoint.NONE;
        wire();
        return this;
    }

    AdmissionScenario whenRecovering() {
        recovered = admin.recoverPendingChains();
        return this;
    }



    AdmissionScenario thenOutcomeIs(AdmissionResult.Outcome expected) {
        assertEquals(expected, result.outcome());
        return this;
    }


    AdmissionScenario thenUndoneStepsAre(String... expectedPrefixes) {
        List<String> undone = result.undone();
        assertEquals(expectedPrefixes.length, undone.size(), "undone steps: " + undone);
        for (int i = 0; i < expectedPrefixes.length; i++) {
            assertTrue(undone.get(i).startsWith(expectedPrefixes[i]),
                    "step " + i + " should start with " + expectedPrefixes[i] + " but was " + undone.get(i));
        }
        return this;
    }

    AdmissionScenario thenChainIs(ChainState expected) {
        assertEquals(expected, theChain().state());
        return this;
    }

    AdmissionScenario thenRegistrationIs(RegistrationStatus expected) {
        assertEquals(expected, registration.findByChain(chainId()).orElseThrow().getStatus());
        return this;
    }

    AdmissionScenario thenNoRegistration() {
        assertTrue(registration.findByChain(chainId()).isEmpty());
        return this;
    }

    AdmissionScenario thenBookingIs(BookingStatus expected) {
        assertEquals(expected, resources.findByChain(chainId()).orElseThrow().getStatus());
        return this;
    }

    AdmissionScenario thenNoBooking() {
        assertTrue(resources.findByChain(chainId()).isEmpty());
        return this;
    }

    AdmissionScenario thenOrderIs(OrderStatus expected) {
        assertEquals(expected, clinical.findByChain(chainId()).orElseThrow().getStatus());
        return this;
    }

    AdmissionScenario thenNoOrder() {
        assertTrue(clinical.findByChain(chainId()).isEmpty());
        return this;
    }

    AdmissionScenario thenFreeBeds(String wardType, int expected) {
        assertEquals(expected, resources.freeBeds(wardType));
        return this;
    }

    AdmissionScenario thenEquipmentLeft(String type, int expected) {
        assertEquals(expected, resources.equipmentLeft(type));
        return this;
    }

    AdmissionScenario thenStockLeft(String drug, int expected) {
        assertEquals(expected, clinical.stockOf(drug));
        return this;
    }

    AdmissionScenario thenRecoveredChains(int expected) {
        assertEquals(expected, recovered.size());
        return this;
    }



    AdmissionAdminFacade admin() {
        return admin;
    }

    String chainId() {
        return theChain().chainId();
    }



    private ChainRecord theChain() {
        List<ChainRecord> all = chains.findAll();
        assertEquals(1, all.size(), "expected exactly one chain");
        return all.get(0);
    }

    private AdmissionRequest request() {
        return new AdmissionRequest(patient, age, policy, cost, ward, equipment,
                labTest, medication, allergies);
    }
}