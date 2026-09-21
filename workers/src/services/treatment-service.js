/**
 * Simulated external treatment, laboratory and imaging service.
 *
 * The booking team depends on services it does not control, and the case is
 * explicit that a temporary failure there must not produce a duplicate
 * appointment (EX-07, FR-016, TC-12). This module therefore does two things:
 *
 *   1. it returns "temporarily unavailable" when the scenario asks for it, so
 *      the booking can stay pending and the process can record the retry
 *   2. it holds one appointment per booking key, so every further attempt for
 *      the same booking returns the appointment already made
 *
 * Limitations are recorded in `workers/README.md`: no capacity data, no
 * clinical constraints, no real scheduling.
 */

const OUTCOMES = ['available', 'unavailable']

function createTreatmentService(config) {
  // booking key -> { appointmentReference, treatmentAppointmentDate, attempts }
  const bookings = new Map()

  return {
    outcomes: OUTCOMES,

    /**
     * @param {object} request
     * @param {string} request.bookingKey        identifies the treatment appointment
     * @param {string} [request.requestedOutcome] explicit outcome from the scenario data
     * @param {Date}   request.now
     */
    async bookTreatment({ bookingKey, requestedOutcome, now }) {
      await delay(config.latencyMs)

      const outcome = requestedOutcome || config.outcome || 'available'
      const existing = bookings.get(bookingKey)
      const attempts = (existing?.attempts || 0) + 1

      if (outcome === 'unavailable') {
        // Nothing new is booked, but the attempt is recorded so the process can
        // show that further attempts were made and notify the responsible team.
        // If an appointment was already made for this booking key it still
        // stands: a later outage cannot unbook it or create a second one.
        bookings.set(bookingKey, { ...existing, attempts })
        return {
          externalServiceAvailable: false,
          treatmentSlotAvailable: Boolean(existing?.appointmentReference),
          appointmentReference: existing?.appointmentReference || null,
          treatmentAppointmentDate: existing?.treatmentAppointmentDate || null,
          bookingStatus: existing?.appointmentReference ? 'confirmed' : 'pending',
          treatmentRetryCount: attempts,
          duplicateAttempt: Boolean(existing?.appointmentReference),
        }
      }

      if (existing?.appointmentReference) {
        bookings.set(bookingKey, { ...existing, attempts })
        return {
          externalServiceAvailable: true,
          treatmentSlotAvailable: true,
          appointmentReference: existing.appointmentReference,
          treatmentAppointmentDate: existing.treatmentAppointmentDate,
          bookingStatus: 'confirmed',
          treatmentRetryCount: attempts,
          duplicateAttempt: true,
        }
      }

      const appointmentReference = `TRT-${bookingKey}`
      const treatmentAppointmentDate = now.toISOString().slice(0, 10)
      bookings.set(bookingKey, { appointmentReference, treatmentAppointmentDate, attempts })

      return {
        externalServiceAvailable: true,
        treatmentSlotAvailable: true,
        appointmentReference,
        treatmentAppointmentDate,
        bookingStatus: 'confirmed',
        treatmentRetryCount: attempts,
        duplicateAttempt: false,
      }
    },
  }
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

module.exports = { createTreatmentService, TREATMENT_OUTCOMES: OUTCOMES }
