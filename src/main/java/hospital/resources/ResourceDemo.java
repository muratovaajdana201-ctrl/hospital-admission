package hospital.resources;

import java.nio.file.Path;

/** Запуск подсистемы B без фасада и без A/C. */
public class ResourceDemo {
    public static void main(String[] args) {
        ResourceRepository repository =
                new ResourceRepository(Path.of("data", "resources-demo.db"));
        repository.saveBed("G1", BedAllocator.GENERAL);
        repository.saveBed("I1", BedAllocator.ICU);
        repository.setEquipmentStock("VENTILATOR", 1);

        ResourceService service = new ResourceService(repository, new BedAllocator());

        ResourceBooking b = service.allocate("demo-chain", BedAllocator.ICU, "VENTILATOR");
        System.out.println("Allocated bed " + b.getBedId() + " -> " + b.getStatus());
        System.out.println("Ventilators left: " + repository.equipmentAvailable("VENTILATOR"));

        service.releaseByChain("demo-chain");
        System.out.println("After release: "
                + service.findByChain("demo-chain").orElseThrow().getStatus());
        System.out.println("Ventilators left: " + repository.equipmentAvailable("VENTILATOR"));
    }
}