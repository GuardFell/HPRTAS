package uk.ac.uwe.hprtas.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import uk.ac.uwe.hprtas.workers.services.Services;

/**
 * End-to-end run of the four operational models against the real engine.
 *
 * Run with {@code mvn test -Pengine -Dtest=OperationalModelsTest} while the engine is running.
 * {@link SmokeTest} proves the workers against a purpose-built linear fixture; this proves them
 * against the delivered models: the jobs come from the service tasks of
 * {@code models/operational/core-1..4}, the gateways read what the workers return, the user tasks are
 * completed the way a Tasklist user completes them, and the path is read back from the Orchestration
 * Cluster API.
 *
 * Five scenarios:
 *
 * <ol>
 *   <li>{@code core-1} normal referral path, to the appointment being arranged</li>
 *   <li>{@code core-2} treatment authorisation, payment and the between-cycle review</li>
 *   <li>{@code core-3} clinic letter distributed</li>
 *   <li>{@code core-4} follow-up requested, to the follow-up appointment being booked</li>
 *   <li>{@code core-4} cancellation of a paid appointment, to the refund being recorded</li>
 * </ol>
 *
 * Scenario 5 runs after scenario 2 on purpose: it refunds the payment reference scenario 2 settled,
 * which is the only way to exercise {@code refund-processing} against a provider that really holds
 * the transaction. The order the scenarios run in is therefore declared rather than left to the
 * runner, because one of them depends on what an earlier one did.
 */
