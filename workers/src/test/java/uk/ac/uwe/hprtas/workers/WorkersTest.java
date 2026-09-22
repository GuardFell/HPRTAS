package uk.ac.uwe.hprtas.workers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import uk.ac.uwe.hprtas.workers.TestSupport.CountingPaymentProvider;
import uk.ac.uwe.hprtas.workers.workers.AppointmentAvailability;
import uk.ac.uwe.hprtas.workers.workers.CorrespondenceDispatch;
import uk.ac.uwe.hprtas.workers.workers.PaymentProcessing;
import uk.ac.uwe.hprtas.workers.workers.ReferralValidation;
import uk.ac.uwe.hprtas.workers.workers.RefundProcessing;
import uk.ac.uwe.hprtas.workers.workers.TreatmentAvailability;

/**
 * Tests for the external workers.
 *
 * Each test states the scenario it belongs to in {@code tests/test-plan.md} where one exists, so the
 * evidence for a test case can point at a named test. Run with {@code mvn test}. No engine is
 * required.
 */
class WorkersTest {

  private static final Config CONFIG = TestSupport.buildConfig();

  private static final ReferralValidation REFERRAL_VALIDATION = new ReferralValidation();
  private static final AppointmentAvailability APPOINTMENT_AVAILABILITY =
      new AppointmentAvailability();
  private static final TreatmentAvailability TREATMENT_AVAILABILITY = new TreatmentAvailability();
  private static final PaymentProcessing PAYMENT_PROCESSING = new PaymentProcessing();
  private static final CorrespondenceDispatch CORRESPONDENCE_DISPATCH = new CorrespondenceDispatch();
  private static final RefundProcessing REFUND_PROCESSING = new RefundProcessing();

  /** Runs a handler and returns the outcome. */
  private static Outcome run(WorkerModule worker, Map<String, Object> variables) {
    return worker.handle(variables, TestSupport.buildContext(CONFIG));
  }

  /** Asserts that an outcome carried a business error with the expected code. */
  private static void assertBusinessError(Outcome outcome, ErrorCode expected) {
    assertIsBusinessError(outcome);
    assertEquals(expected.name(), ((Outcome.BusinessError) outcome).errorCode());
  }

  /** Asserts that a controlled business error is what came back, whatever its code. */
  private static void assertIsBusinessError(Outcome outcome) {
    assertEquals(
        "businessError",
        outcome.kind(),
        "expected a controlled business error, but the worker returned " + outcome.kind());
  }

  /** Asserts that an outcome completed, and returns its variables for further assertions. */
  private static Map<String, Object> completedVariables(Outcome outcome) {
    assertEquals("completed", outcome.kind());
    return ((Outcome.Completed) outcome).variables();
  }

