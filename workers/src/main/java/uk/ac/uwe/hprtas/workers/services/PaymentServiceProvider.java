package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulated external Payment Service Provider.
 *
 * The case requires a payment request to be sent to an external provider and only the status,
 * reference, date and amount to come back - never the card details (BR-06, NFR-007, AC-09). This
 * models that boundary and the outcomes the process has to handle:
 *
 * <ul>
 *   <li>{@code completed} - the payment was taken and confirmed (TC-07)</li>
 *   <li>{@code declined} - the payment was refused (EX-08, TC-08)</li>
 *   <li>{@code success_no_confirmation} - the payment was taken but the confirmation did not come
 *       back, so the transaction must be marked for investigation and not re-requested (BR-07,
 *       EX-09, TC-09, AC-04)</li>
 * </ul>
 *
 * A transaction ledger keyed by the payment reference is what stops the patient being charged twice
 * for the same appointment: once a reference has been paid, any further attempt returns the original
 * transaction marked as a duplicate and takes no more money (FR-023, EX-08, AC-10). A declined
 * attempt recorded nothing, so a retry after a decline is allowed.
 *
 * The same ledger answers refunds, which is what {@code refund-processing} calls. A refund is only
 * recorded against a payment the provider actually settled, it can never exceed what was paid, and a
 * second refund against the same payment reference returns the first one as a duplicate rather than
 * paying out twice (BR-20, FR-033, AC-10).
 *
 * Limitations are recorded in {@code workers/README.md}: no card processing, no clearing or
 * settlement, no 3-D Secure, no fraud checks; refunds are recorded rather than executed.
 */
public class PaymentServiceProvider extends SimulatedService {

  public static final String COMPLETED = "completed";
  public static final String DECLINED = "declined";
  public static final String SUCCESS_NO_CONFIRMATION = "success_no_confirmation";

  public static final List<String> OUTCOMES = List.of(COMPLETED, DECLINED, SUCCESS_NO_CONFIRMATION);
  public static final List<String> REFUND_OUTCOMES = List.of("refunded");

  private final Config.PaymentSettings config;

  /** A settled transaction as the provider holds it. */
  public record Transaction(
      String status,
      String transactionReference,
      String paymentDate,
      Number paidAmount,
      boolean confirmationReceived,
      boolean requiresInvestigation) {}

  /** A refund as the provider holds it. */
  public record Refund(
      String refundReference,
      String refundDate,
      Number refundedAmount,
      String refundReason,
      String refundDecision) {}

  /** A read-only view of what has been paid and refunded. */
  public record Ledger(Map<String, Transaction> payments, Map<String, Refund> refunds) {}

  // Both ledgers are keyed by the hospital's payment reference. The check and the write are made
  // together under the lock, so two jobs cannot both find the reference unpaid and both take money.
  private final Map<String, Transaction> ledger = new ConcurrentHashMap<>();
  private final Map<String, Refund> refunds = new ConcurrentHashMap<>();

  // One counter for the reference numbers, shared between payments and refunds, so no two
  // references the provider issues in a session are the same.
  private final AtomicInteger sequence = new AtomicInteger();

  public PaymentServiceProvider(Config.PaymentSettings config) {
    super(config.latencyMs());
    this.config = config;
  }

  /** What the provider answered for a payment. */
  public record PaymentResult(
      String status,
      String transactionReference,
      String paymentDate,
      Number paidAmount,
      boolean confirmationReceived,
      boolean requiresInvestigation,
      boolean duplicateAttempt) {}

  /**
   * @param paymentReference the hospital's reference for this charge
   * @param requestedOutcome an explicit outcome from the scenario data, or null
   */
  public PaymentResult requestPayment(
      String paymentReference, Number chargeAmount, String requestedOutcome, Instant now) {

    awaitLatency();

    synchronized (ledger) {
      final Transaction settled = ledger.get(paymentReference);
      if (settled != null && COMPLETED.equals(settled.status())) {
        // Already paid. The original transaction is returned and reported as a duplicate so the
        // process can tell the patient and the team, and no further money is taken (FR-023, EX-08).
        return new PaymentResult(
            "duplicate",
            settled.transactionReference(),
            settled.paymentDate(),
            settled.paidAmount(),
            settled.confirmationReceived(),
            settled.requiresInvestigation(),
            true);
      }

      final String outcome = firstSet(requestedOutcome, config.outcome(), COMPLETED);
      final String transactionReference = "PSP-" + compactDate(now) + "-" + referenceNumber();

      if (DECLINED.equals(outcome)) {
        // Nothing was taken, so there is no transaction to keep and no reason to block a later
        // attempt.
        return new PaymentResult(
            DECLINED, transactionReference, Validate.toIsoDate(now), null, true, false, false);
      }

      final boolean confirmationReceived = !SUCCESS_NO_CONFIRMATION.equals(outcome);
      final Transaction transaction =
          new Transaction(
              COMPLETED,
              transactionReference,
              Validate.toIsoDate(now),
              chargeAmount,
              confirmationReceived,
              // BR-07: money taken without a returned confirmation is investigated, never
              // automatically re-requested.
              !confirmationReceived);

      ledger.put(paymentReference, transaction);

      return new PaymentResult(
          transaction.status(),
          transaction.transactionReference(),
          transaction.paymentDate(),
          transaction.paidAmount(),
          transaction.confirmationReceived(),
          transaction.requiresInvestigation(),
          false);
    }
  }

