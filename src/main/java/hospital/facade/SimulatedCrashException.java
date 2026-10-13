package hospital.facade;

public class SimulatedCrashException extends RuntimeException {
    public SimulatedCrashException(String step) {
        super("Simulated crash after step " + step);
    }
}