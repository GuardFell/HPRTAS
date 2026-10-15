package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.db.Database;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Simulated external scheduling service.
 *
 * The hospital has no interface to a real scheduling system, so this stands in for one (AS-03). It
 * is deliberately small, and its limitations are listed in {@code workers/README.md}: there is no
 * clinic capacity, no room or clinician availability and no real diary.
 *
 * Three outcomes, which are what the model needs to exercise the alternative and exception paths:
 *
 * <ul>
 *   <li>{@code available} - a slot inside the window the clinician asked for (TC-01)</li>
 *   <li>{@code outside_window} - a slot exists, but later than the requested period, so the case
 *       must be highlighted rather than quietly booked outside it (BR-17, EX-06, TC-05, TC-18)</li>
 *   <li>{@code none} - nothing available at all</li>
 * </ul>
 *
 * One appointment per booking key, held in the hospital's record store: a second request for the
 * same key returns the appointment already made instead of creating another one, which is how "no
 * duplicate appointments" is implemented (FR-016, AC-10). The check and the write are made together
 * under the store's lock, so two jobs for one booking key cannot both find nothing and then both
 * book. The booking key names the request - the patient type and the window that was asked for - so
 * a booking request for a different window is a different booking, and the appointment the first
 * one made is superseded rather than left standing as though it were still going to happen.
 *
 * The appointment is in the store rather than in this class, so it survives the worker process: a
 * request repeated after a restart still returns the appointment the first request made.
 */
public class SchedulingService extends SimulatedService {

  public static final String AVAILABLE = "available";
  public static final String OUTSIDE_WINDOW = "outside_window";
  public static final String NONE = "none";

  /** The accepted {@code schedulingOutcome} values, for the error message and the documentation. */
  public static final List<String> OUTCOMES = List.of(AVAILABLE, OUTSIDE_WINDOW, NONE);

  /**
   * The appointment type this service books. Both the new patient appointment and the follow-up are
   * patient appointments; which referral they belong to is what tells them apart on the register.
   */
  public static final String PATIENT_APPOINTMENT = "patient-appointment";

  private final Config.SchedulingSettings config;
  private final Database database;

  public SchedulingService(Config.SchedulingSettings config, Database database) {
    super(config.latencyMs());
    this.config = config;
    this.database = database;
  }

  /** What the service answered. */
  public record Appointment(
      String outcome,
      boolean slotAvailable,
      String appointmentDate,
      String alternativeDate,
      boolean withinRequestedWindow,
      boolean duplicateRequest,
      boolean supersededAppointment) {}

  /**
   * @param bookingKey identifies the appointment being sought
   * @param requestedOutcome an explicit outcome from the scenario data, or null
   * @param priority urgent or routine
   * @param patientId the patient the appointment is for, or null when the process does not say
   * @param referralId the referral it belongs to, or null
   * @param appointmentType new-patient or follow-up
   */
  public Appointment findAppointment(
      String bookingKey,
      String requestedOutcome,
      String priority,
      int requestedWindowDays,
      Instant now,
      String patientId,
      String referralId,
      String appointmentType) {

    awaitLatency();

    synchronized (database) {
      final Optional<Database.Appointment> known = database.findAppointment(bookingKey);
      if (known.isPresent()) {
        final Database.Appointment stored = known.get();
        return new Appointment(
            stored.outcome(),
            AVAILABLE.equals(stored.outcome()),
            stored.appointmentDate(),
            stored.alternativeDate(),
            Boolean.TRUE.equals(stored.withinRequestedWindow()),
            true,
            false);
      }

      final String outcome = firstSet(requestedOutcome, config.outcome(), AVAILABLE);
      final int leadDays =
          "urgent".equals(priority)
              ? config.slotLeadDaysUrgent()
              : Math.min(config.slotLeadDaysRoutine(), requestedWindowDays);

      String appointmentDate = null;
      String alternativeDate = null;
      boolean withinRequestedWindow = false;

      if (AVAILABLE.equals(outcome)) {
        appointmentDate = Validate.toIsoDatePlusDays(now, leadDays);
        withinRequestedWindow = leadDays <= requestedWindowDays;
      } else {
        // Either there is nothing at all, or only something beyond the period the clinician asked
        // for. Both are offered as an alternative date.
        alternativeDate =
            Validate.toIsoDatePlusDays(now, requestedWindowDays + config.outsideWindowExtraDays());
      }

      // Only an appointment that was actually made is recorded as one: a request that found nothing
      // is answered again if it is repeated rather than being remembered as an appointment.
      boolean superseded = false;
      if (AVAILABLE.equals(outcome)) {
        database.saveAppointment(
            new Database.Appointment(
                bookingKey,
                appointmentType,
                patientId,
                referralId,
                appointmentDate,
                alternativeDate,
                withinRequestedWindow,
                null,
                "booked",
                outcome,
                1,
                Validate.toIsoDate(now)));
        superseded =
            database.supersedeActiveAppointments(patientId, referralId, null, bookingKey, now) > 0;
      }

      return new Appointment(
          outcome,
          AVAILABLE.equals(outcome),
          appointmentDate,
          alternativeDate,
          withinRequestedWindow,
          false,
          superseded);
    }
  }

  /** The two-week contact rule (BR-03) needs whole calendar days (AS-02). */
  public static boolean appointmentWithinTwoWeeks(
      String appointmentDate, Instant now, int thresholdDays) {
    if (appointmentDate == null) {
      return false;
    }
    return Validate.daysBetween(now, LocalDate.parse(appointmentDate)) <= thresholdDays;
  }
}
