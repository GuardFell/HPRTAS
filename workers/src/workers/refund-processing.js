/**
 * Worker: refund-processing   (job type `process-refund`)
 *
 * Sends an approved refund to the external Payment Service Provider, for the
 * Finance Team's decision on a paid appointment that has been cancelled,
 * rescheduled or otherwise changed (BR-20, BR-21, FR-033, FR-034).
 *
 * The refund is not a payment with the sign reversed, which is why it has its
 * own job type rather than reusing `process-payment`:
 *
 *   - the money moves out, so the provider must first confirm that this payment
 *     reference was actually settled. Refunding a reference that was never paid
 *     would create money, and is refused (BR-20).
 *   - a refund may be full or partial, so the amount is checked against what was
 *     paid rather than against a charge the hospital is asking for.
 *   - the same appointment must never be refunded twice, so a second refund for
 *     a settled reference returns the first one as a duplicate (AC-10).
 *   - card and security details must not reach the hospital system any more on
 *     the way out than on the way in (BR-06, NFR-007, AC-09).
 *
 * Every one of those refusals is reported as INVALID_VARIABLE, which is the error
 * catch event the refund task in `core-4-follow-up-cancellation-enquiry-and-refund`
 * carries, so the Finance Team is handed the request back to correct instead of
 * the process stalling on an incident.
 */

const { completed, businessError } = require('../outcome')
const {
  ERROR_CODES,
  checkVariables,
  findProhibitedFinancialFields,
} = require('../validate')

/** The decisions the model routes here. `none` goes straight to the record, not to the provider. */
const REFUND_DECISIONS = ['full', 'partial']

const INPUT_VARIABLES = {
  paymentReference: { type: 'identifier', required: true },
  refundDecision: { type: 'string', required: true, oneOf: REFUND_DECISIONS },
  // Only meaningful for a partial refund; a full refund takes the amount the
  // provider actually settled, which is recorded in its ledger, not on the form.
  refundAmount: { type: 'money', required: false, max: 100000 },
  refundReason: {
    type: 'string',
    required: true,
    oneOf: ['cancelled', 'rescheduled', 'treatment_changed', 'other'],
  },
}

async function handle(variables, { now, services, log }) {
  const prohibited = findProhibitedFinancialFields(variables)
  if (prohibited.length > 0) {
    return businessError(
      ERROR_CODES.PROHIBITED_FINANCIAL_DATA,
      `the refund cannot be processed because card or security details were supplied: ${prohibited.join(', ')}. The hospital system must not hold them.`,
      { refundStatus: 'not_processed', prohibitedFields: prohibited }
    )
  }

  const { problems, values } = checkVariables(variables, INPUT_VARIABLES)

  if (problems.length > 0) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      `the refund cannot be processed because the request is incomplete or unusable: ${problems.join('; ')}`,
      { refundStatus: 'not_processed' }
    )
  }

  if (values.refundDecision === 'partial' && values.refundAmount === undefined) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      'the refund cannot be processed because a partial refund must state how much is to be refunded',
      { refundStatus: 'not_processed' }
    )
  }

  const result = await services.payment.refundPayment({
    paymentReference: values.paymentReference,
    // A full refund is refunded in full; the provider supplies the amount it
    // actually took rather than trusting a figure typed on the request.
    refundAmount: values.refundDecision === 'full' ? undefined : values.refundAmount,
    refundReason: values.refundReason,
    refundDecision: values.refundDecision,
    now,
  })

  const refusals = {
    no_settled_payment:
      'the provider has no settled payment for this reference, so there is nothing to refund',
    amount_too_high:
      `the refund is larger than the amount the provider took (${result.paidAmount})`,
    invalid_amount: 'the refund amount is not a positive amount',
  }

  if (refusals[result.status]) {
    log.warn('refund refused by the provider', {
      paymentReference: values.paymentReference,
      providerStatus: result.status,
      paidAmount: result.paidAmount,
    })
    return businessError(ERROR_CODES.INVALID_VARIABLE, refusals[result.status], {
      refundStatus: 'not_processed',
      paidAmount: result.paidAmount,
    })
  }

  log.info('refund recorded by the provider', {
    paymentReference: values.paymentReference,
    refundStatus: result.status,
    refundedAmount: result.refundedAmount,
    duplicateRefundAttempt: Boolean(result.duplicateRefundAttempt),
  })

  return completed({
    refundStatus: result.status === 'duplicate' ? 'duplicate' : values.refundDecision,
    refundReference: result.refundReference,
    refundDate: result.refundDate,
    refundedAmount: result.refundedAmount,
    refundReason: values.refundReason,
    duplicateRefundAttempt: Boolean(result.duplicateRefundAttempt),
  })
}

module.exports = {
  name: 'refund-processing',
  taskType: 'process-refund',
  inputVariables: INPUT_VARIABLES,
  refundDecisions: REFUND_DECISIONS,
  handle,
}
