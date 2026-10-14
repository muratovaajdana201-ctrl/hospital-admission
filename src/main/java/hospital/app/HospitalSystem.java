package hospital.app;

import hospital.clinical.ClinicalOrderRepository;
import hospital.clinical.ClinicalOrderService;
import hospital.clinical.DrugSafetyChecker;
import hospital.facade.AdmissionAdminFacade;
import hospital.facade.ChainRepository;
import hospital.facade.HospitalAdmissionFacade;
import hospital.registration.InsuranceVerifier;
import hospital.registration.RegistrationDesk;
import hospital.registration.RegistrationRepository;
import hospital.resources.BedAllocator;
import hospital.resources.ResourceRepository;
import hospital.resources.ResourceService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.stream.Stream;


public class HospitalSystem {
    private static final Path DATA_DIR = Path.of("data");

    private final Clock clock = Clock.systemDefaultZone();
    private String crashAfter = null;

    private HospitalAdmissionFacade facade;
    private AdmissionAdminFacade admin;

    public HospitalSystem() {
        wire();
        seedIfEmpty();
    }

    public HospitalAdmissionFacade facade() {
        return facade;
    }

    public AdmissionAdminFacade admin() {
        return admin;
    }


    public void crashAfter(String step) {
        this.crashAfter = step;
    }


    public void reset() {
        crashAfter = null;
        try (Stream<Path> files = Files.exists(DATA_DIR) ? Files.list(DATA_DIR) : Stream.<Path>empty()) {
            files.filter(p -> p.toString().endsWith(".db")).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        wire();
        seedIfEmpty();
    }

    private void wire() {
        RegistrationDesk registration = new RegistrationDesk(
                new RegistrationRepository(DATA_DIR.resolve("registration.db")),
                new InsuranceVerifier(clock));
        ResourceService resources = new ResourceService(
                new ResourceRepository(DATA_DIR.resolve("resources.db")), new BedAllocator());
        ClinicalOrderService clinical = new ClinicalOrderService(
                new ClinicalOrderRepository(DATA_DIR.resolve("clinical.db")), new DrugSafetyChecker());
        ChainRepository chains = new ChainRepository(DATA_DIR.resolve("chains.db"));

        facade = new HospitalAdmissionFacade(registration, resources, clinical, chains, this::maybeCrash);
        admin = new AdmissionAdminFacade(registration, resources, clinical, chains);
    }


    private void maybeCrash(String step) {
        if (step.equals(crashAfter)) {
            System.out.println();
            System.out.println(">>> SIMULATED CRASH: process killed right after step " + step);
            System.out.flush();
            Runtime.getRuntime().halt(1);
        }
    }

    private void seedIfEmpty() {
        if (admin.hasPolicy("POL-OK")) {
            return;
        }
        admin.addPolicy("POL-OK", 10_000.0, LocalDate.now().plusYears(1));
        admin.addPolicy("POL-SMALL", 500.0, LocalDate.now().plusYears(1));
        admin.addPolicy("POL-EXPIRED", 10_000.0, LocalDate.now().minusYears(1));

        admin.addBed("G1", "GENERAL");
        admin.addBed("G2", "GENERAL");
        admin.addBed("G3", "GENERAL");
        admin.addBed("I1", "ICU");

        admin.setEquipmentStock("VENTILATOR", 1);
        admin.setEquipmentStock("MONITOR", 3);
        admin.setEquipmentStock("WHEELCHAIR", 3);

        for (String drug : new String[]{"PARACETAMOL", "AMOXICILLIN", "IBUPROFEN", "ASPIRIN"}) {
            admin.setPharmacyStock(drug, 10);
        }
    }
}