/**
 * Worker: appointment-availability   (job type `check-appointment-availability`)
 *
 * Asks the external scheduling service for a new patient appointment and
 * reports what came back, so the model can decide between booking, highlighting
 * the case, and recording the telephone contact the two-week rule requires
 * (FR-007, BR-03, BR-17, EX-06, TC-01, TC-04, TC-05, TC-18).
 *
 * The simulated service returns three outcomes; see
 * `src/services/scheduling-service.js`. The scenario decides which one, through
 * the optional `schedulingOutcome` variable, so a test can be reproduced from
 * its test data.
 */

const { completed, businessError } = require('../outcome')
const { ERROR_CODES, checkVariables } = require('../validate')
const { appointmentWithinTwoWeeks } = require('../services/scheduling-service')

const INPUT_VARIABLES = {
  speciality: { type: 'identifier', required: true },
  priority: { type: 'string', required: true, oneOf: ['urgent', 'routine'] },
  requestedWindow: { type: 'days', required: true },
  // Supplied by the form as a hint. The value that is written back is always
  // recomputed from the slot actually found, because that is what the two-week
  // contact rule applies to.
  appointmentWithinTwoWeeks: { type: 'boolean', required: false },
  schedulingOutcome: { type: 'string', required: false, oneOf: ['available', 'outside_window', 'none'] },
}

async function handle(variables, { now, instanceKey, services, config, log }) {
  const { problems, values } = checkVariables(variables, INPUT_VARIABLES)

  if (problems.length > 0) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      `appointment availability cannot be checked because the booking request is incomplete or unusable: ${problems.join('; ')}`,
      { slotAvailable: false }
    )
  }

  const bookingKey = `${instanceKey}:new-patient-appointment`
  const result = await services.scheduling.findAppointment({
    bookingKey,
    requestedOutcome: values.schedulingOutcome,
    priority: values.priority,
    requestedWindowDays: values.requestedWindow,
    now,
  })

  const withinTwoWeeks = appointmentWithinTwoWeeks(
    result.appointmentDate,
    now,
    config.businessCalendar.urgentTelephoneContactDays
  )

  log.info('appointment availability checked', {
    appointmentOutcome: result.outcome,
    slotAvailable: result.slotAvailable,
    appointmentDate: result.appointmentDate,
    duplicateRequest: result.duplicateRequest,
  })

  return completed({
    slotAvailable: result.slotAvailable,
    appointmentDate: result.appointmentDate,
    alternativeDate: result.alternativeDate,
    withinRequestedWindow: result.withinRequestedWindow,
    appointmentWithinTwoWeeks: withinTwoWeeks,
    schedulingServiceOutcome: result.outcome,
  })
}

module.exports = {
  name: 'appointment-availability',
  taskType: 'check-appointment-availability',
  inputVariables: INPUT_VARIABLES,
  handle,
}
