package uk.ac.uwe.hprtas.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.api.worker.JobWorker;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import uk.ac.uwe.hprtas.workers.services.Services;

/**
 * End-to-end smoke test for the external workers.
 *
 * Run with {@code mvn test -Pengine -Dtest=SmokeTest} while the engine is running. It deploys the
 * fixture process, starts the real workers, runs two process instances and checks the variables that
 * came back:
 *
 * <ol>
 *   <li>the normal path, which must leave a completed payment, a confirmed treatment booking and a
 *       dispatched letter</li>
 *   <li>a booking without clinical authorisation, which must take the modelled BPMN error path
 *       rather than stall the instance on an incident</li>
 * </ol>
 *
 * Unlike {@code mvn test} this proves the part the unit tests cannot: that the worker registers
 * against the gateway, receives a job of its type, and returns its result so the process continues.
 * The output is the evidence for the definition-of-done item "External worker".
 */
@Tag("engine")
class SmokeTest {

  private static final String PROCESS_ID = "worker-smoke-test";
  private static final String FIXTURE = "worker-smoke-test.bpmn";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private static Config config;
  private static Services services;
  private static CamundaClient client;
  private static final List<JobWorker> WORKERS = new ArrayList<>();

  @BeforeAll
  static void startWorkers() {
    // Returned from rather than aborted: an aborted class reports no tests at all, which reads the
    // same as a class that has none. Each test skips itself instead, so the skip is counted and
    // says which address was tried.
    if (!EngineTestSupport.engineIsRunning()) {
      return;
    }

    config = Config.load();
    services = Services.create(config);

    // The client logs its own connection lifecycle, which buries the output that is kept as
    // evidence. Error is the level it is held at everywhere else.
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

    client = CamundaClients.create(config);

    final Process deployed =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath(FIXTURE)
            .send()
            .join()
            .getProcesses()
            .stream()
            .filter(process -> PROCESS_ID.equals(process.getBpmnProcessId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "the fixture did not deploy a process with the id " + PROCESS_ID));

    report(
        "Deployed %s version %d (process definition key %d)",
        PROCESS_ID, deployed.getVersion(), deployed.getProcessDefinitionKey());

    final JsonLogger log = JsonLogger.create("warn", null);
    final long processId = ProcessHandle.current().pid();

    for (WorkerModule workerModule : Workers.ALL) {
      final Config.WorkerSettings settings = config.worker(workerModule.name());
      WORKERS.add(
          client
              .newWorker()
              .jobType(settings.taskType())
              .handler(Main.createTaskHandler(workerModule, config, log, services))
              .maxJobsActive(settings.maxJobsToActivate())
              .timeout(Duration.ofMillis(settings.timeoutMs()))
              .name(workerModule.name() + "-smoke-" + processId)
              .open());
    }

    // The client has no readiness callback, so the gateway is asked to confirm the connection
    // before the first instance is started. This is the same proof the Node run got from onReady.
    client.newTopologyRequest().send().join();
    report("Registered %d workers and they are polling.", WORKERS.size());
    report("");
  }

  @AfterAll
  static void stopWorkers() {
    for (JobWorker worker : WORKERS) {
      worker.close();
    }
    WORKERS.clear();
    if (client != null) {
      client.close();
    }
  }

  /** The variables a complete referral-to-treatment scenario needs. */
  private static Map<String, Object> normalScenario(String instanceSuffix) {
    return Vars.of(
        "documentsComplete", true,
        "referringOrganisation", "St Mary GP Surgery",
        "speciality", "Oncology",
        "priority", "routine",
        "requestedWindow", 14,
        "proposedTreatment", "FOLFOX cycle 1",
        "treatmentStartDate", "2026-10-01",
        "specialResources", List.of("pharmacy", "day unit"),
        "clinicalAuthorised", true,
        "chargeAmount", 250,
        "fundingRoute", "patient",
        "paymentReference", "PAY-SMOKE-" + instanceSuffix,
        "recipients", List.of("patient", "GP"),
        "documentType", "new_patient_clinic_letter");
  }

