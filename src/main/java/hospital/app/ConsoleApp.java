package hospital.app;

import hospital.facade.AdmissionAdminFacade;
import hospital.facade.AdmissionRequest;
import hospital.facade.AdmissionResult;
import hospital.facade.ChainRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;


public class ConsoleApp {
    private final Scanner in = new Scanner(System.in);
    private final HospitalSystem system = new HospitalSystem();

    public static void main(String[] args) {
        new ConsoleApp().run();
    }

    private void run() {
        System.out.println("=== Hospital Admission (Facade demo) ===");
        warnAboutUnfinishedChains();
        while (true) {
            printMenu();
            String choice = ask("Choose");
            if (choice.equals("0")) {
                System.out.println("Bye.");
                return;
            }
            try {
                handle(choice);
            } catch (RuntimeException e) {
                System.out.println("ERROR: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println(" 1. Admit patient (enter data manually)");
        System.out.println(" 2. Demo: successful admission (A -> B -> C)");
        System.out.println(" 3. Demo: step A fails (expired insurance) -> nothing to undo");
        System.out.println(" 4. Demo: step B fails -> only A is compensated");
        System.out.println(" 5. Demo: step C fails -> B and then A are compensated");
        System.out.println(" 6. Show all chains");
        System.out.println(" 7. Inspect chain (status in A, B and C)");
        System.out.println(" 8. Show inventory (beds, equipment, pharmacy)");
        System.out.println(" 9. Admin: compensate a chain manually");
        System.out.println("10. Admin: recover unfinished chains (after a crash/restart)");
        System.out.println("11. Demo: process CRASH right after step B");
        System.out.println("12. Admin: change stock / add bed");
        System.out.println("13. Reset the store (delete all data)");
        System.out.println(" 0. Exit");
    }

    private void handle(String choice) {
        switch (choice) {
            case "1" -> admitManually();
            case "2" -> demoSuccess();
            case "3" -> demoFailAtA();
            case "4" -> demoFailAtB();
            case "5" -> demoFailAtC();
            case "6" -> showChains();
            case "7" -> inspectChain();
            case "8" -> showInventory();
            case "9" -> compensateManually();
            case "10" -> recover();
            case "11" -> demoCrash();
            case "12" -> adjustData();
            case "13" -> reset();
            default -> System.out.println("Unknown option.");
        }
    }



    private void admitManually() {
        String name = askOr("Patient name", "Aigerim");
        int age = askInt("Age", 30);
        String policy = askOr("Policy (POL-OK / POL-SMALL / POL-EXPIRED)", "POL-OK").toUpperCase();
        double cost = askDouble("Estimated cost", 2000.0);
        String ward = askOr("Ward (GENERAL / ICU)", "GENERAL").toUpperCase();
        String equipment = askOr("Equipment (NONE / VENTILATOR / MONITOR / WHEELCHAIR)", "NONE").toUpperCase();
        String lab = askOr("Lab test (NONE / BLOOD / XRAY / MRI)", "BLOOD").toUpperCase();
        String drug = askOr("Medication (NONE / PARACETAMOL / AMOXICILLIN / IBUPROFEN / ASPIRIN)",
                "PARACETAMOL").toUpperCase();
        String rawAllergies = askOr("Allergies, comma separated (or none)", "none");
        List<String> allergies = rawAllergies.equalsIgnoreCase("none")
                ? List.of()
                : Arrays.stream(rawAllergies.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        admit(new AdmissionRequest(name, age, policy, cost, ward, equipment, lab, drug, allergies));
    }

    private void demoSuccess() {
        title("Successful admission: A, B and C all succeed");
        ensureResources("GENERAL", "PARACETAMOL");
        admit(demoRequest("POL-OK", "GENERAL", "NONE", "PARACETAMOL", List.of()));
    }

    private void demoFailAtA() {
        title("Step A fails: insurance policy is expired");
        admit(demoRequest("POL-EXPIRED", "GENERAL", "NONE", "PARACETAMOL", List.of()));
    }

    private void demoFailAtB() {
        title("Step B fails: no ventilator available -> only A must be compensated");
        int ventilators = system.admin().inventory().get("VENTILATOR");
        System.out.println("(setting ventilator stock to 0 to force the failure)");
        system.admin().setEquipmentStock("VENTILATOR", 0);
        try {
            admit(demoRequest("POL-OK", "ICU", "VENTILATOR", "PARACETAMOL", List.of()));
        } finally {
            system.admin().setEquipmentStock("VENTILATOR", ventilators);
            System.out.println("(ventilator stock restored to " + ventilators + ")");
        }
    }

    private void demoFailAtC() {
        title("Step C fails: allergy to penicillin -> B and then A must be compensated");
        ensureResources("GENERAL", "AMOXICILLIN");
        admit(demoRequest("POL-OK", "GENERAL", "NONE", "AMOXICILLIN", List.of("PENICILLIN")));
    }

    private void demoCrash() {
        title("Process crash right after step B");
        ensureResources("GENERAL", "PARACETAMOL");
        System.out.println("The process will be KILLED after step B (A and B already committed to disk).");
        System.out.println("Then start the program again:");
        System.out.println("  option 6/7 -> the chain is still PENDING_C (read from disk)");
        System.out.println("  option 10  -> the facade finds it on disk and compensates B, then A");
        ask("Press Enter to crash the process");
        system.crashAfter("B");
        try {
            admit(demoRequest("POL-OK", "GENERAL", "NONE", "PARACETAMOL", List.of()));
        } finally {
            system.crashAfter(null);
        }
    }

    private AdmissionRequest demoRequest(String policy, String ward, String equipment,
                                         String drug, List<String> allergies) {
        return new AdmissionRequest("Demo Patient", 30, policy, 2000.0, ward, equipment,
                "BLOOD", drug, allergies);
    }

    private void admit(AdmissionRequest request) {
        AdmissionResult result = system.facade().admitPatient(request);
        System.out.println();
        System.out.println("Result : " + result.outcome());
        System.out.println("Message: " + result.message());
        if (!result.undone().isEmpty()) {
            System.out.println("Compensation, in this order:");
            int i = 1;
            for (String step : result.undone()) {
                System.out.println("  " + i++ + ". " + step);
            }
        }
        System.out.println("Chain  : " + result.chainId());
        printReport(result.chainId());
    }



    private List<ChainRecord> showChains() {
        List<ChainRecord> chains = system.admin().listChains();
        if (chains.isEmpty()) {
            System.out.println("No chains yet.");
        }
        for (int i = 0; i < chains.size(); i++) {
            ChainRecord c = chains.get(i);
            System.out.printf("%2d. %s  %-12s %s%n", i + 1, c.chainId().substring(0, 8),
                    c.state(), c.patientName());
        }
        return chains;
    }

    private String pickChain() {
        List<ChainRecord> chains = showChains();
        if (chains.isEmpty()) {
            return null;
        }
        try {
            int index = Integer.parseInt(ask("Chain number")) - 1;
            return chains.get(index).chainId();
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            System.out.println("Invalid number.");
            return null;
        }
    }

    private void inspectChain() {
        String chainId = pickChain();
        if (chainId != null) {
            printReport(chainId);
        }
    }

    private void printReport(String chainId) {
        AdmissionAdminFacade.ChainReport r = system.admin().inspect(chainId);
        System.out.println("  Chain state      : " + r.chain().state());
        System.out.println("  A registration   : " + r.registration());
        System.out.println("  B booking        : " + r.booking());
        System.out.println("  C clinical order : " + r.order());
        if (r.chain().failureReason() != null) {
            System.out.println("  Reason           : " + r.chain().failureReason());
        }
    }

    private void showInventory() {
        for (Map.Entry<String, Integer> e : system.admin().inventory().entrySet()) {
            System.out.printf("  %-20s %d%n", e.getKey(), e.getValue());
        }
    }

    private void compensateManually() {
        String chainId = pickChain();
        if (chainId == null) {
            return;
        }
        List<String> undone = system.admin().compensateManually(chainId);
        if (undone.isEmpty()) {
            System.out.println("Nothing to undo for this chain.");
        } else {
            System.out.println("Undone, in this order:");
            undone.forEach(step -> System.out.println("  - " + step));
        }
        printReport(chainId);
    }

    private void recover() {
        List<String> recovered = system.admin().recoverPendingChains();
        if (recovered.isEmpty()) {
            System.out.println("No unfinished chains on disk.");
            return;
        }
        System.out.println("Recovered " + recovered.size() + " chain(s):");
        for (String chainId : recovered) {
            System.out.println("Chain " + chainId.substring(0, 8));
            printReport(chainId);
        }
    }

    private void adjustData() {
        System.out.println("1) equipment stock   2) pharmacy stock   3) add bed");
        switch (ask("Choose")) {
            case "1" -> system.admin().setEquipmentStock(
                    ask("Equipment (VENTILATOR / MONITOR / WHEELCHAIR)").toUpperCase(),
                    askInt("New stock", 0));
            case "2" -> system.admin().setPharmacyStock(
                    ask("Medication (PARACETAMOL / AMOXICILLIN / IBUPROFEN / ASPIRIN)").toUpperCase(),
                    askInt("New stock", 0));
            case "3" -> system.admin().addBed(
                    ask("New bed id (for example G9)"),
                    ask("Ward (GENERAL / ICU)").toUpperCase());
            default -> System.out.println("Unknown option.");
        }
        System.out.println("Done.");
    }

    private void reset() {
        if (ask("This deletes ALL data. Type yes to confirm").equalsIgnoreCase("yes")) {
            system.reset();
            System.out.println("Store reset, demo data restored.");
        }
    }



    private void warnAboutUnfinishedChains() {
        long unfinished = system.admin().listChains().stream()
                .filter(c -> !c.state().isFinished()).count();
        if (unfinished > 0) {
            System.out.println();
            System.out.println("WARNING: " + unfinished + " unfinished chain(s) found on disk "
                    + "(the program was probably killed). Use option 10 to recover.");
        }
    }


    private void ensureResources(String ward, String drug) {
        Map<String, Integer> inventory = system.admin().inventory();
        if (inventory.get("Free " + ward + " beds") == 0) {
            system.admin().addBed("X" + System.currentTimeMillis() % 100000, ward);
            System.out.println("(added a spare " + ward + " bed so the demo can be repeated)");
        }
        if (inventory.get(drug) == 0) {
            system.admin().setPharmacyStock(drug, 10);
            System.out.println("(refilled " + drug + " in the pharmacy)");
        }
    }

    private void title(String text) {
        System.out.println();
        System.out.println("--- " + text + " ---");
    }

    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        if (!in.hasNextLine()) {
            System.out.println();
            System.out.println("Input closed, bye.");
            System.exit(0);
        }
        return in.nextLine().trim();
    }

    private String askOr(String prompt, String defaultValue) {
        String value = ask(prompt + " [" + defaultValue + "]");
        return value.isEmpty() ? defaultValue : value;
    }

    private int askInt(String prompt, int defaultValue) {
        while (true) {
            try {
                return Integer.parseInt(askOr(prompt, String.valueOf(defaultValue)));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private double askDouble(String prompt, double defaultValue) {
        while (true) {
            try {
                return Double.parseDouble(askOr(prompt, String.valueOf(defaultValue)));
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }
}