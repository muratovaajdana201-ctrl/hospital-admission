package hospital.registration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;


public class RegistrationDemo {
    public static void main(String[] args) {
        RegistrationRepository repository =
                new RegistrationRepository(Path.of("data", "registration-demo.db"));
        repository.savePolicy(new RegistrationRepository.PolicyRecord(
                "POL-DEMO", 10_000.0, LocalDate.now().plusYears(1)));

        RegistrationDesk desk =
                new RegistrationDesk(repository, new InsuranceVerifier(Clock.systemDefaultZone()));

        Registration r = desk.register("demo-chain", "Aigerim", 30, "POL-DEMO", 2_000.0);
        System.out.println("Registered: " + r.getPatientName() + " -> " + r.getStatus());

        desk.cancelByChain("demo-chain");
        System.out.println("After cancel: "
                + desk.findByChain("demo-chain").orElseThrow().getStatus());
    }
}