/**
 * Simulated external scheduling service.
 *
 * The hospital has no interface to a real scheduling system, so this module
 * stands in for one (AS-03). It is deliberately small and its limitations are
 * listed in `workers/README.md`: there is no clinic capacity, no room or
 * clinician availability and no real diary.
 *
 * Three outcomes, which are what the model needs to exercise the alternative
 * and exception paths:
 *
 *   available       a slot inside the window the clinician asked for (TC-01)
 *   outside_window  a slot exists, but later than the requested period, so the
 *                   case must be highlighted rather than quietly booked
 *                   outside it (BR-17, EX-06, TC-05, TC-18)
 *   none            nothing available at all
 *
 * The outcome is taken from the scenario data first, then from configuration,
 * so a test run is reproducible from its test data and a demonstrator can still
 * force one outcome for a whole session.
 */

const { addDays, daysBetween, toIsoDate } = require('../validate')

const OUTCOMES = ['available', 'outside_window', 'none']

function createSchedulingService(config) {
  // One appointment per booking key. A second request for the same key returns
  // the appointment already made instead of creating another one, which is how
  // "no duplicate appointments" is implemented (FR-016, AC-10).
  const appointments = new Map()

  return {
    /** The accepted `schedulingOutcome` values, for the error message and the docs. */
    outcomes: OUTCOMES,

    /**
     * @param {object} request
     * @param {string} request.bookingKey          identifies the appointment being sought
     * @param {string} [request.requestedOutcome]  explicit outcome from the scenario data
     * @param {string} request.priority            urgent or routine
     * @param {number} request.requestedWindowDays
     * @param {Date}   request.now
     */
    async findAppointment({ bookingKey, requestedOutcome, priority, requestedWindowDays, now }) {
      await delay(config.latencyMs)

      const known = appointments.get(bookingKey)
      if (known) return { ...known, duplicateRequest: true }

      const outcome = requestedOutcome || config.outcome || 'available'
      const leadDays =
        priority === 'urgent'
          ? config.slotLeadDaysUrgent
          : Math.min(config.slotLeadDaysRoutine, requestedWindowDays)

      let appointmentDate = null
      let alternativeDate = null
      let withinRequestedWindow = false

      if (outcome === 'available') {
        appointmentDate = addDays(now, leadDays)
        withinRequestedWindow = leadDays <= requestedWindowDays
      } else {
        // Either there is nothing at all, or only something beyond the period
        // the clinician asked for. Both are offered as an alternative date.
        alternativeDate = addDays(now, requestedWindowDays + config.outsideWindowExtraDays)
      }

      const result = {
        outcome,
        slotAvailable: outcome === 'available',
        appointmentDate: appointmentDate ? toIsoDate(appointmentDate) : null,
        alternativeDate: alternativeDate ? toIsoDate(alternativeDate) : null,
        withinRequestedWindow,
        duplicateRequest: false,
      }

      if (outcome === 'available') appointments.set(bookingKey, result)
      return result
    },
  }
}

/** The two-week contact rule (BR-03) needs whole calendar days (AS-02). */
function appointmentWithinTwoWeeks(appointmentDate, now, thresholdDays) {
  if (!appointmentDate) return false
  return daysBetween(now, new Date(`${appointmentDate}T00:00:00Z`)) <= thresholdDays
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

module.exports = {
  createSchedulingService,
  appointmentWithinTwoWeeks,
  SCHEDULING_OUTCOMES: OUTCOMES,
}
