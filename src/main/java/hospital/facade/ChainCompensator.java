package hospital.facade;

import hospital.clinical.ClinicalOrderService;
import hospital.clinical.OrderStatus;
import hospital.registration.RegistrationDesk;
import hospital.registration.RegistrationStatus;
import hospital.resources.BookingStatus;
import hospital.resources.ResourceService;

import java.util.ArrayList;
import java.util.List;


public class ChainCompensator {
    private final RegistrationDesk registration;
    private final ResourceService resources;
    private final ClinicalOrderService clinical;
    private final ChainRepository chains;
    private final CrashPoint crashPoint;

    public ChainCompensator(RegistrationDesk registration, ResourceService resources,
                            ClinicalOrderService clinical, ChainRepository chains,
                            CrashPoint crashPoint) {
        this.registration = registration;
        this.resources = resources;
        this.clinical = clinical;
        this.chains = chains;
        this.crashPoint = crashPoint;
    }


    public List<String> compensate(String chainId, String reason) {
        chains.setState(chainId, ChainState.COMPENSATING, reason);
        List<String> undone = new ArrayList<>();


        if (clinical.findByChain(chainId)
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED).isPresent()) {
            clinical.cancelByChain(chainId);
            undone.add("C: clinical order cancelled, medication returned");
        }
        crashPoint.after("undo-C");


        if (resources.findByChain(chainId)
                .filter(b -> b.getStatus() == BookingStatus.ALLOCATED).isPresent()) {
            resources.releaseByChain(chainId);
            undone.add("B: bed and equipment released");
        }
        crashPoint.after("undo-B");


        if (registration.findByChain(chainId)
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED).isPresent()) {
            registration.cancelByChain(chainId);
            undone.add("A: registration cancelled");
        }

        chains.setState(chainId, ChainState.COMPENSATED, reason);
        return undone;
    }
}