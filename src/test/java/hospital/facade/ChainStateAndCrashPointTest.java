package hospital.facade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChainStateAndCrashPointTest {

    @Test
    void onlyFinalStatesAreFinished() {
        assertTrue(ChainState.COMPLETED.isFinished());
        assertTrue(ChainState.COMPENSATED.isFinished());
        assertTrue(ChainState.REJECTED.isFinished());
        assertFalse(ChainState.STARTED.isFinished());
        assertFalse(ChainState.PENDING_B.isFinished());
        assertFalse(ChainState.PENDING_C.isFinished());
        assertFalse(ChainState.COMPENSATING.isFinished());
    }

    @Test
    void crashPointThrowsOnlyAfterTheChosenStep() {
        CrashPoint crash = CrashPoint.throwAfter("B");
        assertDoesNotThrow(() -> crash.after("A"));
        assertThrows(SimulatedCrashException.class, () -> crash.after("B"));
    }

    @Test
    void defaultCrashPointNeverThrows() {
        assertDoesNotThrow(() -> CrashPoint.NONE.after("B"));
    }
}