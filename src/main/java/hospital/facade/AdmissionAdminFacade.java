package hospital.facade;

import hospital.clinical.ClinicalOrderService;
import hospital.exceptions.ChainNotFoundException;
import hospital.registration.RegistrationDesk;
import hospital.resources.ResourceService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class AdmissionAdminFacade {


    public record ChainReport(ChainRecord chain, String registration, String booking, String order) {
    }

    private final RegistrationDesk registration;
    private final ResourceService resources;
    private final ClinicalOrderService clinical;
    private final ChainRepository chains;
    private final ChainCompensator compensator;

    public AdmissionAdminFacade(RegistrationDesk registration, ResourceService resources,
                                ClinicalOrderService clinical, ChainRepository chains) {
        this.registration = registration;
        this.resources = resources;
        this.clinical = clinical;
        this.chains = chains;
        this.compensator = new ChainCompensator(registration, resources, clinical, chains,
                CrashPoint.NONE);
    }


    public List<ChainRecord> listChains() {
        return chains.findAll();
    }

    public ChainReport inspect(String chainId) {
        ChainRecord chain = chains.find(chainId)
                .orElseThrow(() -> new ChainNotFoundException(chainId));
        String a = registration.findByChain(chainId).map(r -> r.getStatus().name()).orElse("-");
        String b = resources.findByChain(chainId).map(x -> x.getStatus().name()).orElse("-");
        String c = clinical.findByChain(chainId).map(x -> x.getStatus().name()).orElse("-");
        return new ChainReport(chain, a, b, c);
    }

    public Map<String, Integer> inventory() {
        Map<String, Integer> view = new LinkedHashMap<>();
        view.put("Free GENERAL beds", resources.freeBeds("GENERAL"));
        view.put("Free ICU beds", resources.freeBeds("ICU"));
        for (String equipment : List.of("VENTILATOR", "MONITOR", "WHEELCHAIR")) {
            view.put(equipment, resources.equipmentLeft(equipment));
        }
        for (String medication : List.of("PARACETAMOL", "AMOXICILLIN", "IBUPROFEN", "ASPIRIN")) {
            view.put(medication, clinical.stockOf(medication));
        }
        return view;
    }



    public List<String> compensateManually(String chainId) {
        ChainRecord chain = chains.find(chainId)
                .orElseThrow(() -> new ChainNotFoundException(chainId));
        if (chain.state() == ChainState.COMPENSATED || chain.state() == ChainState.REJECTED) {
            return List.of();
        }
        return compensator.compensate(chainId, "Manual compensation by administrator");
    }


    public List<String> recoverPendingChains() {
        List<String> recovered = new ArrayList<>();
        for (ChainRecord record : chains.findUnfinished()) {
            compensator.compensate(record.chainId(),
                    "Recovered after restart (was " + record.state() + ")");
            recovered.add(record.chainId());
        }
        return recovered;
    }



    public void addPolicy(String policyNumber, double coverageLimit, LocalDate validUntil) {
        registration.addPolicy(policyNumber, coverageLimit, validUntil);
    }

    public boolean hasPolicy(String policyNumber) {
        return registration.hasPolicy(policyNumber);
    }

    public void addBed(String bedId, String wardType) {
        resources.addBed(bedId, wardType);
    }

    public void setEquipmentStock(String equipmentType, int available) {
        resources.setEquipmentStock(equipmentType, available);
    }

    public void setPharmacyStock(String medication, int available) {
        clinical.setStock(medication, available);
    }
}