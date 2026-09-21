/**
 * Simulated external correspondence service.
 *
 * Letters to patients, GPs and other providers go out through an external
 * service (FR-008). This module records what was dispatched, to which
 * recipients, on which channel and when, which is what the case asks the system
 * to retain (FR-037).
 *
 * Limitations are recorded in `workers/README.md`: there is no printing, no
 * postage and no delivery confirmation, and the patient's channel preference is
 * recorded and echoed but is not honoured end to end.
 */

const OUTCOMES = ['dispatched']

/**
 * The channels accepted at a referral, plus the default used when the patient
 * has not stated a preference (NFR-012, AS-12).
 */
const CHANNELS = ['post', 'digital', 'authorised_representative', 'accessible_format']
const DEFAULT_CHANNEL = 'post'

function createCorrespondenceService(config) {
  const dispatches = []

  return {
    outcomes: OUTCOMES,
    channels: CHANNELS,

    /**
     * @param {object} request
     * @param {string[]} request.recipients      who the letter goes to
     * @param {string}   [request.channelPreference]
     * @param {string}   request.documentType
     * @param {Date}     request.now
     */
    async dispatch({ recipients, channelPreference, documentType, now }) {
      await delay(config.latencyMs)

      const dispatchChannel = channelPreference || DEFAULT_CHANNEL
      const dispatchDate = now.toISOString().slice(0, 10)
      const dispatchReference = `COR-${now.getTime()}-${dispatches.length + 1}`

      dispatches.push({ recipients, dispatchChannel, documentType, dispatchDate, dispatchReference })

      return { dispatchDate, dispatchChannel, dispatchReference, dispatchedTo: recipients }
    },
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

module.exports = {
  createCorrespondenceService,
  CORRESPONDENCE_OUTCOMES: OUTCOMES,
  CORRESPONDENCE_CHANNELS: CHANNELS,
  DEFAULT_CORRESPONDENCE_CHANNEL: DEFAULT_CHANNEL,
}
