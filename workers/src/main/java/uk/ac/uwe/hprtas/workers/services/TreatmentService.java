package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.db.Database;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Simulated external treatment, laboratory and imaging service.
 *
 * The booking team depends on services it does not control, and the case is explicit that a
 * temporary failure there must not produce a duplicate appointment (EX-07, FR-016, TC-12). This
 * therefore does two things:
 *
 * <ol>
 *   <li>it returns "temporarily unavailable" when the scenario asks for it, so the booking can stay
 *       pending and the process can record the retry</li>
 *   <li>it holds one appointment per booking key, so every further attempt for the same booking
 *       returns the appointment already made</li>
 * </ol>
 *
 * The booking, and how many attempts have been made for it, are held in the hospital's record store
 * rather than in this class, so they survive the worker process restarting. The booking key names the
 * request: the appointment type and the start date that was asked for. A rescheduled request names a
 * different start date, so it is a different booking - which is why the appointment it makes can be
 * a different one while a retry of the original request still returns the original appointment.
 *
 * Limitations are recorded in {@code workers/README.md}: no capacity data, no clinical constraints,
 * no real scheduling. The appointment date is the start date the request asked for, or the day the
 * request was made when it named none: there is no diary behind it either way.
 */
public class TreatmentService extends SimulatedService {

  public static final String AVAILABLE = "available";
  public static final String UNAVAILABLE = "unavailable";

  public static final List<String> OUTCOMES = List.of(AVAILABLE, UNAVAILABLE);

  /** The appointment type recorded for the appointments this service books. */
  public static final String TREATMENT_APPOINTMENT = "treatment-appointment";

  private final Config.TreatmentSettings config;
  private final Database database;

  public TreatmentService(Config.TreatmentSettings config, Database database) {
    super(config.latencyMs());
    this.config = config;
    this.database = database;
  }

  /** What the service answered. */
  public record BookingResult(
      boolean externalServiceAvailable,
      boolean treatmentSlotAvailable,
      String appointmentReference,
      String treatmentAppointmentDate,
      String bookingStatus,
      int treatmentRetryCount,
      boolean duplicateAttempt,
      boolean supersededAppointment) {}

  /**
   * @param bookingKey identifies the treatment appointment
   * @param requestedOutcome an explicit outcome from the scenario data, or null
   * @param requestedStartDate the start date the request asked for, or null
   * @param patientId the patient, or null when the process does not say
   * @param referralId the referral the treatment belongs to, or null
   */
  public BookingResult bookTreatment(
      String bookingKey,
      String requestedOutcome,
      Instant now,
      String requestedStartDate,
      String patientId,
      String referralId) {

    awaitLatency();

    synchronized (database) {
      final String outcome = firstSet(requestedOutcome, config.outcome(), AVAILABLE);
      final Optional<Database.Appointment> stored = database.findAppointment(bookingKey);
      final int attempts = (stored.map(Database.Appointment::attempts).orElse(0)) + 1;
      final String existingReference = stored.map(Database.Appointment::appointmentReference).orElse(null);
      final String existingDate = stored.map(Database.Appointment::appointmentDate).orElse(null);

      if (UNAVAILABLE.equals(outcome)) {
        // Nothing new is booked, but the attempt is recorded so the process can show that further
        // attempts were made and notify the responsible team. If an appointment was already made
        // for this booking key it still stands: a later outage cannot unbook it or create a second
        // one.
        final boolean alreadyBooked = existingReference != null;
        database.saveAppointment(
            new Database.Appointment(
                bookingKey,
                TREATMENT_APPOINTMENT,
                patientId,
                referralId,
                existingDate,
                null,
                null,
                existingReference,
                alreadyBooked ? "confirmed" : "pending",
                outcome,
                attempts,
                Validate.toIsoDate(now)));

        return new BookingResult(
            false, alreadyBooked, existingReference, existingDate,
            alreadyBooked ? "confirmed" : "pending", attempts, alreadyBooked, false);
      }

      if (existingReference != null) {
        database.saveAppointment(
            new Database.Appointment(
                bookingKey,
                TREATMENT_APPOINTMENT,
                patientId,
                referralId,
                existingDate,
                null,
                null,
                existingReference,
                "confirmed",
                outcome,
                attempts,
                Validate.toIsoDate(now)));
        return new BookingResult(
            true, true, existingReference, existingDate, "confirmed", attempts, true, false);
      }

      final String appointmentReference = "TRT-" + bookingKey;
      final String treatmentAppointmentDate =
          requestedStartDate == null || requestedStartDate.isEmpty()
              ? Validate.toIsoDate(now)
              : requestedStartDate;

      database.saveAppointment(
          new Database.Appointment(
              bookingKey,
              TREATMENT_APPOINTMENT,
              patientId,
              referralId,
              treatmentAppointmentDate,
              null,
              null,
              appointmentReference,
              "confirmed",
              outcome,
              attempts,
              Validate.toIsoDate(now)));

      // This is a new booking for the patient, so any other live treatment appointment of theirs is
      // the one it replaces: a rescheduled booking must not leave two treatment appointments on the
      // register as though both were going to happen.
      final boolean superseded =
          database.supersedeActiveAppointments(
                  patientId, null, TREATMENT_APPOINTMENT, bookingKey, now)
              > 0;

      return new BookingResult(
          true, true, appointmentReference, treatmentAppointmentDate, "confirmed", attempts, false,
          superseded);
    }
  }
}
