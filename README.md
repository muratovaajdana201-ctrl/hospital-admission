# Hospital Admission: a Facade over a three-step chain

This is my solution to Assignment 4 (Facade pattern). My domain is hospital admission.

From the client's point of view, admitting a patient is one operation. Behind it there are three independent subsystems that run in a chain. Subsystem A registers the patient and checks the insurance, so it holds the place. Subsystem B allocates a bed and equipment, so it does the actual work. Subsystem C orders the lab test and dispenses the medication, so it confirms everything. The facade runs A, then B, then C. If B or C fails after earlier steps have already been saved to disk, the facade undoes exactly the part that was really saved.

## How to build and run

You need JDK 17 or newer and Maven. Everything works from the project folder:

```
mvn compile exec:java
mvn clean test
mvn clean verify
```

The first command starts the console demo. The second runs the unit tests. The third runs the tests and then checks that line coverage is at least 80 percent. The coverage report is generated in `target/site/jacoco/index.html`.

### Resetting the stored data

The program keeps its data in the data folder. To start from a clean state, either choose option 13 in the demo menu and type `yes`, or close the program and delete the `data` folder. On the next start the databases and the demo data are created again automatically.

## Structure

The UML class diagram is in docs/uml.png. The editable source is docs/uml.drawio.
![UML class diagram](docs/uml.png)
### The three subsystems

Each subsystem has five classes and the same shape. It has an entity that holds the state, a status enum with three values, one class with pure business logic that never touches the database, a persistence gateway that talks to SQLite, and a service that ties them together.

For A these are Registration, RegistrationStatus, InsuranceVerifier, RegistrationRepository and RegistrationDesk. For B they are ResourceBooking, BookingStatus, BedAllocator, ResourceRepository and ResourceService. For C they are ClinicalOrder, OrderStatus, DrugSafetyChecker, ClinicalOrderRepository and ClinicalOrderService.

A few rules hold for all three:

- No subsystem imports or calls another one. The only things they share are the chainId string, which the facade passes in, and the package with the exception classes.
- Each entity checks its own status transitions. For example, a cancelled registration can never become registered again, even if the facade asked for it.
- B and C change stock and records inside one SQL transaction. If the equipment runs out, the bed that was just marked as taken is rolled back, so nothing is left half done.
- Each subsystem can be used alone, without the facade. RegistrationDemo, ResourceDemo and ClinicalDemo each have a main method for that.

### The facades

There are two facades in the package hospital.facade.

HospitalAdmissionFacade is for the normal client workflow. Its method admitPatient does real work: it runs the steps in order, saves the progress after every step, and decides what to do when a step fails.

AdmissionAdminFacade is the narrow one for administration. It lists and inspects chains, compensates a chain by hand, recovers unfinished chains after a restart, shows the inventory, and lets me change stock so I can trigger failures on purpose.

Both facades use ChainCompensator for the undo logic. I put it in a separate class so the same code is used in both places and neither facade grows too big.

### Durable state

Every subsystem has its own SQLite file in data: registration.db, resources.db and clinical.db. The facade layer keeps the progress of each chain in chains.db. A chain moves through these states:

```
STARTED -> PENDING_B -> PENDING_C -> COMPLETED

STARTED -> REJECTED                  (A failed, nothing to undo)
PENDING_B or PENDING_C -> COMPENSATING -> COMPENSATED
```

The state is written to disk after each step, so it survives a restart. When the program starts again, the facade can read `chains.db`, see which chains were left unfinished, and deal with them.

### How the compensation decides what to undo

The compensator does not rely on flags in memory, because a restart would erase them. For a given chain it asks each subsystem what is actually stored. If there is a confirmed clinical order, it cancels it and puts the medication back. If there is an allocated booking, it releases the bed and the equipment. If the registration is not cancelled yet, it cancels it. The order is always C, then B, then A.

All the undo operations are idempotent. Running them a second time changes nothing, so it is safe to repeat a compensation, for example after a crash in the middle of one.

After a restart I chose to compensate unfinished chains instead of resuming them. The original request data, such as the allergies and the ward type, is not stored, so the chain cannot be continued. Undoing is always possible because it only needs the `chainId`.

