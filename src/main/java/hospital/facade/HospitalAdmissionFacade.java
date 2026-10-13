package hospital.facade;

import hospital.clinical.ClinicalOrderService;
import hospital.exceptions.AdmissionException;
import hospital.registration.RegistrationDesk;
import hospital.resources.ResourceService;

import java.util.List;
import java.util.UUID;

public class HospitalAdmissionFacade {
    private final RegistrationDesk registration;
    private final ResourceService resources;
    private final ClinicalOrderService clinical;
    private final ChainRepository chains;
    private final ChainCompensator compensator;
    private final CrashPoint crashPoint;

    public HospitalAdmissionFacade(RegistrationDesk registration, ResourceService resources,
                                   ClinicalOrderService clinical, ChainRepository chains,
                                   CrashPoint crashPoint) {
        this.registration = registration;
        this.resources = resources;
        this.clinical = clinical;
        this.chains = chains;
        this.crashPoint = crashPoint;
        this.compensator = new ChainCompensator(registration, resources, clinical, chains, crashPoint);
    }

    public HospitalAdmissionFacade(RegistrationDesk registration, ResourceService resources,
                                   ClinicalOrderService clinical, ChainRepository chains) {
        this(registration, resources, clinical, chains, CrashPoint.NONE);
    }

    public AdmissionResult admitPatient(AdmissionRequest req) {
        String chainId = UUID.randomUUID().toString();
        chains.create(chainId, req.patientName());
        try {

            registration.register(chainId, req.patientName(), req.age(),
                    req.policyNumber(), req.estimatedCost());
            chains.setState(chainId, ChainState.PENDING_B, null);
            crashPoint.after("A");


            resources.allocate(chainId, req.wardType(), req.equipmentType());
            chains.setState(chainId, ChainState.PENDING_C, null);
            crashPoint.after("B");


            clinical.place(chainId, req.labTest(), req.medication(), req.allergies());
            chains.setState(chainId, ChainState.COMPLETED, null);
            crashPoint.after("C");

            return AdmissionResult.completed(chainId);

        } catch (AdmissionException e) {
            return handleFailure(chainId, e);
        }
    }

    private AdmissionResult handleFailure(String chainId, AdmissionException e) {
        ChainState state = chains.find(chainId).orElseThrow().state();

        if (state == ChainState.STARTED) {

            chains.setState(chainId, ChainState.REJECTED, e.getMessage());
            return AdmissionResult.rejected(chainId, e.getMessage());
        }


        List<String> undone = compensator.compensate(chainId, e.getMessage());
        return AdmissionResult.compensated(chainId, e.getMessage(), undone);
    }
}