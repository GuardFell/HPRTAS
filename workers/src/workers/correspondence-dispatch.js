/**
 * Worker: correspondence-dispatch   (job type `send-correspondence`)
 *
 * Sends appointment letters and clinic letters through the external
 * correspondence service and records the dispatch date, the channel and a
 * reference, which is what the case asks the system to keep (FR-008, FR-037).
 *
 * The patient's channel preference is used where it is recorded and defaults to
 * letter by post where it is not (NFR-012, AS-12). The two-week telephone
 * contact is a safety net that applies whatever the preference is, so it is not
 * decided here; the model handles it from `appointmentWithinTwoWeeks`.
 */

const { completed, businessError } = require('../outcome')
const { ERROR_CODES, checkVariables, asList } = require('../validate')
const { CORRESPONDENCE_CHANNELS } = require('../services/correspondence-service')

const DOCUMENT_TYPES = [
  'new_patient_clinic_letter',
  'follow_up_clinic_letter',
  'appointment_letter',
  'referral_outcome_letter',
  'deferral_or_cancellation_letter',
]

const INPUT_VARIABLES = {
  recipients: { type: 'list', required: true, max: 20 },
  channelPreference: { type: 'string', required: false, oneOf: CORRESPONDENCE_CHANNELS },
  documentType: { type: 'string', required: true, oneOf: DOCUMENT_TYPES },
  correspondenceOutcome: { type: 'string', required: false, oneOf: ['dispatched'] },
}

async function handle(variables, { now, services, log }) {
  const { problems, values } = checkVariables(variables, INPUT_VARIABLES)

  if (problems.length > 0) {
    return businessError(
      ERROR_CODES.INVALID_VARIABLE,
      `the correspondence cannot be dispatched because the request is incomplete or unusable: ${problems.join('; ')}`,
      { dispatchChannel: null }
    )
  }

  const recipients = asList(values.recipients)
  const result = await services.correspondence.dispatch({
    recipients,
    channelPreference: values.channelPreference,
    documentType: values.documentType,
    now,
  })

  log.info('correspondence dispatched', {
    documentType: values.documentType,
    channel: result.dispatchChannel,
    recipientCount: recipients.length,
    dispatchReference: result.dispatchReference,
  })

  return completed({
    dispatchDate: result.dispatchDate,
    dispatchChannel: result.dispatchChannel,
    dispatchReference: result.dispatchReference,
    documentType: values.documentType,
    recipients: recipients,
  })
}

module.exports = {
  name: 'correspondence-dispatch',
  taskType: 'send-correspondence',
  inputVariables: INPUT_VARIABLES,
  documentTypes: DOCUMENT_TYPES,
  handle,
}
