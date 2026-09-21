/**
 * Worker: payment-processing   (job type `process-payment`)
 *
 * Sends the payment request to the external Payment Service Provider and
 * records what came back.
 *
 * The rules this worker enforces:
 *
 *   - Card and security details never reach the hospital system. If any are
 *     present in the variables the worker refuses the payment outright rather
 *     than passing them on (BR-06, NFR-007, AC-09).
 *   - A patient whose treatment is funded by the hospital or an approved
 *     insurer, or who holds an exemption, is not asked to pay: the provider is
 *     not called at all.
 *   - The same payment reference is never charged twice. A second attempt
 *     returns the original transaction marked as a duplicate (FR-023, EX-08,
 *     AC-10).
 *   - A payment taken without a returned confirmation is marked for
 *     investigation and is not re-requested (BR-07, EX-09, TC-09, AC-04).
 */

const { completed, businessError } = require('../outcome')
const {
  ERROR_CODES,
  checkVariables,
  findProhibitedFinancialFields,
} = require('../validate')

/** Funding routes where the patient makes no direct payment (AS-08, AS-09). */
const ROUTES_WITHOUT_PATIENT_PAYMENT = ['hospital', 'insurer', 'exempt']

const INPUT_VARIABLES = {
  chargeAmount: { type: 'money', required: true, max: 100000 },
  fundingRoute: { type: 'string', required: true, oneOf: ['hospital', 'insurer', 'patient', 'exempt'] },
  paymentReference: { type: 'identifier', required: true },
  paymentOutcome: { type: 'string', required: false, oneOf: ['completed', 'declined', 'success_no_confirmation'] },
}

async function handle(variables, { now, services, log }) {
  const prohibited = findProhibitedFinancialFields(variables)
  if (prohibited.length > 0) {
    return businessError(
      ERROR_CODES.PROHIBITED_FINANCIAL_DATA,
      `the payment cannot be processed because card or security details were supplied: ${prohibited.join(', ')}. The hospital system must not hold them.`,
      { paymentStatus: 'not_processed', prohibitedFields: prohibited }
    )
  }

  const { problems, values } = checkVariables(variables, INPUT_VARIABLES)

  if (problems.length > 0) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      `the payment cannot be processed because the request is incomplete or unusable: ${problems.join('; ')}`,
      { paymentStatus: 'not_processed' }
    )
  }

  if (ROUTES_WITHOUT_PATIENT_PAYMENT.includes(values.fundingRoute)) {
    log.info('no patient payment is required for this funding route', {
      fundingRoute: values.fundingRoute,
    })
    return completed({
      paymentStatus: 'not_required',
      fundingRoute: values.fundingRoute,
      transactionReference: null,
      paymentDate: null,
      paidAmount: null,
      confirmationReceived: false,
      requiresInvestigation: false,
    })
  }

  const result = await services.payment.requestPayment({
    paymentReference: values.paymentReference,
    chargeAmount: values.chargeAmount,
    requestedOutcome: values.paymentOutcome,
    now,
  })

  log.info('payment request returned', {
    paymentStatus: result.status,
    transactionReference: result.transactionReference,
    confirmationReceived: result.confirmationReceived,
    requiresInvestigation: result.requiresInvestigation,
    duplicateAttempt: result.duplicateAttempt,
  })

  return completed({
    paymentStatus: result.status,
    fundingRoute: values.fundingRoute,
    transactionReference: result.transactionReference,
    paymentDate: result.paymentDate,
    paidAmount: result.paidAmount,
    confirmationReceived: result.confirmationReceived,
    requiresInvestigation: result.requiresInvestigation,
    duplicateAttempt: result.duplicateAttempt,
  })
}

module.exports = {
  name: 'payment-processing',
  taskType: 'process-payment',
  inputVariables: INPUT_VARIABLES,
  routesWithoutPatientPayment: ROUTES_WITHOUT_PATIENT_PAYMENT,
  handle,
}
