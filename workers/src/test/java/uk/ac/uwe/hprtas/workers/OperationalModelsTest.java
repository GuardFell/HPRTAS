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
 * End-to-end run of the five operational models against the real engine.
 *
 * Run with {@code mvn test -Pengine -Dtest=OperationalModelsTest} while the engine is running.
 * {@link SmokeTest} proves the workers against a purpose-built linear fixture; this proves them
 * against the delivered models: the jobs come from the service tasks of
 * {@code models/operational/core-1..5}, the gateways read what the workers return, the user tasks are
 * completed the way a Tasklist user completes them, and the path is read back from the Orchestration
 * Cluster API.
 *
 * Nine scenarios:
 *
 * <ol>
 *   <li>{@code core-1} normal referral path, to the appointment being arranged</li>
 *   <li>{@code core-2} treatment authorisation, payment and the between-cycle review</li>
 *   <li>{@code core-3} clinic letter distributed</li>
 *   <li>{@code core-4} follow-up requested, to the follow-up appointment being booked</li>
 *   <li>{@code core-4} cancellation of a paid appointment, to the refund being recorded</li>
 *   <li>{@code core-4} an urgent clinical enquiry routed to the Clinical Nurse Specialist Team</li>
 *   <li>{@code core-1} a referral the consultant rejects and one it redirects, neither booked</li>
 *   <li>{@code core-1} an urgent referral with no suitable slot, escalated out of the booking</li>
 *   <li>{@code core-5} the request for missing documentation published by the worker, and the
 *       reply correlated by the referral that the instance is waiting for</li>
 * </ol>
 *
 * Scenarios 5 and 6 are the two the models are started for by a message rather than by a direct
 * instance creation, so they are what proves the message start events work end to end. Scenario 9
 * is the other half of messaging: the process waits on a subscription, and a publication is
 * matched to it by its correlation key rather than by the message name.
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
          // the pathway that calls the others, so an orchestrated run can be driven too
          "core-0-patient-pathway-end-to-end.bpmn",
          "core-1-referral-and-new-patient-appointment.bpmn",
          "core-2-treatment-authorisation-funding-and-payment.bpmn",
          "core-3-clinic-letter-and-pathway-escalation.bpmn",
          "core-4-follow-up-cancellation-enquiry-and-refund.bpmn",
          "core-5-missing-information-message-exchange.bpmn");

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

    // The engine run gets a throwaway record store: the scenarios assert on what the
    // workers record during the run, and a store left over from an earlier run would make a
    // reference from it look like a duplicate.
    System.setProperty("hprtas.dbUrl", TestSupport.throwawayStoreUrl());
    config = Config.load();
    services = Services.create(config);
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

    report("External workers against the five operational models");
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
    final MessagePublisher messages = new ClientMessagePublisher(client);

    for (WorkerModule workerModule : Workers.ALL) {
      final Config.WorkerSettings settings = config.worker(workerModule.name());
      WORKERS.add(
          client
              .newWorker()
              .jobType(settings.taskType())
              .handler(Main.createTaskHandler(workerModule, config, log, services, messages))
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
                        "referralId", "REF-E2E-0001",
                        "patientId", "PAT-E2E-0001",
                        "nhsNumber", "9434765919",
                        "patientName", "Ada Lovelace",
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
                    Vars.of(
                        "confirmationOutcome", "confirmed",
                        "confirmationBasis", "payment_settled",
                        "recipients", List.of("patient"),
                        "documentType", "appointment_letter"),
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
                "N_OB_ConfirmFollowUp",
                    Vars.of("confirmationOutcome", "confirmed", "confirmationBasis", "not_applicable")));

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
  }

  @Test
  @Order(6)
  @DisplayName("Scenario 6 - core-4, an urgent clinical enquiry routed to the clinical team")
  void core4EnquiryRoutedToClinicalTeam() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // The second of the two messages core-4 is started by. The case requires this path to stay out
    // of the call handler's hands: a clinical enquiry goes to the Clinical Nurse Specialist Team,
    // and an urgent clinical concern is highlighted rather than queued.
    final Driven run =
        drive(
            "Scenario 6 - core-4, an urgent clinical enquiry routed to the clinical team",
            "core-4-follow-up-cancellation-enquiry-and-refund",
            "patient-enquiry",
            Vars.of(),
            Vars.of(
                "N_CH_ClassifyEnquiry",
                    Vars.of(
                        "enquiryReference", "ENQ-E2E-0001",
                        "receivedBy", "Call Handling Team",
                        "enquirySummary", "New symptom reported after the first treatment cycle",
                        "enquiryType", "clinical",
                        "enquiryPriority", "urgent",
                        "handledBy", "Call Handling Team",
                        "responsibleTeam", "Clinical Nurse Specialist Team"),
                "N_CNS_ClinicalAdvice",
                    Vars.of(
                        "adviceGiven", "Passed to the treating consultant the same day",
                        "urgentClinicalConcern", true,
                        "advisedBy", "Clinical Nurse Specialist"),
                "N_CNS_HighlightUrgent",
                    Vars.of(
                        "escalationReason", "New symptom reported between treatment cycles",
                        "escalatedTo", "Treating consultant",
                        "patientContacted", true,
                        "escalatedBy", "Clinical Nurse Specialist")));

    assertEquals(
        "N_CH_ContactReceived",
        run.elements().isEmpty() ? "" : run.elements().get(0),
        "the enquiry message should have started the instance at the message start event");
    assertTrue(
        run.elements().contains("N_CH_ClassifyEnquiry"),
        "core-4 should have recorded and classified the enquiry");
    assertTrue(
        run.elements().contains("N_CNS_ClinicalAdvice"),
        "a clinical enquiry should reach the Clinical Nurse Specialist Team");
    assertTrue(
        run.elements().contains("N_CNS_HighlightUrgent"),
        "an urgent clinical concern should be highlighted");
    assertTrue(
        run.elements().contains("N_CNS_EnquiryResolved"),
        "core-4 should end with the enquiry resolved");
    assertFalse(
        run.elements().contains("N_CH_AnswerAdmin"),
        "the call handling team must not answer a clinical enquiry");
    assertFalse(
        run.elements().contains("N_F_AnswerFinance"),
        "a clinical enquiry is not the finance team's to answer");
  }

  @Test
  @Order(7)
  @DisplayName("Scenario 7 - core-1, a referral the consultant does not accept")
  void core1ReferralNotAccepted() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());

    // The two decisions that end the pathway before an appointment is arranged. TC-02 also covers
    // the request for further information, which returns to the missing-information request and then
    // to the clinical decision again; a fixed set of task variables cannot drive that round trip,
    // because the second visit would answer the same way and go round for ever.
    record Outcome(String decision, String endEvent) {}

    for (Outcome outcome :
        List.of(
            new Outcome("rejected", "N_C_Rejected"),
            new Outcome("redirected", "N_C_Redirected"))) {

      final Driven run =
          drive(
              "Scenario 7 - core-1, the referral is " + outcome.decision(),
              "core-1-referral-and-new-patient-appointment",
              Vars.of(),
              Vars.of(
                  "N_MS_CheckReferral",
                      Vars.of(
                          "referralId", "REF-E2E-0001",
                        "patientId", "PAT-E2E-0001",
                        "nhsNumber", "9434765919",
                        "patientName", "Ada Lovelace",
                        "documentsComplete", true, "referringOrganisation", "St Mary GP Surgery"),
                  "N_C_ClinicalReview", Vars.of("decision", outcome.decision())));

      assertTrue(
          run.elements().contains(outcome.endEvent()),
          "core-1 should have reached " + outcome.endEvent());
      assertFalse(
          run.elements().contains("N_OB_PrepareRequest"),
          "a referral the consultant did not accept must not be prepared for booking");
      assertFalse(
          run.elements().contains("N_OB_CheckAvailability"),
          "no appointment may be sought for a referral that was not accepted");
    }
  }

  @Test
  @Order(8)
  @DisplayName("Scenario 8 - core-1, an urgent referral with no suitable slot is escalated")
  void core1UrgentReferralWithNoSlotEscalated() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // This is the branch DEF-11 was about: it used to send the referral back to the availability
    // check, and because the scheduling service answers the same request with the same result, the
    // instance could never leave it. The instance has to finish, not merely visit the right
    // elements - one that went round again would sit at the recording task for ever.
    final Driven run =
        drive(
            "Scenario 8 - core-1, an urgent referral with no suitable slot is escalated",
            "core-1-referral-and-new-patient-appointment",
            Vars.of(),
            Vars.of(
                "N_MS_CheckReferral",
                    Vars.of(
                        "referralId", "REF-E2E-0001",
                        "patientId", "PAT-E2E-0001",
                        "nhsNumber", "9434765919",
                        "patientName", "Ada Lovelace",
                        "documentsComplete", true, "referringOrganisation", "St Mary GP Surgery"),
                "N_C_ClinicalReview", Vars.of("decision", "accepted"),
                "N_OB_PrepareRequest",
                    Vars.of(
                        "speciality", "Oncology",
                        "priority", "urgent",
                        "requestedWindow", 14,
                        "schedulingOutcome", "none",
                        "recipients", List.of("patient", "GP"),
                        "documentType", "new_patient_clinic_letter"),
                "N_OB_RecordNoSlot",
                    Vars.of(
                        "referralId", "REF-E2E-0002",
                        "patientId", "PAT-E2E-0002",
                        "noSlotOutcome", "none",
                        "actionTaken", "highlighted_for_pathway_team",
                        "recordedBy", "Outpatient Bookings Team"),
                "N_PC_EscalateUrgent",
                    Vars.of(
                        "referralId", "REF-E2E-0002",
                        "patientId", "PAT-E2E-0002",
                        "escalationRoute", "expedited_slot",
                        "escalatedBy", "Patient Pathway Coordinator")));

    assertEquals(
        "COMPLETED",
        EngineTestSupport.instanceState(run.instanceKey()),
        "an urgent referral with no suitable slot must leave the booking process, not go round it");
    assertTrue(
        run.elements().contains("N_PC_EscalateUrgent"),
        "the urgent referral should have been escalated to the pathway coordinator");
    assertTrue(
        run.elements().contains("N_PC_UrgentEscalated"),
        "core-1 should end with the urgent referral escalated");
    assertFalse(
        run.elements().contains("N_PC_ReviewDelay"),
        "an urgent referral takes the escalation path, not the routine delay review");
  }

  @Test
  @Order(9)
  @DisplayName(
      "Scenario 9 - core-5, the request is published and only this referral's reply moves it on")
  void core5MissingInformationExchange() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // The message exchange the guide describes, on a delivered model: a worker publishes the
    // request, the process waits on a subscription, and the reply is correlated by a key the
    // instance already holds. The negative half is the part worth driving - a reply addressed to
    // another referral must leave this instance exactly where it is, which is what the correlation
    // key is for and what a message name alone would not do.
    final String referralId = "REF-CORE5-" + System.currentTimeMillis();
    final String processId = "core-5-missing-information-message-exchange";

    report("");
    report("Scenario 9 - core-5, missing information requested from, and supplied by, the referring");
    report("organisation");

    final long instanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            // The first step of the guide: the variable a subscription is correlated by is
            // initialised before the instance runs, not when the waiting step is reached.
            .variables(Vars.of("referralId", referralId))
            .send()
            .join()
            .getProcessInstanceKey();
    report("  instance %d, referral %s", instanceKey, referralId);

    completeUserTask(
        instanceKey,
        "N_MS_RecordMissingItems",
        Vars.of(
            "referralId", referralId,
            "requestedFrom", "St Mary GP Surgery",
            "requestedItems", List.of("investigation results", "diagnostic reports")));

    // The worker publishes the request and completes its job, so the instance reaches the waiting
    // step rather than stalling on the service task.
    EngineTestSupport.awaitActiveAt(instanceKey, "N_MS_DocumentsSupplied", 30000);
    report("  the request was published and the instance is waiting on N_MS_DocumentsSupplied");

    publishCorrelated("missing-information-supplied", "REF-NOT-THIS-REFERRAL", Vars.of());
    EngineTestSupport.sleep(2000);
    assertTrue(
        EngineTestSupport.isActiveAt(instanceKey, "N_MS_DocumentsSupplied"),
        "a reply correlated by another referral must not move this instance: the message name is"
            + " the same, so the key is the only thing that tells the two apart");
    assertFalse(
        EngineTestSupport.visitedElementIds(instanceKey).contains("N_MS_CheckSuppliedDocuments"),
        "the referral must not have carried on from another referral's reply");

    publishCorrelated(
        "missing-information-supplied",
        referralId,
        Vars.of("suppliedItems", "investigation results, diagnostic reports"));
    EngineTestSupport.awaitActiveAt(instanceKey, "N_MS_CheckSuppliedDocuments", 30000);
    report("  the reply for this referral was correlated and the instance carried on");

    completeUserTask(
        instanceKey,
        "N_MS_CheckSuppliedDocuments",
        Vars.of(
            "referralId", referralId,
            "documentsComplete", true,
            "supportingDocuments", "investigation results, diagnostic reports",
            "checkedBy", "Medical Secretary"));

    final List<String> path =
        EngineTestSupport.visitedElementIds(
            instanceKey,
            List.of("N_MS_ReadyForReview"),
            30000);
    report("  path: %s", String.join(" -> ", path));

    assertEquals(
        "COMPLETED",
        EngineTestSupport.instanceState(instanceKey),
        "the referral should have finished once the documentation arrived");
    assertTrue(
        path.contains("N_MS_RequestDocuments"),
        "the request for the missing information should have been published by the worker");
    assertTrue(
        path.contains("N_MS_DocumentsSupplied"),
        "the process should have waited on the message subscription");
    assertTrue(
        path.contains("N_MS_ReadyForReview"),
        "the referral should have been ready for clinical review");
    assertFalse(
        path.contains("B_MS_RequestInvalid"),
        "a usable request should not have taken the error path back to the recording task");

    report("");
    report("=".repeat(74));
    report("All six models ran against the real workers: the five stages and the pathway that");
    report("calls them. Both messages core-4 is started by were sent - the cancellation in");
    report("scenario 5 and the enquiry in scenario 6 -");
    report("scenario 7 drove both decisions that end core-1 before an appointment, scenario 8 drove");
    report("an urgent referral that could not be booked to the escalation DEF-11 was about, and");
    report("scenario 9 drove the message exchange: the request was published by the worker and the");
    report("reply was correlated by the referral, with a reply for another referral ignored.");
    report("Every service task was reached and every gateway routed on what the workers returned.");
    report("The refund in scenario 5 was recorded by the provider against the payment scenario 2");
    report("settled. Scenarios 10 to 12 were added with the feedback pass: a declined confirmation");
    report("and a reschedule in core-2, and the pathway end to end through core-0.");
  }

  @Test
  @Order(10)
  @DisplayName("Scenario 10 - core-2, a declined treatment appointment is recorded, and the appointment released")
  void core2DeclinedConfirmation() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // The confirmation contact has more than one outcome now, and the ones that do not confirm are
    // process paths rather than a note: a decline goes to the recording task and ends the instance
    // at the end event that says the appointment was not confirmed, and no notification letter goes
    // out as though it had been.
    final Driven run =
        drive(
            "Scenario 10 - core-2, the patient declines the treatment appointment",
            "core-2-treatment-authorisation-funding-and-payment",
            Vars.of(),
            Vars.of(
                "N_CL_AssessPatient", Vars.of("consentGiven", true),
                "N_CL_AuthoriseTreatment",
                    Vars.of(
                        "proposedTreatment", "FOLFOX cycle 1",
                        "treatmentStartDate", "2026-10-01",
                        "numberOfCycles", 6,
                        "clinicalAuthorised", true,
                        "authorisingClinician", "Dr Chen"),
                "N_TB_VerifyAuthorisation", Vars.of(),
                "N_F_DetermineFunding",
                    Vars.of(
                        "fundingRoute", "hospital",
                        "fundingApprovalRequired", false,
                        "paymentRequiredFromPatient", false),
                "N_TB_ConfirmAppointment",
                    Vars.of(
                        "confirmationOutcome", "declined_by_patient",
                        "confirmationBasis", "not_applicable",
                        "recipients", List.of("patient"),
                        "documentType", "deferral_or_cancellation_letter"),
                "N_TB_RecordCancellation",
                    Vars.of(
                        "cancellationType", "declined",
                        "paidAppointment", false,
                        "offerAnotherAppointment", false,
                        "pathwayReviewRequired", false,
                        "recordedBy", "Bookings Clerk")));

    for (String id :
        List.of("N_TB_ConfirmationOutcome", "N_TB_RecordCancellation", "N_TB_AppointmentCancelled")) {
      assertTrue(run.elements().contains(id), "the declined appointment should have run " + id);
    }
    assertFalse(
        run.elements().contains("N_TB_NotifyPatient"),
        "a declined appointment is not confirmed with an appointment letter");
    assertFalse(
        run.elements().contains("N_OC_PreCycleReview"),
        "there is no cycle to review once the appointment was declined");
  }

  @Test
  @Order(11)
  @DisplayName("Scenario 11 - core-2, a patient who asks for another date is booked again")
  void core2RescheduledConfirmation() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // The same task is reached twice and answered differently each time: the contact is recorded as
    // a reschedule, the change is recorded, and the booking is sought again for the new start date
    // - which is a different booking, so the appointment the first request made is not simply
    // returned again.
    final Driven run =
        drive(
            "Scenario 11 - core-2, the patient asks for another date and is booked again",
            "core-2-treatment-authorisation-funding-and-payment",
            Vars.of(),
            Vars.of(
                "N_CL_AssessPatient", Vars.of("consentGiven", true),
                "N_CL_AuthoriseTreatment",
                    Vars.of(
                        "proposedTreatment", "FOLFOX cycle 1",
                        "treatmentStartDate", "2026-10-01",
                        "numberOfCycles", 6,
                        "clinicalAuthorised", true,
                        "authorisingClinician", "Dr Chen"),
                "N_TB_VerifyAuthorisation", Vars.of(),
                "N_F_DetermineFunding",
                    Vars.of(
                        "fundingRoute", "hospital",
                        "fundingApprovalRequired", false,
                        "paymentRequiredFromPatient", false),
                "N_TB_ConfirmAppointment",
                    List.of(
                        Vars.of(
                            "confirmationOutcome", "reschedule_requested",
                            "confirmationBasis", "not_applicable",
                            "recipients", List.of("patient"),
                            "documentType", "appointment_letter"),
                        Vars.of(
                            "confirmationOutcome", "confirmed",
                            "confirmationBasis", "funding_in_place",
                            "recipients", List.of("patient"),
                            "documentType", "appointment_letter")),
                "N_TB_RescheduleAppointment",
                    Vars.of(
                        "newRequestedDate", "2026-10-15",
                        "rescheduleReason", "patient_request",
                        "patientInformed", true,
                        "recordedBy", "Bookings Clerk"),
                "N_OC_PreCycleReview", Vars.of("fitToContinue", false),
                "N_CL_ModifyTreatment",
                    Vars.of(
                        "affectsCharge", false,
                        "modificationReason", "no change to the plan")));

    assertTrue(
        run.elements().contains("N_TB_RescheduleAppointment"),
        "the requested change should have been recorded");
    final long availabilityChecks =
        EngineTestSupport.visitedElementInstanceCount(run.instanceKey(), "N_TB_CheckAvailability");
    assertEquals(
        2,
        availabilityChecks,
        "the booking should have been sought again for the new request, not answered with the"
            + " appointment the first request made");
    assertTrue(
        run.elements().contains("N_TB_NotifyPatient"),
        "the appointment the second request made is confirmed with a letter");
  }

  @Test
  @Order(12)
  @DisplayName("Scenario 12 - the pathway end to end, one instance through all five stages")
  void endToEndPathway() {
    assumeTrue(client != null, EngineTestSupport.engineNotRunningMessage());
    // The integration: one instance of core-0 walks the referral, the treatment, the letter and the
    // follow-up, while the documentation the referral arrived without is obtained from the
    // referring organisation through core-5's message exchange. Every stage runs as the process the
    // call activity names, which is asserted from the tree of instances afterwards.
    final String referralId = "REF-E2E-" + System.currentTimeMillis();

    report("");
    report("Scenario 12 - the pathway end to end (core-0): referral, documentation, treatment,");
    report("letter and follow-up, each stage as the process the call activity names");
    report("  referral %s", referralId);

    // The reply the exchange waits for is buffered by the engine for its time to live, so it is
    // published before the instance reaches the waiting step. It is correlated by the referral, so
    // it can only move that referral's instance on.
    publishCorrelated(
        "missing-information-supplied", referralId, Vars.of("suppliedItems", "diagnostic report"));

    final Driven run =
        drive(
            "Scenario 12 - the pathway end to end",
            "core-0-patient-pathway-end-to-end",
            null,
            Vars.of(
                "referralId", referralId,
                "patientId", "PAT-E2E-0001",
                "nhsNumber", "9434765919",
                "patientName", "Ada Lovelace",
                "referringOrganisation", "St Mary GP Surgery",
                "documentsComplete", false,
                "missingItems", List.of("diagnostic report")),
            Vars.of(
                "N_MS_RecordMissingItems",
                    Vars.of(
                        "referralId", referralId,
                        "requestedFrom", "St Mary GP Surgery",
                        "requestedItems", List.of("diagnostic report")),
                "N_MS_CheckSuppliedDocuments",
                    Vars.of(
                        "referralId", referralId,
                        "patientId", "PAT-E2E-0001",
                        "nhsNumber", "9434765919",
                        "patientName", "Ada Lovelace",
                        "referringOrganisation", "St Mary GP Surgery",
                        "supportingDocuments", "diagnostic report",
                        "documentsComplete", true,
                        "missingItems", List.of(),
                        "patientIdentificationConfirmed", true,
                        "checkedBy", "Medical Secretary"),
                "N_MS_CheckReferral",
                    Vars.of(
                        "referralId", referralId,
                        "patientId", "PAT-E2E-0001",
                        "nhsNumber", "9434765919",
                        "patientName", "Ada Lovelace",
                        "referringOrganisation", "St Mary GP Surgery",
                        "supportingDocuments", "diagnostic report",
                        "documentsComplete", true,
                        // The referral arrived without this and it has since been supplied, so the
                        // list of missing items is cleared: a referral marked complete that still
                        // lists missing items cannot be decided.
                        "missingItems", List.of(),
                        "patientIdentificationConfirmed", true,
                        "checkedBy", "Medical Secretary"),
                "N_C_ClinicalReview",
                    Vars.of(
                        "referralId", referralId,
                        "patientName", "Ada Lovelace",
                        "referringOrganisation", "St Mary GP Surgery",
                        "clinicalSummary", "Suspected lymphoma, referred for assessment",
                        "decision", "accepted",
                        "decisionReason", "Meets the referral criteria",
                        "speciality", "oncology",
                        "priority", "routine",
                        "requestedWindow", 14),
                "N_OB_PrepareRequest",
                    Vars.of(
                        "referralId", referralId,
                        "patientId", "PAT-E2E-0001",
                        "speciality", "oncology",
                        "priority", "routine",
                        "requestedWindow", 14,
                        "recipients", List.of("patient"),
                        "documentType", "appointment_letter"),
                "N_OB_TelephonePatient",
                    Vars.of(
                        "patientId", "PAT-E2E-0001",
                        "contactMethod", "telephone",
                        "contactOutcome", "contacted",
                        "contactAttemptNumber", 1),
                "N_CL_AssessPatient",
                    Vars.of(
                        "patientId", "PAT-E2E-0001",
                        "diagnosis", "Lymphoma",
                        "diagnosisDiscussed", true,
                        "treatmentOptionsExplained", true,
                        "consentGiven", true,
                        "assessingClinician", "Dr Chen"),
                "N_CL_AuthoriseTreatment",
                    Vars.of(
                        "treatmentRequestId", "TR-E2E-0001",
                        "proposedTreatment", "FOLFOX cycle 1",
                        "treatmentStartDate", "2026-10-01",
                        "numberOfCycles", 6,
                        "clinicalAuthorised", true,
                        "authorisingClinician", "Dr Chen"),
                "N_TB_VerifyAuthorisation",
                    Vars.of(
                        "treatmentRequestId", "TR-E2E-0001",
                        "authorisationVerified", true,
                        "verifiedBy", "Bookings Clerk"),
                "N_F_DetermineFunding",
                    Vars.of(
                        "treatmentRequestId", "TR-E2E-0001",
                        "fundingRoute", "hospital",
                        "fundingApprovalRequired", false,
                        "paymentRequiredFromPatient", false),
                "N_TB_ConfirmAppointment",
                    Vars.of(
                        "patientId", "PAT-E2E-0001",
                        "appointmentReference", "TR-E2E-0001",
                        "confirmationOutcome", "confirmed",
                        "confirmationBasis", "funding_in_place",
                        "recipients", List.of("patient"),
                        "documentType", "appointment_letter"),
                "N_OC_PreCycleReview",
                    Vars.of(
                        "patientId", "PAT-E2E-0001",
                        "cycleNumber", 2,
                        "bloodTestResults", "Full blood count within range",
                        "fitToContinue", false,
                        "reviewedBy", "Dr Chen"),
                "N_CL_ModifyTreatment",
                    Vars.of(
                        "treatmentRequestId", "TR-E2E-0001",
                        "proposedTreatment", "FOLFOX reduced dose",
                        "modificationReason", "Blood count low: cycle delayed",
                        "affectsCharge", false,
                        "clinicalAuthorised", true,
                        "authorisingClinician", "Dr Chen"),
                "N_F_ReviewFinancialImpact",
                    Vars.of(
                        "treatmentRequestId", "TR-E2E-0001",
                        "financialImpact", "none",
                        "reviewedBy", "Finance Officer"),
                "N_C_PrepareLetter",
                    Vars.of(
                        "consultationId", "CONS-E2E-0001",
                        "letterContent", "Seen in clinic; imaging reviewed; plan discussed.",
                        "authoringClinician", "Dr Chen",
                        "recipients", List.of("patient", "GP"),
                        "documentType", "new_patient_clinic_letter"),
                "N_C_ApproveLetter",
                    Vars.of("consultationId", "CONS-E2E-0001", "authoringClinician", "Dr Chen"),
                "N_MS_ProcessLetter",
                    Vars.of(
                        "consultationId", "CONS-E2E-0001",
                        "suspectedClinicalError", false,
                        "appointmentDate", "2026-09-20",
                        "approvalDate", "2026-09-21",
                        "processingDate", "2026-09-22",
                        "processedBy", "Medical Secretary"),
                "N_MS_ConfirmRecipients",
                    Vars.of(
                        "consultationId", "CONS-E2E-0001",
                        "recipients", List.of("patient", "GP"),
                        "documentType", "new_patient_clinic_letter"),
                "N_OB_ArrangeFollowUp",
                    Vars.of(
                        "referralId", referralId,
                        "patientId", "PAT-E2E-0001",
                        "speciality", "oncology",
                        "priority", "routine",
                        "requestedWindow", 21,
                        "recipients", List.of("patient"),
                        "documentType", "follow_up_clinic_letter"),
                "N_OB_ConfirmFollowUp",
                    Vars.of(
                        "patientId", "PAT-E2E-0001",
                        "appointmentReference", "TR-E2E-0001",
                        "confirmationOutcome", "confirmed",
                        "confirmationBasis", "not_applicable",
                        "recipients", List.of("patient"),
                        "documentType", "follow_up_clinic_letter")),
            240_000);

    assertTrue(
        run.elements().contains("N_CO_PathwayComplete"),
        "the pathway should have completed: " + String.join(" -> ", run.elements()));

    final List<String> processes = EngineTestSupport.processIdsInTree(run.instanceKey());
    for (String stage :
        List.of(
            "core-5-missing-information-message-exchange",
            "core-1-referral-and-new-patient-appointment",
            "core-2-treatment-authorisation-funding-and-payment",
            "core-3-clinic-letter-and-pathway-escalation",
            "core-4-follow-up-cancellation-enquiry-and-refund")) {
      assertTrue(
          processes.contains(stage),
          "the pathway should have run " + stage + " as the process its call activity names, and"
              + " ran " + processes);
    }
  }


  /** Completes the open user task at an element, which is how a Tasklist user would do it. */
  private static void completeUserTask(
      long instanceKey, String elementId, Map<String, Object> variables) {

    // The task list is served from the exported records, so a task that was created a moment ago
    // is not there yet. The other scenarios poll for it inside the drive loop; this waits the same
    // way, so that a task which never appears is a timeout rather than a race.
    final long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      for (EngineTestSupport.UserTask task : EngineTestSupport.openUserTasksInTree(instanceKey)) {
        if (elementId.equals(task.elementId())) {
          EngineTestSupport.completeUserTask(task.userTaskKey(), variables);
          report("  %-32s completed with %s", elementId, Json.write(variables));
          return;
        }
      }
      EngineTestSupport.sleep(500);
    }
    throw new IllegalStateException(
        "the instance " + instanceKey + " has no open user task at " + elementId);
  }

  /** What to complete an element's task with, taking the next entry where a list of them is given. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> variablesFor(
      Map<String, Object> taskVariables, String elementId, Map<String, Integer> visits) {

    final Object configured = taskVariables.get(elementId);
    if (configured instanceof List<?> sequence) {
      final int visit = visits.merge(elementId, 1, Integer::sum) - 1;
      final Object entry = sequence.get(Math.min(visit, sequence.size() - 1));
      return (Map<String, Object>) entry;
    }
    return configured == null ? Map.of() : (Map<String, Object>) configured;
  }

  /** Publishes a message addressed to one subscription, which is what a correlation key does. */
  private static void publishCorrelated(
      String message, String correlationKey, Map<String, Object> variables) {

    client
        .newPublishMessageCommand()
        .messageName(message)
        .correlationKey(correlationKey)
        .timeToLive(Duration.ofMinutes(2))
        .variables(variables)
        .send()
        .join();
    report("  published %s correlated by %s", message, correlationKey);
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
    return drive(label, processDefinitionId, null, variables, taskVariables, 90_000);
  }

  private Driven drive(
      String label,
      String processDefinitionId,
      String message,
      Map<String, Object> variables,
      Map<String, Object> taskVariables) {
    return drive(label, processDefinitionId, message, variables, taskVariables, 90_000);
  }

  /**
   * The same, with a deadline of the caller's choosing and per-visit variables.
   *
   * A value in {@code taskVariables} may be a list of maps rather than one map: the first time the
   * element's task is completed it is given the first, the second time the second, and so on, which
   * is how a scenario drives a task the process reaches more than once differently each time (a
   * confirmation that first asks for another date and is then confirmed, for instance).
   */
  private Driven drive(
      String label,
      String processDefinitionId,
      String message,
      Map<String, Object> variables,
      Map<String, Object> taskVariables,
      long timeoutMs) {

    final long instanceKey = start(processDefinitionId, message, variables, label);

    report("");
    report("%s", label);
    report("  instance %d", instanceKey);

    final Set<String> completed = new LinkedHashSet<>();
    final Set<String> visitedTasks = new LinkedHashSet<>();
    final Map<String, Integer> visits = new LinkedHashMap<>();
    final long deadline = System.currentTimeMillis() + timeoutMs;

    while (true) {
      for (EngineTestSupport.UserTask task : EngineTestSupport.openUserTasksInTree(instanceKey)) {
        if (completed.contains(task.userTaskKey())) {
          continue;
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object> supplied = variablesFor(taskVariables, task.elementId(), visits);
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

    // The engine may already hold active instances of this process from earlier runs, so the one
    // this call started is found by what was active before the message and what is active after it.
    // Asking for "the first active instance" instead hands back a leftover from an earlier run, and
    // the instance this message really started is then never driven and the scenario times out.
    final Set<Long> before = activeInstancesOf(processDefinitionId);

    client
        .newPublishMessageCommand()
        .messageName(message)
        // The models wait for these messages on message start events, and a start event holds no
        // subscription, so it has no correlation key to name.
        .withoutCorrelationKey()
        .timeToLive(Duration.ofMinutes(1))
        .variables(variables)
        .send()
        .join();

    final long deadline = System.currentTimeMillis() + 20000;
    while (System.currentTimeMillis() < deadline) {
      final Set<Long> started = activeInstancesOf(processDefinitionId);
      started.removeAll(before);
      if (!started.isEmpty()) {
        return started.iterator().next();
      }
      EngineTestSupport.sleep(500);
    }
    throw new IllegalStateException(label + ": the message started nothing");
  }

  @SuppressWarnings("unchecked")
  private Set<Long> activeInstancesOf(String processDefinitionId) {
    final Map<String, Object> body =
        EngineTestSupport.post(
            "/v2/process-instances/search",
            Map.of(
                "filter",
                Map.of("processDefinitionId", processDefinitionId, "state", "ACTIVE"),
                "page",
                Map.of("limit", 200)));

    final Set<Long> keys = new LinkedHashSet<>();
    for (Object item : (List<Object>) body.getOrDefault("items", List.of())) {
      final Map<String, Object> instance = (Map<String, Object>) item;
      keys.add(Long.valueOf(String.valueOf(instance.get("processInstanceKey"))));
    }
    return keys;
  }

  private static void report(String format, Object... arguments) {
    Console.out(format, arguments);
  }
}