@Tag("engine")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OperationalModelsTest {

  private static final List<String> MODELS =
      List.of(
          "core-1-referral-and-new-patient-appointment.bpmn",
          "core-2-treatment-authorisation-funding-and-payment.bpmn",
          "core-3-clinic-letter-and-pathway-escalation.bpmn",
          "core-4-follow-up-cancellation-enquiry-and-refund.bpmn");

  private static final String PAYMENT_REFERENCE = "PAY-E2E-0001";

  private static Config config;
  private static Services services;
  private static CamundaClient client;
  private static final List<JobWorker> WORKERS = new ArrayList<>();

  @BeforeAll
  static void deployAndStartWorkers() {
    // Returned from rather than aborted: an aborted class reports no tests at all, which reads the
    // same as a class that has none. Each scenario skips itself instead, so the skip is counted and
    // says which address was tried.
    if (!EngineTestSupport.engineIsRunning()) {
      return;
    }

    config = Config.load();
    services = Services.create(config);
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

    report("External workers against the four operational models");
    report("=".repeat(74));
    report(
        "Camunda 8 Run via %s, gRPC %s",
        EngineTestSupport.BASE_URL, config.connection().get("ZEEBE_GRPC_ADDRESS"));
    report("");

    client = CamundaClients.create(config);

    // The working directory is the workers directory, so the models and forms sit beside it.
    final Path repository = config.workersDir().getParent();
    deploy(repository.resolve("models").resolve("operational"), MODELS);
    try (Stream<Path> forms = Files.list(repository.resolve("forms"))) {
      deploy(repository.resolve("forms"), forms.filter(p -> p.toString().endsWith(".form")).map(Path::getFileName).map(Path::toString).toList());
    } catch (Exception e) {
      throw new IllegalStateException("the forms directory could not be read", e);
    }

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
              .name(workerModule.name() + "-e2e-" + processId)
              .open());
    }

    client.newTopologyRequest().send().join();
    report(
        "registered %d workers: %s",
        WORKERS.size(), String.join(", ", Workers.ALL.stream().map(WorkerModule::name).toList()));
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

  @Test
  @Order(1)
  @DisplayName("Scenario 1 - core-1, normal referral and new patient appointment")
  void core1NormalReferralPath() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    final Driven run =
        drive(
            "Scenario 1 - core-1, normal referral and new patient appointment",
            "core-1-referral-and-new-patient-appointment",
            Vars.of(),
            Vars.of(
                "N_MS_CheckReferral",
                    Vars.of(
                        "documentsComplete", true, "referringOrganisation", "St Mary GP Surgery"),
                "N_C_ClinicalReview", Vars.of("decision", "accepted"),
                "N_OB_PrepareRequest",
                    Vars.of(
                        "speciality", "Oncology",
                        "priority", "routine",
                        "requestedWindow", 14,
                        "recipients", List.of("patient", "GP"),
                        "documentType", "new_patient_clinic_letter"),
                "N_OB_TelephonePatient", Vars.of("contactOutcome", "contacted")));

    for (String id : List.of("N_MS_ValidateReferral", "N_OB_CheckAvailability", "N_OB_SendNotification")) {
      assertTrue(run.elements().contains(id), "core-1 should have run " + id);
    }
    assertTrue(
        run.elements().contains("N_OB_AppointmentArranged"),
        "core-1 should end with the appointment arranged");
  }

  @Test
  @Order(2)
  @DisplayName("Scenario 2 - core-2, treatment authorisation, funding, payment and review")
  void core2AuthorisationPaymentAndReview() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    final Driven run =
        drive(
            "Scenario 2 - core-2, treatment authorisation, funding, payment and review",
            "core-2-treatment-authorisation-funding-and-payment",
            Vars.of(),
            Vars.of(
                "N_CL_AssessPatient", Vars.of("consentGiven", true),
                "N_CL_AuthoriseTreatment",
                    Vars.of(
                        "proposedTreatment", "FOLFOX cycle 1",
                        "treatmentStartDate", "2026-10-01",
                        "specialResources", List.of("pharmacy", "day unit"),
                        "clinicalAuthorised", true,
                        "authorisingClinician", "Dr Chen"),
                "N_TB_VerifyAuthorisation", Vars.of(),
                "N_F_DetermineFunding",
                    Vars.of(
                        "fundingRoute", "patient",
                        "fundingApprovalRequired", false,
                        "paymentRequiredFromPatient", true),
                "N_F_CalculateCharge",
                    Vars.of(
                        "chargeAmount", 250,
                        "fundingRoute", "patient",
                        "paymentReference", PAYMENT_REFERENCE),
                "N_TB_ConfirmAppointment",
                    Vars.of("recipients", List.of("patient"), "documentType", "appointment_letter"),
                "N_OC_PreCycleReview", Vars.of("fitToContinue", false),
                "N_CL_ModifyTreatment", Vars.of("affectsCharge", true)));

    for (String id : List.of("N_TB_CheckAvailability", "N_F_ProcessPayment", "N_TB_NotifyPatient")) {
      assertTrue(run.elements().contains(id), "core-2 should have run " + id);
    }
    assertTrue(
        run.elements().contains("N_F_ImpactReviewed"),
        "core-2 should end on the financial review");
  }

  @Test
  @Order(3)
  @DisplayName("Scenario 3 - core-3, clinic letter prepared, approved and distributed")
  void core3ClinicLetter() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    final Driven run =
        drive(
            "Scenario 3 - core-3, clinic letter prepared, approved and distributed",
            "core-3-clinic-letter-and-pathway-escalation",
            Vars.of(),
            Vars.of(
                "N_C_PrepareLetter",
                    Vars.of(
                        "consultationId", "CONS-E2E-0001",
                        "recipients", List.of("patient", "GP"),
                        "documentType", "new_patient_clinic_letter"),
                "N_C_ApproveLetter", Vars.of(),
                "N_MS_ProcessLetter", Vars.of("suspectedClinicalError", false),
                "N_MS_ConfirmRecipients", Vars.of()));

    assertTrue(
        run.elements().contains("N_MS_SendLetter"), "core-3 should have run the letter dispatch");
    assertTrue(
        run.elements().contains("N_MS_LetterDistributed"),
        "core-3 should end with the letter distributed");
  }

  @Test
  @Order(4)
  @DisplayName("Scenario 4 - core-4, follow-up requested and booked")
  void core4FollowUpBooked() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    final Driven run =
        drive(
            "Scenario 4 - core-4, follow-up requested and booked",
            "core-4-follow-up-cancellation-enquiry-and-refund",
            Vars.of(),
            Vars.of(
                "N_OB_ArrangeFollowUp",
                    Vars.of(
                        "speciality", "Oncology",
                        "priority", "routine",
                        "requestedWindow", 21,
                        "recipients", List.of("patient"),
                        "documentType", "follow_up_clinic_letter"),
                "N_OB_ConfirmFollowUp", Vars.of()));

    assertTrue(
        run.elements().contains("N_OB_FollowUpBooked"),
        "core-4 should end with the follow-up booked");
  }

  @Test
  @Order(5)
  @DisplayName("Scenario 5 - core-4, paid appointment cancelled and refunded")
  void core4CancellationAndRefund() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // Started by a message, and it refunds the reference scenario 2 settled.
    final Driven run =
        drive(
            "Scenario 5 - core-4, paid appointment cancelled and refunded",
            "core-4-follow-up-cancellation-enquiry-and-refund",
            "patient-cancellation-or-non-attendance",
            Vars.of(),
            Vars.of(
                "N_OB_RecordCancellation",
                    Vars.of(
                        "paidAppointment", true,
                        "offerAnotherAppointment", false,
                        "pathwayReviewRequired", false),
                "N_F_RetentionDecision",
                    Vars.of(
                        "refundDecision", "full",
                        "paymentReference", PAYMENT_REFERENCE,
                        "refundReason", "cancelled")));

    assertTrue(
        run.elements().contains("N_F_ProcessRefund"),
        "core-4 should have asked the provider for the refund");
    assertTrue(
        run.elements().contains("N_F_RefundRecorded"),
        "core-4 should end with the refund recorded");
    assertFalse(
        run.elements().contains("N_F_CorrectRefundRequest"),
        "the refund should have been accepted, not sent back for correction");

    report("");
    report("=".repeat(74));
    report("All four operational models ran against the real workers. Every service task");
    report("in them was reached, every gateway routed on what the workers returned, and");
    report("the refund in scenario 5 was recorded by the provider against the payment");
    report("scenario 2 settled.");
  }

  // ---------------------------------------------------------------------------

  /** An instance that ran to an end event. */
  private record Driven(long instanceKey, List<String> elements, Set<String> userTasks) {}

  private static void deploy(Path directory, List<String> fileNames) {
    for (String fileName : fileNames) {
      final Path file = directory.resolve(fileName);
      final var deployment =
          client.newDeployResourceCommand().addResourceFile(file.toString()).send().join();

      deployment.getProcesses().forEach(process ->
          report(
              "deployed %s version %d",
              process.getBpmnProcessId(), process.getVersion()));
      deployment.getForm().forEach(form -> report("deployed form %s", form.getFormId()));
    }
  }

  /** Starts an instance, completes every user task it opens, and reports the path it took. */
  private Driven drive(
      String label, String processDefinitionId, Map<String, Object> variables, Map<String, Object> taskVariables) {
    return drive(label, processDefinitionId, null, variables, taskVariables);
  }

  private Driven drive(
      String label,
      String processDefinitionId,
      String message,
      Map<String, Object> variables,
      Map<String, Object> taskVariables) {

    final long instanceKey = start(processDefinitionId, message, variables, label);

    report("");
    report("%s", label);
    report("  instance %d", instanceKey);

    final Set<String> completed = new LinkedHashSet<>();
    final Set<String> visitedTasks = new LinkedHashSet<>();
    final long deadline = System.currentTimeMillis() + 90000;

    while (true) {
      for (EngineTestSupport.UserTask task : EngineTestSupport.openUserTasks(instanceKey)) {
        if (completed.contains(task.userTaskKey())) {
          continue;
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> supplied =
            (Map<String, Object>) taskVariables.getOrDefault(task.elementId(), Map.of());
        EngineTestSupport.completeUserTask(task.userTaskKey(), supplied);
        completed.add(task.userTaskKey());
        visitedTasks.add(task.elementId());
        report("  %-32s completed with %s", task.elementId(), Json.write(supplied));
      }

      final String state = EngineTestSupport.instanceState(instanceKey);
      if (state != null && !"ACTIVE".equals(state)) {
        report("  final state: %s", state);
        break;
      }
      if (System.currentTimeMillis() > deadline) {
        report("  TIMED OUT waiting for the instance to finish");
        break;
      }
      EngineTestSupport.sleep(500);
    }

    // The exporter lags behind the instance, so the path is read once things have settled.
    EngineTestSupport.sleep(1500);
    final List<String> elements = EngineTestSupport.visitedElementIds(instanceKey);
    report("  path: %s", String.join(" -> ", elements));

    return new Driven(instanceKey, elements, visitedTasks);
  }

  /** Starts an instance directly, or by publishing the message that starts the model. */
  private long start(
      String processDefinitionId, String message, Map<String, Object> variables, String label) {

    if (message == null) {
      return client
          .newCreateInstanceCommand()
          .bpmnProcessId(processDefinitionId)
          .latestVersion()
          .variables(variables)
          .send()
          .join()
          .getProcessInstanceKey();
    }

    client
        .newPublishMessageCommand()
        .messageName(message)
        .correlationKey(message + "-" + System.currentTimeMillis())
        .timeToLive(Duration.ofMinutes(1))
        .variables(variables)
        .send()
        .join();

    final long deadline = System.currentTimeMillis() + 20000;
    while (System.currentTimeMillis() < deadline) {
      final Long key = runningInstanceOf(processDefinitionId);
      if (key != null) {
        return key;
      }
      EngineTestSupport.sleep(500);
    }
    throw new IllegalStateException(label + ": the message started nothing");
  }

  @SuppressWarnings("unchecked")
  private Long runningInstanceOf(String processDefinitionId) {
    final Map<String, Object> body =
        EngineTestSupport.post(
            "/v2/process-instances/search", Map.of("filter", Map.of("processDefinitionId", processDefinitionId)));

    for (Object item : (List<Object>) body.getOrDefault("items", List.of())) {
      final Map<String, Object> instance = (Map<String, Object>) item;
      if ("ACTIVE".equals(String.valueOf(instance.get("state")))) {
        return Long.valueOf(String.valueOf(instance.get("processInstanceKey")));
      }
    }
    return null;
  }

  private static void report(String format, Object... arguments) {
    Console.out(format, arguments);
  }
}