## Failure scenarios

All of these can be started from the demo menu.

The normal case is menu option 2. All three steps work and the chain ends as COMPLETED.

The extra failure case is option 3. The insurance policy is expired, so step A fails before anything is saved. The chain is REJECTED and there is nothing to undo.

The first compensation depth is option 4. A patient needs a ventilator, but none is left. Step B fails after A was saved, and only A is compensated.

The second compensation depth is option 5. A patient is allergic to penicillin but was prescribed amoxicillin. Step C fails after A and B were saved, so B is released first and then A is cancelled.

The crash case is option 11. The process is killed right after step B. After starting the program again, the chain is still visible as PENDING_C, and option 10 finds it on disk and compensates it.

I defined my own exceptions. AdmissionException is the base class for business errors. Below it are `InvalidPatientDataException`, `InsuranceRejectedException`, `NoBedAvailableException`, `EquipmentUnavailableException`, `PharmacyRejectedException` and `ChainNotFoundException`. There is also `PersistenceException` for technical database problems, and it deliberately does not extend `AdmissionException`. The facade catches only `AdmissionException`. A crash or a database failure is not swallowed, so the chain stays on disk and gets recovered later.

## Principle of Least Knowledge, before and after

Without a facade the client has to create and know three services, know the order of the steps, keep the chain bookkeeping, and write the undo logic itself:

```text
RegistrationDesk desk = new RegistrationDesk(regRepository, new InsuranceVerifier(clock));
ResourceService resources = new ResourceService(resRepository, new BedAllocator());
ClinicalOrderService clinical = new ClinicalOrderService(clinRepository, new DrugSafetyChecker());

String chainId = UUID.randomUUID().toString();
chains.create(chainId, name);
try {
    desk.register(chainId, name, age, policy, cost);
    chains.setState(chainId, ChainState.PENDING_B, null);
    try {
        resources.allocate(chainId, ward, equipment);
        chains.setState(chainId, ChainState.PENDING_C, null);
        try {
            clinical.place(chainId, labTest, drug, allergies);
            chains.setState(chainId, ChainState.COMPLETED, null);
        } catch (AdmissionException e) {
            resources.releaseByChain(chainId);
            desk.cancelByChain(chainId);
            chains.setState(chainId, ChainState.COMPENSATED, e.getMessage());
        }
    } catch (AdmissionException e) {
        desk.cancelByChain(chainId);
        chains.setState(chainId, ChainState.COMPENSATED, e.getMessage());
    }
} catch (AdmissionException e) {
    chains.setState(chainId, ChainState.REJECTED, e.getMessage());
}

RegistrationStatus status = desk.findByChain(chainId).get().getStatus();
```

That client talks to many objects and reaches inside the objects it gets back. With the facade it talks to one:

```text
AdmissionResult result = facade.admitPatient(request);
System.out.println(result.outcome() + " " + result.undone());
```

The client only knows the facade, a plain AdmissionRequest and an AdmissionResult. The class ConsoleApp does not import anything from the three subsystem packages.

## Why a Facade and not an Adapter

An Adapter is used when one interface does not match the one a client expects, and the goal is to make them compatible. That is not our problem. Each of my three subsystems already has a clean interface, and nobody needs to convert it into something else.

Our problem is about coordination. Admitting a patient is one business operation made of three unrelated subsystems. They must be called in the right order, the progress has to be saved after every step, and when a later step fails, the correct part of the chain has to be undone. If every client had to know all of that, the knowledge would be repeated everywhere and would break easily. A Facade is meant for exactly this: it gives a simple entry point to a group of subsystems and takes over the orchestration, so the client is decoupled from them. The facade also adds logic of its own, such as sequencing, saved progress and compensation, which an Adapter would never contain.

So the choice is about intent, not about how many classes are wrapped. A facade can wrap one class and an adapter can wrap three. Here the intent is to simplify and coordinate, not to convert an interface. The facade also does not lock the subsystems away. Each of them can still be used directly, as the `Demo` classes show.

## Tests

There are 105 tests, run with mvn clean test.

