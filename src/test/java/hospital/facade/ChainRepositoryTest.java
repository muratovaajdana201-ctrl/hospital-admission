package hospital.facade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChainRepositoryTest {

    @TempDir
    Path tempDir;

    private ChainRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ChainRepository(tempDir.resolve("chains.db"));
    }

    @Test
    void createdChainStartsInStartedState() {
        repository.create("c1", "Aigerim");
        ChainRecord record = repository.find("c1").orElseThrow();
        assertEquals(ChainState.STARTED, record.state());
        assertEquals("Aigerim", record.patientName());
        assertNull(record.failureReason());
    }

    @Test
    void setStateStoresStateAndReason() {
        repository.create("c1", "Aigerim");
        repository.setState("c1", ChainState.COMPENSATED, "bed missing");
        ChainRecord record = repository.find("c1").orElseThrow();
        assertEquals(ChainState.COMPENSATED, record.state());
        assertEquals("bed missing", record.failureReason());
    }

    @Test
    void unknownChainIsEmpty() {
        assertTrue(repository.find("nope").isEmpty());
    }

    @Test
    void nullPatientNameIsStoredAsEmptyText() {
        repository.create("c1", null);
        assertEquals("", repository.find("c1").orElseThrow().patientName());
    }

    @Test
    void findUnfinishedSkipsFinishedChains() {
        repository.create("c1", "A");
        repository.create("c2", "B");
        repository.create("c3", "C");
        repository.setState("c1", ChainState.COMPLETED, null);
        repository.setState("c2", ChainState.PENDING_C, null);
        repository.setState("c3", ChainState.REJECTED, "x");

        List<ChainRecord> unfinished = repository.findUnfinished();

        assertEquals(1, unfinished.size());
        assertEquals("c2", unfinished.get(0).chainId());
    }

    @Test
    void chainSurvivesRestart() {
        repository.create("c1", "Aigerim");
        repository.setState("c1", ChainState.PENDING_B, null);

        ChainRepository afterRestart = new ChainRepository(tempDir.resolve("chains.db"));

        assertEquals(ChainState.PENDING_B, afterRestart.find("c1").orElseThrow().state());
    }

    @Test
    void findAllKeepsInsertionOrder() {
        repository.create("c1", "A");
        repository.create("c2", "B");
        assertEquals("c1", repository.findAll().get(0).chainId());
        assertEquals("c2", repository.findAll().get(1).chainId());
    }
}