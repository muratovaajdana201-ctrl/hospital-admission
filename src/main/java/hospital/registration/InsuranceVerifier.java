package hospital.registration;

import hospital.exceptions.InsuranceRejectedException;

import java.time.Clock;
import java.time.LocalDate;


public class InsuranceVerifier {
    private final Clock clock;

    public InsuranceVerifier(Clock clock) {
        this.clock = clock;
    }

    public void verify(double coverageLimit, LocalDate validUntil, double estimatedCost) {
        LocalDate today = LocalDate.now(clock);
        if (validUntil.isBefore(today)) {
            throw new InsuranceRejectedException("Policy expired on " + validUntil);
        }
        if (estimatedCost > coverageLimit) {
            throw new InsuranceRejectedException(
                    "Estimated cost " + estimatedCost + " exceeds coverage limit " + coverageLimit);
        }
    }
}