  /**
   * Records a refund against a payment this provider settled earlier.
   *
   * The provider is the only party that can say whether the money was ever taken, so the decision
   * about what may be refunded is made here and reported back as a status the worker turns into a
   * business error:
   *
   * <ul>
   *   <li>{@code refunded} - the refund was recorded</li>
   *   <li>{@code duplicate} - this payment reference has already been refunded</li>
   *   <li>{@code no_settled_payment} - no completed transaction exists for the reference</li>
   *   <li>{@code amount_too_high} - the refund is larger than the amount paid</li>
   *   <li>{@code invalid_amount} - the refund is not a positive amount</li>
   * </ul>
   *
   * @param refundAmount the amount to give back, or null to refund in full
   * @param refundDecision full or partial
   */
  public RefundResult refundPayment(
      String paymentReference,
      Number refundAmount,
      String refundReason,
      String refundDecision,
      Instant now) {

    awaitLatency();

    synchronized (ledger) {
      final Transaction settled = ledger.get(paymentReference);
      if (settled == null || !COMPLETED.equals(settled.status()) || settled.paidAmount() == null) {
        // Nothing was ever taken for this reference, so there is nothing to give back. Refunding it
        // would create money.
        return refused("no_settled_payment", null);
      }

      final Refund alreadyRefunded = refunds.get(paymentReference);
      if (alreadyRefunded != null) {
        // Paying out twice for one cancelled appointment is exactly what the ledger is there to
        // prevent (AC-10).
        return new RefundResult(
            "duplicate",
            alreadyRefunded.refundReference(),
            alreadyRefunded.refundDate(),
            alreadyRefunded.refundedAmount(),
            alreadyRefunded.refundReason(),
            alreadyRefunded.refundDecision(),
            settled.paidAmount(),
            true);
      }

      final Number amount = refundAmount == null ? settled.paidAmount() : refundAmount;
      if (!Double.isFinite(amount.doubleValue()) || amount.doubleValue() <= 0) {
        return refused("invalid_amount", settled.paidAmount());
      }
      if (amount.doubleValue() > settled.paidAmount().doubleValue()) {
        return refused("amount_too_high", settled.paidAmount());
      }

      final Refund refund =
          new Refund(
              "PSP-RF-" + compactDate(now) + "-" + referenceNumber(),
              Validate.toIsoDate(now),
              amount,
              refundReason == null || refundReason.isEmpty() ? null : refundReason,
              refundDecision == null || refundDecision.isEmpty()
                  ? (amount.doubleValue() == settled.paidAmount().doubleValue() ? "full" : "partial")
                  : refundDecision);

      refunds.put(paymentReference, refund);

      return new RefundResult(
          "refunded",
          refund.refundReference(),
          refund.refundDate(),
          refund.refundedAmount(),
          refund.refundReason(),
          refund.refundDecision(),
          settled.paidAmount(),
          false);
    }
  }

  /** Renders a refusal. Only the status and what the provider holds are meaningful for one. */
  private static RefundResult refused(String status, Number paidAmount) {
    return new RefundResult(status, null, null, null, null, null, paidAmount, false);
  }

  /** What the provider answered for a refund. */
  public record RefundResult(
      String status,
      String refundReference,
      String refundDate,
      Number refundedAmount,
      String refundReason,
      String refundDecision,
      Number paidAmount,
      boolean duplicateRefundAttempt) {}

  /** Read-only view of what has been paid and refunded. */
  public Ledger ledger() {
    return new Ledger(Map.copyOf(ledger), Map.copyOf(refunds));
  }

  /** PSP references carry the date and a sequence number, as the provider's own format. */
  private String compactDate(Instant now) {
    return Validate.toIsoDate(now).replace("-", "");
  }

  private String referenceNumber() {
    return String.format("%04d", sequence.incrementAndGet());
  }
}
