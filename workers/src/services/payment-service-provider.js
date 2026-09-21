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
 * Limitations are recorded in `workers/README.md`: no card processing, no
 * clearing or settlement, no 3-D Secure, no fraud checks; refunds are recorded
 * rather than executed.
 */

const OUTCOMES = ['completed', 'declined', 'success_no_confirmation']

function createPaymentServiceProvider(config) {
  // payment reference -> { status, transactionReference, paymentDate, paidAmount, confirmationReceived }
  const ledger = new Map()
  let sequence = 0

  return {
    outcomes: OUTCOMES,

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
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

module.exports = { createPaymentServiceProvider, PAYMENT_OUTCOMES: OUTCOMES }