  // ---------------------------------------------------------------------------
  // referral-validation
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("referral-validation: a complete referral is accepted for clinical review (TC-01)")
  void completeReferralIsAccepted() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                REFERRAL_VALIDATION,
                Vars.of(
                    "documentsComplete", true,
                    "referringOrganisation", "St Mary GP Surgery")));

    assertEquals("complete", variables.get("validationResult"));
    assertEquals(List.of(), variables.get("requestedItems"));
  }

  @Test
  @DisplayName("referral-validation: missing information is requested from the referring organisation (TC-03)")
  void missingInformationIsRequested() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                REFERRAL_VALIDATION,
                Vars.of(
                    "documentsComplete", false,
                    "missingItems", "previous clinic letter, diagnostic report",
                    "referringOrganisation", "Northside Hospital")));

    assertEquals("incomplete", variables.get("validationResult"));
    assertEquals(
        List.of("previous clinic letter", "diagnostic report"), variables.get("requestedItems"));
    assertEquals("Northside Hospital", variables.get("missingInformationRequestedFrom"));
  }

  @Test
  @DisplayName("referral-validation: an incomplete referral that names no missing items is a business error")
  void incompleteWithoutItemsIsABusinessError() {
    final Outcome outcome =
        run(
            REFERRAL_VALIDATION,
            Vars.of("documentsComplete", false, "referringOrganisation", "Northside Hospital"));

    assertBusinessError(outcome, ErrorCode.MISSING_INFORMATION_NOT_SPECIFIED);
  }

  @Test
  @DisplayName("referral-validation: unusable input produces a controlled error, not a crash (TC-11)")
  void unusableReferralInputIsControlled() {
    assertBusinessError(run(REFERRAL_VALIDATION, Vars.of()), ErrorCode.INVALID_VARIABLE);

    assertBusinessError(
        run(
            REFERRAL_VALIDATION,
            Vars.of(
                "documentsComplete", true,
                "missingItems", List.of("diagnostic report"),
                "referringOrganisation", "Northside Hospital")),
        ErrorCode.INVALID_VARIABLE);

    assertBusinessError(
        run(
            REFERRAL_VALIDATION,
            Vars.of("documentsComplete", true, "referringOrganisation", "   ")),
        ErrorCode.INVALID_VARIABLE);
  }

  @Test
  @DisplayName("referral-validation: the check is administrative and returns no clinical judgement (BR-01)")
  void theCheckIsAdministrative() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                REFERRAL_VALIDATION,
                Vars.of("documentsComplete", true, "referringOrganisation", "St Mary GP Surgery")));

    final List<String> clinicalWords =
        List.of("clinical", "suitab", "accept", "reject", "diagnos", "triage");
    for (String written : variables.keySet()) {
      for (String word : clinicalWords) {
        assertFalse(
            written.toLowerCase().contains(word),
            "the worker must not write a variable that looks like a clinical decision, found \""
                + word
                + "\"");
      }
    }
  }

  // ---------------------------------------------------------------------------
  // appointment-availability
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("appointment-availability: a slot inside the requested window is offered (TC-01)")
  void slotInsideTheWindowIsOffered() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                APPOINTMENT_AVAILABILITY,
                Vars.of("speciality", "Oncology", "priority", "routine", "requestedWindow", 14)));

    assertEquals(true, variables.get("slotAvailable"));
    assertEquals("2026-09-26", variables.get("appointmentDate"));
    assertEquals(true, variables.get("withinRequestedWindow"));
  }

  @Test
  @DisplayName("appointment-availability: an appointment inside two weeks is flagged for telephone contact (TC-04, BR-03)")
  void appointmentInsideTwoWeeksIsFlagged() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                APPOINTMENT_AVAILABILITY,
                Vars.of("speciality", "Oncology", "priority", "urgent", "requestedWindow", 14)));

    assertEquals(true, variables.get("appointmentWithinTwoWeeks"));
  }

  @Test
  @DisplayName("appointment-availability: no slot inside the period is offered as an alternative and not booked (TC-05, BR-17)")
  void noSlotIsOfferedAsAnAlternative() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                APPOINTMENT_AVAILABILITY,
                Vars.of(
                    "speciality", "Oncology",
                    "priority", "routine",
                    "requestedWindow", 14,
                    "schedulingOutcome", "none")));

    assertEquals(false, variables.get("slotAvailable"));
    assertNull(variables.get("appointmentDate"));
    assertEquals("2026-10-21", variables.get("alternativeDate"));
    assertEquals(false, variables.get("withinRequestedWindow"));
  }

  @Test
  @DisplayName("appointment-availability: a slot outside the requested period is not silently accepted (TC-18, EX-06)")
  void aSlotOutsideTheWindowIsNotAccepted() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                APPOINTMENT_AVAILABILITY,
                Vars.of(
                    "speciality", "Oncology",
                    "priority", "routine",
                    "requestedWindow", 14,
                    "schedulingOutcome", "outside_window")));

    assertEquals(false, variables.get("slotAvailable"));
    assertEquals("outside_window", variables.get("schedulingServiceOutcome"));
  }

  @Test
  @DisplayName("appointment-availability: asking twice for the same instance returns the same appointment")
  void askingTwiceReturnsTheSameAppointment() {
    final WorkerContext context = TestSupport.buildContext(CONFIG);
    final Map<String, Object> variables =
        Vars.of("speciality", "Oncology", "priority", "routine", "requestedWindow", 14);

    final Map<String, Object> first =
        completedVariables(APPOINTMENT_AVAILABILITY.handle(variables, context));
    final Map<String, Object> second =
        completedVariables(APPOINTMENT_AVAILABILITY.handle(variables, context));

    assertEquals(first.get("appointmentDate"), second.get("appointmentDate"));
  }

  @Test
  @DisplayName("appointment-availability: an unusable booking request produces a controlled error (TC-11)")
  void unusableBookingRequestIsControlled() {
    assertBusinessError(
        run(
            APPOINTMENT_AVAILABILITY,
            Vars.of("speciality", "Oncology", "priority", "routine", "requestedWindow", 0)),
        ErrorCode.INVALID_VARIABLE);

    assertBusinessError(
        run(
            APPOINTMENT_AVAILABILITY,
            Vars.of("speciality", "Oncology", "priority", "soon", "requestedWindow", 14)),
        ErrorCode.INVALID_VARIABLE);
  }

  // ---------------------------------------------------------------------------
  // treatment-availability
  // ---------------------------------------------------------------------------

  private static final Map<String, Object> AUTHORISED_REQUEST =
      Vars.of(
          "proposedTreatment", "FOLFOX cycle 1",
          "treatmentStartDate", "2026-10-01",
          "specialResources", "pharmacy, day unit",
          "clinicalAuthorised", true);

  @Test
  @DisplayName("treatment-availability: an authorised request is booked (TC-07)")
  void anAuthorisedRequestIsBooked() {
    final Map<String, Object> variables =
        completedVariables(run(TREATMENT_AVAILABILITY, AUTHORISED_REQUEST));

    assertEquals("confirmed", variables.get("bookingStatus"));
    assertTrue(
        String.valueOf(variables.get("appointmentReference")).startsWith("TRT-"),
        "the treatment appointment should be referenced");
    assertEquals(true, variables.get("externalServiceAvailable"));
  }

  @Test
  @DisplayName("treatment-availability: a request without clinical authorisation is not processed (TC-06, BR-04, AC-02)")
  void anUnauthorisedRequestIsNotProcessed() {
    final Outcome outcome =
        run(TREATMENT_AVAILABILITY, with(AUTHORISED_REQUEST, "clinicalAuthorised", false));

    assertBusinessError(outcome, ErrorCode.UNAUTHORISED_BOOKING_REQUEST);
    assertEquals(
        "not_processed", ((Outcome.BusinessError) outcome).variables().get("bookingStatus"));
  }

  @Test
  @DisplayName("treatment-availability: an unavailable service leaves the booking pending and records the attempt (TC-12, EX-07)")
  void anUnavailableServiceLeavesTheBookingPending() {
    final WorkerContext context = TestSupport.buildContext(CONFIG);
    final Map<String, Object> variables = with(AUTHORISED_REQUEST, "treatmentOutcome", "unavailable");

    final Map<String, Object> first =
        completedVariables(TREATMENT_AVAILABILITY.handle(variables, context));
    final Map<String, Object> second =
        completedVariables(TREATMENT_AVAILABILITY.handle(variables, context));

    assertEquals(false, first.get("externalServiceAvailable"));
    assertEquals("pending", first.get("bookingStatus"));
    assertNull(first.get("appointmentReference"));
    assertEquals(1, first.get("treatmentRetryCount"));
    assertEquals(2, second.get("treatmentRetryCount"), "each further attempt is recorded");
  }

  @Test
  @DisplayName("treatment-availability: retrying after an outage creates no second appointment (AC-10)")
  void retryingAfterAnOutageCreatesNoSecondAppointment() {
    final WorkerContext context = TestSupport.buildContext(CONFIG);

    TREATMENT_AVAILABILITY.handle(
        with(AUTHORISED_REQUEST, "treatmentOutcome", "unavailable"), context);
    final Map<String, Object> booked =
        completedVariables(
            TREATMENT_AVAILABILITY.handle(
                with(AUTHORISED_REQUEST, "treatmentOutcome", "available"), context));
    final Map<String, Object> retried =
        completedVariables(
            TREATMENT_AVAILABILITY.handle(
                with(AUTHORISED_REQUEST, "treatmentOutcome", "available"), context));

    assertEquals(booked.get("appointmentReference"), retried.get("appointmentReference"));
    assertEquals(true, retried.get("duplicateAttempt"));
  }

  // ---------------------------------------------------------------------------
  // payment-processing
  // ---------------------------------------------------------------------------

  private static final Map<String, Object> PATIENT_PAYMENT =
      Vars.of("chargeAmount", 250, "fundingRoute", "patient", "paymentReference", "PAY-2026-0001");

  @Test
  @DisplayName("payment-processing: a completed payment returns the reference, date and amount (TC-07, FR-020)")
  void aCompletedPaymentIsRecorded() {
    final Map<String, Object> variables = completedVariables(run(PAYMENT_PROCESSING, PATIENT_PAYMENT));

    assertEquals("completed", variables.get("paymentStatus"));
    assertEquals(250, variables.get("paidAmount"));
    assertEquals(true, variables.get("confirmationReceived"));
    assertEquals(false, variables.get("requiresInvestigation"));
    assertEquals("2026-09-16", variables.get("paymentDate"));
  }

  @Test
  @DisplayName("payment-processing: a declined payment is reported so the patient and team can be notified (TC-08, EX-08)")
  void aDeclinedPaymentIsReported() {
    final Map<String, Object> variables =
        completedVariables(
            run(PAYMENT_PROCESSING, with(PATIENT_PAYMENT, "paymentOutcome", "declined")));

    assertEquals("declined", variables.get("paymentStatus"));
    assertNull(variables.get("paidAmount"));
    assertEquals(false, variables.get("requiresInvestigation"));
  }

  @Test
  @DisplayName("payment-processing: a payment taken without a confirmation is marked for investigation (TC-09, BR-07, AC-04)")
  void aPaymentWithoutConfirmationIsInvestigated() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                PAYMENT_PROCESSING,
                with(PATIENT_PAYMENT, "paymentOutcome", "success_no_confirmation")));

    assertEquals("completed", variables.get("paymentStatus"));
    assertEquals(false, variables.get("confirmationReceived"));
    assertEquals(true, variables.get("requiresInvestigation"));
  }

  @Test
  @DisplayName("payment-processing: the same reference is never charged twice (FR-023, AC-10)")
  void theSameReferenceIsNeverChargedTwice() {
    final WorkerContext context = TestSupport.buildContext(CONFIG);

    final Map<String, Object> first =
        completedVariables(PAYMENT_PROCESSING.handle(PATIENT_PAYMENT, context));
    final Map<String, Object> second =
        completedVariables(PAYMENT_PROCESSING.handle(PATIENT_PAYMENT, context));

    assertEquals("duplicate", second.get("paymentStatus"));
    assertEquals(true, second.get("duplicateAttempt"));
    assertEquals(
        first.get("transactionReference"),
        second.get("transactionReference"),
        "the original transaction is returned rather than a new one");
    assertEquals(first.get("paidAmount"), second.get("paidAmount"));
  }

  @Test
  @DisplayName("payment-processing: a declined attempt may be retried without a double charge (EX-08)")
  void aDeclinedAttemptMayBeRetried() {
    final WorkerContext context = TestSupport.buildContext(CONFIG);

    final Map<String, Object> declined =
        completedVariables(
            PAYMENT_PROCESSING.handle(
                with(PATIENT_PAYMENT, "paymentOutcome", "declined"), context));
    final Map<String, Object> retried =
        completedVariables(
            PAYMENT_PROCESSING.handle(
                with(PATIENT_PAYMENT, "paymentOutcome", "completed"), context));

    assertEquals("declined", declined.get("paymentStatus"));
    assertEquals("completed", retried.get("paymentStatus"));
    assertNotEquals(
        declined.get("transactionReference"), retried.get("transactionReference"));
  }

  @Test
  @DisplayName("payment-processing: card details are refused and never passed on (BR-06, NFR-007, AC-09)")
  void cardDetailsAreRefused() {
    final CountingPaymentProvider provider =
        new CountingPaymentProvider(CONFIG.simulatedServices().payment());

    final Outcome outcome =
        PAYMENT_PROCESSING.handle(
            with(PATIENT_PAYMENT, "cardNumber", "4111111111111111", "cvv", "123"),
            TestSupport.buildContext(CONFIG, provider));

    assertBusinessError(outcome, ErrorCode.PROHIBITED_FINANCIAL_DATA);
    assertFalse(provider.wasAskedToTakePayment(), "the provider must not be called with card details");
    assertEquals(
        List.of("cardNumber", "cvv"),
        ((Outcome.BusinessError) outcome).variables().get("prohibitedFields"));
  }

  @Test
  @DisplayName("payment-processing: a funded patient is not asked to pay")
  void aFundedPatientIsNotAskedToPay() {
    final CountingPaymentProvider provider =
        new CountingPaymentProvider(CONFIG.simulatedServices().payment());
    final WorkerContext context = TestSupport.buildContext(CONFIG, provider);

    for (String fundingRoute : List.of("hospital", "insurer", "exempt")) {
      final Map<String, Object> variables =
          completedVariables(
              PAYMENT_PROCESSING.handle(
                  with(PATIENT_PAYMENT, "fundingRoute", fundingRoute), context));

      assertEquals("not_required", variables.get("paymentStatus"));
      assertNull(variables.get("transactionReference"));
    }
    assertFalse(
        provider.wasAskedToTakePayment(), "the provider must not be called when no payment is due");
  }

  @Test
  @DisplayName("payment-processing: an unusable payment request produces a controlled error (TC-11)")
  void unusablePaymentRequestIsControlled() {
    final List<Map<String, Object>> unusable =
        List.of(
            with(PATIENT_PAYMENT, "chargeAmount", "two hundred and fifty"),
            with(PATIENT_PAYMENT, "chargeAmount", -5),
            with(PATIENT_PAYMENT, "fundingRoute", "charity"),
            with(PATIENT_PAYMENT, "paymentReference", ""),
            Vars.of("chargeAmount", 250, "fundingRoute", "patient"));

    for (Map<String, Object> variables : unusable) {
      assertBusinessError(run(PAYMENT_PROCESSING, variables), ErrorCode.INVALID_VARIABLE);
    }
  }

  // ---------------------------------------------------------------------------
  // correspondence-dispatch
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("correspondence-dispatch: a letter is dispatched and the date, channel and reference are recorded (FR-037)")
  void aLetterIsDispatched() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                CORRESPONDENCE_DISPATCH,
                Vars.of(
                    "recipients", "patient, GP", "documentType", "new_patient_clinic_letter")));

    assertEquals("2026-09-16", variables.get("dispatchDate"));
    assertEquals("post", variables.get("dispatchChannel"), "no preference defaults to post (AS-12)");
    assertEquals(List.of("patient", "GP"), variables.get("recipients"));
    assertNotNull(variables.get("dispatchReference"));
  }

  @Test
  @DisplayName("correspondence-dispatch: a recorded channel preference is used (NFR-012)")
  void aRecordedChannelPreferenceIsUsed() {
    final Map<String, Object> variables =
        completedVariables(
            run(
                CORRESPONDENCE_DISPATCH,
                Vars.of(
                    "recipients", "patient",
                    "documentType", "appointment_letter",
                    "channelPreference", "accessible_format")));

    assertEquals("accessible_format", variables.get("dispatchChannel"));
  }

  @Test
  @DisplayName("correspondence-dispatch: an unusable dispatch request produces a controlled error (TC-11)")
  void unusableDispatchRequestIsControlled() {
    final List<Map<String, Object>> unusable =
        List.of(
            Vars.of("recipients", "", "documentType", "appointment_letter"),
            Vars.of("recipients", "patient", "documentType", "a letter of some kind"),
            Vars.of(
                "recipients", "patient",
                "documentType", "appointment_letter",
                "channelPreference", "sms"));

    for (Map<String, Object> variables : unusable) {
      assertBusinessError(run(CORRESPONDENCE_DISPATCH, variables), ErrorCode.INVALID_VARIABLE);
    }
  }

  // ---------------------------------------------------------------------------
  // refund-processing
  // ---------------------------------------------------------------------------

  /** The payment the refund tests are made against, settled first so there is money to give back. */
  private static final Map<String, Object> SETTLED_PAYMENT = PATIENT_PAYMENT;

  private static final Map<String, Object> REFUND_REQUEST =
      Vars.of(
          "paymentReference", "PAY-2026-0001",
          "refundDecision", "full",
          "refundReason", "cancelled");

  /** Pays first, so the provider's ledger holds a transaction the refund can be made against. */
  private static WorkerContext settledContext() {
    final WorkerContext context = TestSupport.buildContext(CONFIG);
    final Map<String, Object> paid = completedVariables(PAYMENT_PROCESSING.handle(SETTLED_PAYMENT, context));
    assertEquals("completed", paid.get("paymentStatus"), "the fixture payment must settle");
    return context;
  }

  @Test
  @DisplayName("refund-processing: a full refund returns the amount the provider actually took (BR-20, FR-033)")
  void aFullRefundReturnsWhatWasTaken() {
    final Map<String, Object> variables =
        completedVariables(REFUND_PROCESSING.handle(REFUND_REQUEST, settledContext()));

    assertEquals("full", variables.get("refundStatus"));
    assertEquals(250, variables.get("refundedAmount"));
    assertTrue(
        String.valueOf(variables.get("refundReference")).startsWith("PSP-RF-"),
        "the refund should be referenced");
  }

  @Test
  @DisplayName("refund-processing: a partial refund returns only the amount the Finance Team decided")
  void aPartialRefundReturnsTheDecidedAmount() {
    final Map<String, Object> variables =
        completedVariables(
            REFUND_PROCESSING.handle(
                with(REFUND_REQUEST, "refundDecision", "partial", "refundAmount", 100,
                    "refundReason", "rescheduled"),
                settledContext()));

    assertEquals("partial", variables.get("refundStatus"));
    assertEquals(100, variables.get("refundedAmount"));
  }

  @Test
  @DisplayName("refund-processing: the same payment is never refunded twice (FR-023, AC-10)")
  void theSamePaymentIsNeverRefundedTwice() {
    final WorkerContext context = settledContext();

    final Map<String, Object> first =
        completedVariables(REFUND_PROCESSING.handle(REFUND_REQUEST, context));
    final Map<String, Object> second =
        completedVariables(REFUND_PROCESSING.handle(REFUND_REQUEST, context));

    assertEquals("duplicate", second.get("refundStatus"));
    assertEquals(true, second.get("duplicateRefundAttempt"));
    assertEquals(
        first.get("refundReference"),
        second.get("refundReference"),
        "the original refund is returned rather than a second payout");
    assertEquals(first.get("refundedAmount"), second.get("refundedAmount"));
  }

  @Test
  @DisplayName("refund-processing: a payment that was never settled cannot be refunded (BR-20)")
  void aPaymentThatWasNeverSettledCannotBeRefunded() {
    // No payment was made for this reference, so refunding it would create money.
    final Outcome outcome =
        REFUND_PROCESSING.handle(REFUND_REQUEST, TestSupport.buildContext(CONFIG));

    assertBusinessError(outcome, ErrorCode.INVALID_VARIABLE);
    assertTrue(((Outcome.BusinessError) outcome).errorMessage().contains("nothing to refund"));
  }

  @Test
  @DisplayName("refund-processing: a refund larger than the amount paid is refused")
  void aRefundLargerThanWhatWasPaidIsRefused() {
    final WorkerContext context = settledContext();

    assertBusinessError(
        REFUND_PROCESSING.handle(
            with(REFUND_REQUEST, "refundDecision", "partial", "refundAmount", 251), context),
        ErrorCode.INVALID_VARIABLE);
  }

  @Test
  @DisplayName("refund-processing: a partial refund that names no amount is refused")
  void aPartialRefundWithNoAmountIsRefused() {
    final Outcome outcome =
        REFUND_PROCESSING.handle(
            with(REFUND_REQUEST, "refundDecision", "partial"), settledContext());

    assertBusinessError(outcome, ErrorCode.INVALID_VARIABLE);
    assertTrue(
        ((Outcome.BusinessError) outcome)
            .errorMessage()
            .contains("how much is to be refunded"));
  }

  @Test
  @DisplayName("refund-processing: card details are refused and never passed on (BR-06, NFR-007, AC-09)")
  void cardDetailsAreRefusedOnARefund() {
    final CountingPaymentProvider provider =
        new CountingPaymentProvider(CONFIG.simulatedServices().payment());
    final WorkerContext context = TestSupport.buildContext(CONFIG, provider);
    completedVariables(PAYMENT_PROCESSING.handle(SETTLED_PAYMENT, context));

    final Outcome outcome =
        REFUND_PROCESSING.handle(
            with(REFUND_REQUEST, "cardNumber", "4111111111111111", "cvv", "123"), context);

    assertBusinessError(outcome, ErrorCode.PROHIBITED_FINANCIAL_DATA);
    assertFalse(provider.wasAskedToRefund(), "the provider must not be called with card details");
    assertEquals(
        List.of("cardNumber", "cvv"),
        ((Outcome.BusinessError) outcome).variables().get("prohibitedFields"));
  }

  @Test
  @DisplayName("refund-processing: an unusable refund request produces a controlled error (TC-11)")
  void unusableRefundRequestIsControlled() {
    final WorkerContext context = settledContext();

    final List<Map<String, Object>> unusable =
        List.of(
            with(REFUND_REQUEST, "paymentReference", ""),
            with(REFUND_REQUEST, "refundReason", "because"),
            with(REFUND_REQUEST, "refundDecision", "none"),
            with(REFUND_REQUEST, "refundAmount", "a hundred"));

    for (Map<String, Object> variables : unusable) {
      assertBusinessError(
          REFUND_PROCESSING.handle(variables, context), ErrorCode.INVALID_VARIABLE);
    }
  }

  // ---------------------------------------------------------------------------
  // The job contract itself
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("the worker turns a completed outcome into a job completion")
  void aCompletedOutcomeCompletesTheJob() {
    final TestSupport.RecordingActions actions = new TestSupport.RecordingActions();

    Main.handle(
        REFERRAL_VALIDATION,
        CONFIG,
        TestSupport.silentLogger(),
        TestSupport.buildContext(CONFIG).services(),
        actions,
        TestSupport.buildJob(
            Vars.of("documentsComplete", true, "referringOrganisation", "St Mary GP Surgery")));

    assertEquals("complete", actions.kind);
    assertEquals("complete", actions.variables.get("validationResult"));
  }

  @Test
  @DisplayName("the worker turns a broken business rule into a BPMN error the model can catch")
  void aBrokenRuleBecomesABpmnError() {
    final TestSupport.RecordingActions actions = new TestSupport.RecordingActions();

    Main.handle(
        TREATMENT_AVAILABILITY,
        CONFIG,
        TestSupport.silentLogger(),
        TestSupport.buildContext(CONFIG).services(),
        actions,
        TestSupport.buildJob(
            Vars.of(
                "proposedTreatment", "FOLFOX cycle 1",
                "treatmentStartDate", "2026-10-01",
                "clinicalAuthorised", false)));

    assertEquals("error", actions.kind);
    assertEquals("UNAUTHORISED_BOOKING_REQUEST", actions.errorCode);
    assertEquals("not_processed", actions.variables.get("bookingStatus"));
  }

  @Test
  @DisplayName("the worker fails the job when the handler throws, so the broker retries")
  void aThrowingHandlerFailsTheJob() {
    final WorkerModule broken =
        new WorkerModule() {
          @Override
          public String name() {
            return "referral-validation";
          }

          @Override
          public String taskType() {
            return "validate-referral";
          }

          @Override
          public Outcome handle(Map<String, Object> variables, WorkerContext context) {
            throw new IllegalStateException("the simulated service is unreachable");
          }
        };

    final TestSupport.RecordingActions actions = new TestSupport.RecordingActions();

    Main.handle(
        broken,
        CONFIG,
        TestSupport.silentLogger(),
        TestSupport.buildContext(CONFIG).services(),
        actions,
        TestSupport.buildJob(Vars.of()));

    assertEquals("fail", actions.kind);
    assertTrue(actions.errorMessage.contains("the simulated service is unreachable"));
    assertNull(
        actions.retries,
        "the wrapper leaves the retry count to the client, which consumes one of the model's own");
  }

  @Test
  @DisplayName("the worker fails the job when a handler returns nothing at all")
  void aHandlerReturningNothingFailsTheJob() {
    final WorkerModule broken =
        new WorkerModule() {
          @Override
          public String name() {
            return "referral-validation";
          }

          @Override
          public String taskType() {
            return "validate-referral";
          }

          @Override
          public Outcome handle(Map<String, Object> variables, WorkerContext context) {
            return null;
          }
        };

    final TestSupport.RecordingActions actions = new TestSupport.RecordingActions();

    Main.handle(
        broken,
        CONFIG,
        TestSupport.silentLogger(),
        TestSupport.buildContext(CONFIG).services(),
        actions,
        TestSupport.buildJob(Vars.of()));

    assertEquals("fail", actions.kind);
  }

  // ---------------------------------------------------------------------------

  /** A copy of a variables map with some entries replaced or added. */
  private static Map<String, Object> with(Map<String, Object> base, Object... keysAndValues) {
    final Map<String, Object> copy = new LinkedHashMap<>(base);
    for (int i = 0; i < keysAndValues.length; i += 2) {
      copy.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return copy;
  }
}
