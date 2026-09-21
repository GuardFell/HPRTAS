/**
 * Simulated external Payment Service Provider.
 *
 * The case requires a payment request to be sent to an external provider and
 * only the status, reference, date and amount to come back - never the card
 * details (BR-06, NFR-007, AC-09). This module models that boundary and the
 * outcomes the process has to handle:
 *
 *   completed                the payment was taken and confirmed (TC-07)
 *   declined                 the payment was refused (EX-08, TC-08)
 *   success_no_confirmation  the payment was taken but the confirmation did
 *                            not come back, so the transaction must be marked
 *                            for investigation and not re-requested
 *                            (BR-07, EX-09, TC-09, AC-04)
 *
 * A transaction ledger keyed by the payment reference is what stops the patient
 * being charged twice for the same appointment: once a reference has been paid,
 * any further attempt returns the original transaction marked as a duplicate
 * and takes no more money (FR-023, EX-08, AC-10). A declined attempt recorded
 * nothing, so a retry after a decline is allowed.
 *
 * The same ledger answers refunds, which is what `refund-processing` calls. A
 * refund is only recorded against a payment the provider actually settled, it
 * can never exceed what was paid, and a second refund against the same payment
 * reference returns the first one as a duplicate rather than paying out twice
 * (BR-20, FR-033, AC-10).
 *
 * Limitations are recorded in `workers/README.md`: no card processing, no
 * clearing or settlement, no 3-D Secure, no fraud checks; refunds are recorded
 * rather than executed.
 */

const OUTCOMES = ['completed', 'declined', 'success_no_confirmation']
const REFUND_OUTCOMES = ['refunded']

function createPaymentServiceProvider(config) {
  // payment reference -> { status, transactionReference, paymentDate, paidAmount, confirmationReceived }
  const ledger = new Map()
  // payment reference -> { refundReference, refundDate, refundedAmount, refundReason, refundDecision }
  const refunds = new Map()
  let sequence = 0

  return {
    outcomes: OUTCOMES,
    refundOutcomes: REFUND_OUTCOMES,

    /**
     * @param {object} request
     * @param {string} request.paymentReference  the hospital's reference for this charge
     * @param {number} request.chargeAmount
     * @param {string} [request.requestedOutcome] explicit outcome from the scenario data
     * @param {Date}   request.now
     */
    async requestPayment({ paymentReference, chargeAmount, requestedOutcome, now }) {
      await delay(config.latencyMs)

      const settled = ledger.get(paymentReference)
      if (settled && settled.status === 'completed') {
        // Already paid. The original transaction is returned and reported as a
        // duplicate so the process can tell the patient and the team, and no
        // further money is taken (FR-023, EX-08, AC-10).
        return {
          ...settled,
          status: 'duplicate',
          duplicateAttempt: true,
        }
      }

      const outcome = requestedOutcome || config.outcome || 'completed'
      sequence += 1
      const transactionReference = `PSP-${now.toISOString().slice(0, 10).replace(/-/g, '')}-${String(sequence).padStart(4, '0')}`

      if (outcome === 'declined') {
        // Nothing was taken, so there is no transaction to keep and no reason
        // to block a later attempt.
        return {
          status: 'declined',
          transactionReference,
          paymentDate: now.toISOString().slice(0, 10),
          paidAmount: null,
          confirmationReceived: true,
          requiresInvestigation: false,
          duplicateAttempt: false,
        }
      }

      const confirmationReceived = outcome !== 'success_no_confirmation'
      const transaction = {
        status: 'completed',
        transactionReference,
        paymentDate: now.toISOString().slice(0, 10),
        paidAmount: chargeAmount,
        confirmationReceived,
        // BR-07: money taken without a returned confirmation is investigated,
        // never automatically re-requested.
        requiresInvestigation: !confirmationReceived,
      }
      ledger.set(paymentReference, transaction)

      return { ...transaction, duplicateAttempt: false }
    },

    /**
     * Records a refund against a payment this provider settled earlier.
     *
     * The provider is the only party that can say whether the money was ever
     * taken, so the decision about what may be refunded is made here and
     * reported back as a status the worker turns into a business error:
     *
     *   refunded            the refund was recorded
     *   duplicate           this payment reference has already been refunded
     *   no_settled_payment  no completed transaction exists for the reference
     *   amount_too_high     the refund is larger than the amount paid
     *   invalid_amount      the refund is not a positive amount
     *
     * @param {object} request
     * @param {string} request.paymentReference  the reference of the payment to refund
     * @param {number} [request.refundAmount]    omitted means refund in full
     * @param {string} [request.refundReason]
     * @param {string} [request.refundDecision]  full | partial
     * @param {Date}   request.now
     */
    async refundPayment({ paymentReference, refundAmount, refundReason, refundDecision, now }) {
      await delay(config.latencyMs)

      const settled = ledger.get(paymentReference)
      if (!settled || settled.status !== 'completed' || typeof settled.paidAmount !== 'number') {
        // Nothing was ever taken for this reference, so there is nothing to
        // give back. Refunding it would create money.
        return { status: 'no_settled_payment', paidAmount: null, refundedAmount: null }
      }

      const alreadyRefunded = refunds.get(paymentReference)
      if (alreadyRefunded) {
        // Paying out twice for one cancelled appointment is exactly what the
        // ledger is there to prevent (AC-10).
        return { ...alreadyRefunded, status: 'duplicate', duplicateRefundAttempt: true }
      }

      const amount = refundAmount === undefined || refundAmount === null ? settled.paidAmount : refundAmount
      if (typeof amount !== 'number' || !Number.isFinite(amount) || amount <= 0) {
        return { status: 'invalid_amount', paidAmount: settled.paidAmount, refundedAmount: null }
      }
      if (amount > settled.paidAmount) {
        return { status: 'amount_too_high', paidAmount: settled.paidAmount, refundedAmount: null }
      }

      sequence += 1
      const refund = {
        refundReference: `PSP-RF-${now.toISOString().slice(0, 10).replace(/-/g, '')}-${String(sequence).padStart(4, '0')}`,
        refundDate: now.toISOString().slice(0, 10),
        refundedAmount: amount,
        refundReason: refundReason || null,
        refundDecision: refundDecision || (amount === settled.paidAmount ? 'full' : 'partial'),
      }
      refunds.set(paymentReference, refund)

      return { ...refund, status: 'refunded', paidAmount: settled.paidAmount, duplicateRefundAttempt: false }
    },

    /** Read-only view of what has been paid and refunded, used by the tests. */
    ledger() {
      return {
        payments: Object.fromEntries(ledger),
        refunds: Object.fromEntries(refunds),
      }
    },
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

module.exports = {
  createPaymentServiceProvider,
  PAYMENT_OUTCOMES: OUTCOMES,
  REFUND_OUTCOMES,
}
