/**
 * Worker: treatment-availability   (job type `check-treatment-availability`)
 *
 * Books the treatment appointment through the external treatment, laboratory
 * and imaging service, after the funding and payment gate.
 *
 * Three things from the case are implemented here:
 *
 *   - An unauthorised Treatment Booking Request is refused. The request must
 *     have been completed and authorised by a clinical professional before
 *     administrative staff may process it (BR-04, FR-014, TC-06, AC-02).
 *   - A service that is temporarily unavailable leaves the booking pending, with
 *     the attempt recorded and no duplicate appointment (EX-07, FR-016, TC-12,
 *     AC-10).
 *   - Further attempts for the same booking return the appointment already
 *     made, so retrying cannot create a second one.
 */

const { completed, businessError } = require('../outcome')
const { ERROR_CODES, checkVariables, asList } = require('../validate')

const INPUT_VARIABLES = {
  proposedTreatment: { type: 'identifier', required: true },
  treatmentStartDate: { type: 'identifier', required: true },
  specialResources: { type: 'list', required: false, default: [] },
  // Written by the treatment authorisation form. Administrative staff cannot
  // supply it, which is the point of the check (BR-04, AS-06).
  clinicalAuthorised: { type: 'boolean', required: true },
  treatmentOutcome: { type: 'string', required: false, oneOf: ['available', 'unavailable'] },
}

async function handle(variables, { now, instanceKey, services, log }) {
  const { problems, values } = checkVariables(variables, INPUT_VARIABLES)

  if (problems.length > 0) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      `the treatment booking cannot be processed because the request is incomplete or unusable: ${problems.join('; ')}`,
      { bookingStatus: 'not_processed' }
    )
  }

  if (!values.clinicalAuthorised) {
    return businessError(
      ERROR_CODES.UNAUTHORISED_BOOKING_REQUEST,
      'the Treatment Booking Request has not been authorised by a clinical professional, so it cannot be processed',
      { bookingStatus: 'not_processed', treatmentSlotAvailable: false }
    )
  }

  const bookingKey = `${instanceKey}:treatment-appointment`
  const result = await services.treatment.bookTreatment({
    bookingKey,
    requestedOutcome: values.treatmentOutcome,
    now,
  })

  log.info('treatment booking attempted', {
    bookingStatus: result.bookingStatus,
    externalServiceAvailable: result.externalServiceAvailable,
    appointmentReference: result.appointmentReference,
    treatmentRetryCount: result.treatmentRetryCount,
    duplicateAttempt: result.duplicateAttempt,
  })

  return completed({
    ...result,
    proposedTreatment: values.proposedTreatment,
    treatmentStartDate: values.treatmentStartDate,
    specialResources: asList(values.specialResources),
  })
}

module.exports = {
  name: 'treatment-availability',
  taskType: 'check-treatment-availability',
  inputVariables: INPUT_VARIABLES,
  handle,
}
