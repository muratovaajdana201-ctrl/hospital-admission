package hospital.facade;

@FunctionalInterface
public interface CrashPoint {
    CrashPoint NONE = step -> { };

    void after(String step);


    static CrashPoint throwAfter(String step) {
        return current -> {
            if (current.equals(step)) {
                throw new SimulatedCrashException(step);
            }
        };
    }
}