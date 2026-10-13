package hospital.clinical;

import java.nio.file.Path;
import java.util.List;

/** Запуск подсистемы C без фасада и без A/B. */
public class ClinicalDemo {
    public static void main(String[] args) {
        ClinicalOrderRepository repository =
                new ClinicalOrderRepository(Path.of("data", "clinical-demo.db"));
        repository.setStock("PARACETAMOL", 5);
        repository.setStock("AMOXICILLIN", 5);

        ClinicalOrderService service =
                new ClinicalOrderService(repository, new DrugSafetyChecker());

        ClinicalOrder o = service.place("demo-chain", "BLOOD", "PARACETAMOL", List.of());
        System.out.println("Placed order -> " + o.getStatus());
        System.out.println("Paracetamol left: " + repository.stockOf("PARACETAMOL"));

        try {
            service.place("demo-chain-2", "BLOOD", "AMOXICILLIN", List.of("penicillin"));
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        service.cancelByChain("demo-chain");
        System.out.println("After cancel: "
                + service.findByChain("demo-chain").orElseThrow().getStatus());
        System.out.println("Paracetamol left: " + repository.stockOf("PARACETAMOL"));
    }
}