For F.I.R.S.T., every test gets its own temporary folder from JUnit, so each one starts from a clean saved state and does not depend on any other test or on the order. The tests are fast, and every one of them checks its result with assertions instead of printing something to read. The tests were committed together with the code they cover, which can be seen in the git history.

For the domain-specific testing language I wrote the class AdmissionScenario. It lets a test read like a sentence about the hospital:

```text
hospital.needsWard("ICU").needsEquipment("VENTILATOR")
        .prescribed("AMOXICILLIN").allergicTo("PENICILLIN")
        .whenAdmitted()
        .thenOutcomeIs(Outcome.COMPENSATED)
        .thenUndoneStepsAre("B", "A")
        .thenBookingIs(BookingStatus.RELEASED)
        .thenRegistrationIs(RegistrationStatus.CANCELLED);
```

Both compensation depths have their own tests. noIcuBed_afterA_compensatesOnlyA covers B failing after A. allergyConflict_afterAandB_compensatesBThenA covers C failing after A and B. Each one checks the final state that is stored on disk.

Two tests cover reloading. crashAfterB_chainSurvivesRestartAndIsRecoveredFromDisk and crashInTheMiddleOfCompensation_isFinishedAfterRestartWithoutDoubleRelease simulate a restart by creating new repositories on the same database files and then checking that the facade continues correctly from the saved status.

### Coverage

I measured coverage with JaCoCo. It is the standard coverage tool for Maven, and it works together with `mvn test` without extra setup. The build fails if line coverage drops below 80 percent. The current result is XX percent of lines.

The console interface in hospital.app and the standalone Demo classes are excluded from the measurement. They only read keyboard input and print text, and the logic they use is already tested through the facades.
![JaCoCo coverage report](docs/coverage.png)
## Conclusion

In this assignment I built a small hospital admission system around one idea: three subsystems that know nothing about each other, and a facade that makes them look like a single operation. The system really runs. It stores its data in SQLite files, it survives a restart, and I can start any of the failure scenarios from the console menu and watch what happens.

The most interesting part for me was the compensation. At first it sounds simple: if a step fails, undo the previous one. In practice the facade has to undo a different part of the chain depending on how far it got. If B fails, only A is undone. If C fails, B is undone first and then A. What made this manageable was a decision to never trust memory. The compensator asks the databases what is really stored for a given chain and undoes only that. Because of this, the same code handles a normal failure, a manual rollback by an administrator, and a crash in the middle of the process. I also made every undo operation idempotent, so repeating it is harmless. This turned out to matter a lot: the test where the program crashes in the middle of a compensation finishes correctly after the restart and returns the equipment exactly once.

Another thing I learned is how much the shape of the subsystems helps the facade. Because each subsystem keeps its own state, checks its own status transitions and uses a transaction for its own changes, the facade does not have to know how a bed is occupied or how a medication is counted. It only passes a chain id and asks for a result. The subsystems stay simple, and the facade stays focused on order and recovery. I moved the undo logic into its own class so that neither facade grew into one big object that knows everything, which is the main danger of this pattern mentioned in the lecture.

Writing the tests in the style of a small domain language also changed how I worked. A test such as "patient needs an ICU bed, no bed is free, then only the registration is cancelled" is much easier to read and check than a list of repository calls. It was also much easier to see which scenarios were still missing, and that is how the tests for the crash during compensation and for repeated recovery appeared.

There are limits to what I built, and I want to name them honestly. After a restart an unfinished chain is compensated and not resumed, because the original request data is not stored. This is safe, but it means the patient has to be admitted again. The three subsystems use separate database files, so there is no real distributed transaction. The design relies on every step being undoable by the chain id, and a failure in the middle of an undo is repaired by running the recovery again, not by a single atomic operation. The system also assumes one application instance, and two programs writing to the same files at the same time are not handled. Finally, the demo is a console menu, which is enough for this task, but a real system would need a proper interface and some authentication for the administrator functions.

If I continued the project, the first things I would add are storing the full request so that a chain can be resumed and not only undone, a retry with a limit for the compensation itself, and a small REST layer on top of the two facades. The structure would not need to change for any of this, because the client already talks only to the facades. For me this is the main result of the assignment: the Principle of Least Knowledge is not just a rule from a slide, it is what allows the inside of the system to change without touching the code that uses it.