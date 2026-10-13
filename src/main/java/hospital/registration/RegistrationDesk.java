package hospital.registration;

import hospital.exceptions.InsuranceRejectedException;
import hospital.exceptions.InvalidPatientDataException;

import java.util.List;
import java.util.Optional;


public class RegistrationDesk {
    private final RegistrationRepository repository;
    private final InsuranceVerifier verifier;

    public RegistrationDesk(RegistrationRepository repository, InsuranceVerifier verifier) {
        this.repository = repository;
        this.verifier = verifier;
    }


    public Registration register(String chainId, String patientName, int age,
                                 String policyNumber, double estimatedCost) {
        validateInput(patientName, age, policyNumber, estimatedCost);

        RegistrationRepository.PolicyRecord policy = repository.findPolicy(policyNumber)
                .orElseThrow(() -> new InsuranceRejectedException(
                        "Unknown insurance policy " + policyNumber));

        verifier.verify(policy.coverageLimit(), policy.validUntil(), estimatedCost);

        Registration registration =
                new Registration(chainId, patientName, age, policyNumber, estimatedCost);
        registration.moveTo(RegistrationStatus.REGISTERED);
        repository.save(registration);
        return registration;
    }


    public void cancelByChain(String chainId) {
        repository.findByChainId(chainId).ifPresent(registration -> {
            if (registration.getStatus() != RegistrationStatus.CANCELLED) {
                registration.moveTo(RegistrationStatus.CANCELLED);
                repository.save(registration);
            }
        });
    }

    public Optional<Registration> findByChain(String chainId) {
        return repository.findByChainId(chainId);
    }

    public List<Registration> findAll() {
        return repository.findAll();
    }

    private void validateInput(String name, int age, String policyNumber, double cost) {
        if (name == null || name.isBlank()) {
            throw new InvalidPatientDataException("Patient name must not be empty");
        }
        if (age < 0 || age > 120) {
            throw new InvalidPatientDataException("Age must be between 0 and 120");
        }
        if (policyNumber == null || policyNumber.isBlank()) {
            throw new InvalidPatientDataException("Policy number must not be empty");
        }
        if (cost <= 0) {
            throw new InvalidPatientDataException("Estimated cost must be positive");
        }
    }
}