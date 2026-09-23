package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.services.PaymentServiceProvider;

import java.util.List;
import java.util.Map;

/**
 * Worker: refund-processing   (job type {@code process-refund})
 *
 * Sends an approved refund to the external Payment Service Provider, for the Finance Team's decision
 * on a paid appointment that has been cancelled, rescheduled or otherwise changed (BR-20, BR-21,
 * FR-033, FR-034).
 *
 * The refund is not a payment with the sign reversed, which is why it has its own job type rather
 * than reusing {@code process-payment}:
 *
 * <ul>
 *   <li>the money moves out, so the provider must first confirm that this payment reference was
 *       actually settled. Refunding a reference that was never paid would create money, and is
 *       refused (BR-20).</li>
 *   <li>a refund may be full or partial, so the amount is checked against what was paid rather than
 *       against a charge the hospital is asking for.</li>
 *   <li>the same appointment must never be refunded twice, so a second refund for a settled
 *       reference returns the first one as a duplicate (AC-10).</li>
 *   <li>card and security details must not reach the hospital system any more on the way out than on
 *       the way in (BR-06, NFR-007, AC-09).</li>
 * </ul>
 *
 * An unusable request is reported as {@code INVALID_VARIABLE} and card or security details as
 * {@code PROHIBITED_FINANCIAL_DATA}. The refund task in
 * {@code core-4-follow-up-cancellation-enquiry-and-refund} catches both, so the Finance Team is
 * handed the request back to correct instead of the process stalling on an incident. The second
 * catch event is the one {@code DEF-12} was raised about: without it that refusal, and only that
 * one, became an incident.
 */
public final class RefundProcessing implements WorkerModule {

  /** The decisions the model routes here. {@code none} goes straight to the record, not to the provider. */
  public static final List<String> REFUND_DECISIONS = List.of("full", "partial");

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "paymentReference", FieldSpec.identifier().required(),
          "refundDecision",
              FieldSpec.string().required().oneOf(REFUND_DECISIONS.toArray(new String[0])),
          // Only meaningful for a partial refund; a full refund takes the amount the provider
          // actually settled, which is recorded in its ledger, not on the form.
          "refundAmount", FieldSpec.money().max(100000),
          "refundReason",
              FieldSpec.string()
                  .required()
                  .oneOf("cancelled", "rescheduled", "treatment_changed", "other"));

  @Override
  public String name() {
    return "refund-processing";
  }

  @Override
  public String taskType() {
    return "process-refund";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final List<String> prohibited = Validate.findProhibitedFinancialFields(variables);
    if (!prohibited.isEmpty()) {
      return Outcome.businessError(
          ErrorCode.PROHIBITED_FINANCIAL_DATA,
          "the refund cannot be processed because card or security details were supplied: "
              + String.join(", ", prohibited)
              + ". The hospital system must not hold them.",
          Vars.of("refundStatus", "not_processed", "prohibitedFields", List.copyOf(prohibited)));
    }

    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the refund cannot be processed because the request is incomplete or unusable: "
              + check.summarise(),
          Vars.of("refundStatus", "not_processed"));
    }

    final String refundDecision = (String) check.values().get("refundDecision");
    final String paymentReference = (String) check.values().get("paymentReference");
    final String refundReason = (String) check.values().get("refundReason");

    if ("partial".equals(refundDecision) && check.values().get("refundAmount") == null) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the refund cannot be processed because a partial refund must state how much is to be"
              + " refunded",
          Vars.of("refundStatus", "not_processed"));
    }

    final PaymentServiceProvider.RefundResult result =
        context
            .services()
            .payment()
            .refundPayment(
                paymentReference,
                // A full refund is refunded in full; the provider supplies the amount it actually
                // took rather than trusting a figure typed on the request.
                "full".equals(refundDecision)
                    ? null
                    : (Number) check.values().get("refundAmount"),
                refundReason,
                refundDecision,
                context.now());

    final String refusal = refusalFor(result.status(), result.paidAmount());
    if (refusal != null) {
      context
          .log()
          .warn(
              "refund refused by the provider",
              Vars.of(
                  "paymentReference", paymentReference,
                  "providerStatus", result.status(),
                  "paidAmount", result.paidAmount()));
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          refusal,
          Vars.of("refundStatus", "not_processed", "paidAmount", result.paidAmount()));
    }

    context
        .log()
        .info(
            "refund recorded by the provider",
            Vars.of(
                "paymentReference", paymentReference,
                "refundStatus", result.status(),
                "refundedAmount", result.refundedAmount(),
                "duplicateRefundAttempt", result.duplicateRefundAttempt()));

    return Outcome.completed(
        Vars.of(
            "refundStatus", "duplicate".equals(result.status()) ? "duplicate" : refundDecision,
            "refundReference", result.refundReference(),
            "refundDate", result.refundDate(),
            "refundedAmount", result.refundedAmount(),
            "refundReason", refundReason,
            "duplicateRefundAttempt", result.duplicateRefundAttempt()));
  }

  /** The reason a refund the provider refused cannot be paid out, or null when it was recorded. */
  private static String refusalFor(String status, Number paidAmount) {
    return switch (status) {
      case "no_settled_payment" ->
          "the provider has no settled payment for this reference, so there is nothing to refund";
      case "amount_too_high" ->
          "the refund is larger than the amount the provider took (" + paidAmount + ")";
      case "invalid_amount" -> "the refund amount is not a positive amount";
      default -> null;
    };
  }
}