  @Test
  @DisplayName("Scenario 1 - the normal path runs end to end and every worker returns its result")
  void theNormalPathRunsEndToEnd() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());

    final Run run = runToCompletion(normalScenario("001"));
    final Map<String, Object> normal = run.variables();

    report("Scenario 1 - normal path");
    for (String key : new TreeSet<>(normal.keySet())) {
      report("  %s = %s", key, Json.write(normal.get(key)));
    }

    assertEquals(
        "complete",
        normal.get("validationResult"),
        "the referral should have been accepted as complete");
    assertEquals(true, normal.get("slotAvailable"), "an appointment should have been offered");
    assertNotNull(normal.get("appointmentDate"), "an appointment date should have been returned");
    assertEquals("confirmed", normal.get("bookingStatus"), "the treatment booking should be confirmed");
    assertTrue(
        String.valueOf(normal.get("appointmentReference")).startsWith("TRT-"),
        "the treatment appointment should be referenced");
    assertEquals(
        "completed", normal.get("paymentStatus"), "the payment should be recorded as completed");
    assertEquals(
        250, normal.get("paidAmount"), "the amount returned by the provider should be recorded");
    assertEquals("post", normal.get("dispatchChannel"), "the letter should be dispatched by post");
    assertNotNull(normal.get("dispatchReference"), "the dispatch should be referenced");

    final List<String> expectedNormalPath =
        List.of(
            "StartEvent_Smoke",
            "Task_ValidateReferral",
            "Task_AppointmentAvailability",
            "Task_TreatmentAvailability",
            "Task_ProcessPayment",
            "Task_SendCorrespondence",
            "EndEvent_Completed");

    final List<String> path =
        EngineTestSupport.visitedElementIds(run.instanceKey(), expectedNormalPath, 20000);
    report("  path: %s", String.join(" -> ", path));

    for (String elementId : expectedNormalPath) {
      assertTrue(path.contains(elementId), elementId + " should have been visited");
    }
    assertFalse(path.contains("EndEvent_NotAuthorised"), "the error path should not have been taken");

    report("Scenario 1 passed.");
    report("");
  }

  @Test
  @DisplayName("Scenario 2 - a booking without clinical authorisation is caught by the boundary error event")
  void aBookingWithoutAuthorisationTakesTheErrorPath() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());

    // The worker throws a BPMN error. The model catches it with a boundary event, so the instance
    // must complete on that path rather than stall on an incident.
    final Run run = runToCompletion(without(normalScenario("002"), "clinicalAuthorised", false));
    final Map<String, Object> blocked = run.variables();

    report("Scenario 2 - booking without clinical authorisation");

    assertNull(
        blocked.get("appointmentReference"),
        "no treatment appointment should exist for an unauthorised request");
    assertNull(
        blocked.get("paymentStatus"),
        "the process should not have reached payment after the error path was taken");

    final List<String> path =
        EngineTestSupport.visitedElementIds(
            run.instanceKey(), List.of("Task_TreatmentAvailability", "EndEvent_NotAuthorised"), 20000);
    report("  path: %s", String.join(" -> ", path));

    assertTrue(
        path.contains("EndEvent_NotAuthorised"), "the boundary error event should have been taken");
    assertFalse(
        path.contains("Task_ProcessPayment"), "the process must not continue past a refused booking");

    report(
        "Scenario 2 passed: the BPMN error was caught by the boundary event, no appointment and no payment.");
    report("");
    report("Smoke test passed: the workers obtain work and return results end to end.");
  }

  private static void report(String format, Object... arguments) {
    Console.out(format, arguments);
  }

  // ---------------------------------------------------------------------------

  /** An instance that ran to an end event, and the variables it left behind. */
  private record Run(long instanceKey, Map<String, Object> variables) {}

  /**
   * Starts an instance and waits for it to finish.
   *
   * The fixture is linear and has no user tasks, so nothing else has to be driven: every step is a
   * service task one of the workers serves.
   */
  private Run runToCompletion(Map<String, Object> variables) {
    final ProcessInstanceResult result =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(variables)
            .withResult()
            .requestTimeout(REQUEST_TIMEOUT)
            .send()
            .join();

    return new Run(result.getProcessInstanceKey(), result.getVariablesAsMap());
  }

  private static Map<String, Object> without(Map<String, Object> base, String key, Object value) {
    final Map<String, Object> copy = new LinkedHashMap<>(base);
    copy.put(key, value);
    return copy;
  }
}
