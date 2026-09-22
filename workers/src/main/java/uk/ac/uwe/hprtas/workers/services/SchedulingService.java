package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 */
public class SchedulingService extends SimulatedService {

  public static final String AVAILABLE = "available";
  public static final String OUTSIDE_WINDOW = "outside_window";
  public static final String NONE = "none";

  /** The accepted {@code schedulingOutcome} values, for the error message and the documentation. */
  public static final List<String> OUTCOMES = List.of(AVAILABLE, OUTSIDE_WINDOW, NONE);

  private final Config.SchedulingSettings config;

  // One appointment per booking key. A second request for the same key returns the appointment
  // already made instead of creating another one, which is how "no duplicate appointments" is
  // implemented (FR-016, AC-10). Job workers run on more than one thread, so the check and the
  // booking are made together under the ledger's lock: two jobs for one booking key must not both
  // find nothing and then both book.
  private final Map<String, Appointment> appointments = new ConcurrentHashMap<>();

  public SchedulingService(Config.SchedulingSettings config) {
    super(config.latencyMs());
    this.config = config;
  }

  /** What the service answered. */
  public record Appointment(
      String outcome,
      boolean slotAvailable,
      String appointmentDate,
      String alternativeDate,
      boolean withinRequestedWindow,
      boolean duplicateRequest) {}

  /**
   * @param bookingKey identifies the appointment being sought
   * @param requestedOutcome an explicit outcome from the scenario data, or null
   * @param priority urgent or routine
   */
  public Appointment findAppointment(
      String bookingKey,
      String requestedOutcome,
      String priority,
      int requestedWindowDays,
      Instant now) {

    awaitLatency();

    synchronized (appointments) {
      final Appointment known = appointments.get(bookingKey);
      if (known != null) {
        return new Appointment(
            known.outcome(),
            known.slotAvailable(),
            known.appointmentDate(),
            known.alternativeDate(),
            known.withinRequestedWindow(),
            true);
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

      final Appointment result =
          new Appointment(
              outcome,
              AVAILABLE.equals(outcome),
              appointmentDate,
              alternativeDate,
              withinRequestedWindow,
              false);

      if (AVAILABLE.equals(outcome)) {
        appointments.put(bookingKey, result);
      }
      return result;
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
