package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.Validate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * Limitations are recorded in {@code workers/README.md}: no capacity data, no clinical constraints,
 * no real scheduling.
 */
public class TreatmentService extends SimulatedService {

  public static final String AVAILABLE = "available";
  public static final String UNAVAILABLE = "unavailable";

  public static final List<String> OUTCOMES = List.of(AVAILABLE, UNAVAILABLE);

  private final Config.TreatmentSettings config;

  // booking key -> the appointment made for it, and how many attempts have been made
  private final Map<String, Booking> bookings = new ConcurrentHashMap<>();

  public TreatmentService(Config.TreatmentSettings config) {
    super(config.latencyMs());
    this.config = config;
  }

  private record Booking(String appointmentReference, String treatmentAppointmentDate, int attempts) {}

  /** What the service answered. */
  public record BookingResult(
      boolean externalServiceAvailable,
      boolean treatmentSlotAvailable,
      String appointmentReference,
      String treatmentAppointmentDate,
      String bookingStatus,
      int treatmentRetryCount,
      boolean duplicateAttempt) {}

  /**
   * @param bookingKey identifies the treatment appointment
   * @param requestedOutcome an explicit outcome from the scenario data, or null
   */
  public BookingResult bookTreatment(String bookingKey, String requestedOutcome, Instant now) {
    awaitLatency();

    synchronized (bookings) {
      final String outcome = firstSet(requestedOutcome, config.outcome(), AVAILABLE);
      final Booking existing = bookings.get(bookingKey);
      final int attempts = (existing == null ? 0 : existing.attempts()) + 1;

      if (UNAVAILABLE.equals(outcome)) {
        // Nothing new is booked, but the attempt is recorded so the process can show that further
        // attempts were made and notify the responsible team. If an appointment was already made
        // for this booking key it still stands: a later outage cannot unbook it or create a second
        // one.
        final String reference = existing == null ? null : existing.appointmentReference();
        final String date = existing == null ? null : existing.treatmentAppointmentDate();
        bookings.put(bookingKey, new Booking(reference, date, attempts));

        final boolean alreadyBooked = reference != null;
        return new BookingResult(
            false,
            alreadyBooked,
            reference,
            date,
            alreadyBooked ? "confirmed" : "pending",
            attempts,
            alreadyBooked);
      }

      if (existing != null && existing.appointmentReference() != null) {
        bookings.put(
            bookingKey,
            new Booking(existing.appointmentReference(), existing.treatmentAppointmentDate(), attempts));
        return new BookingResult(
            true,
            true,
            existing.appointmentReference(),
            existing.treatmentAppointmentDate(),
            "confirmed",
            attempts,
            true);
      }

      final String appointmentReference = "TRT-" + bookingKey;
      final String treatmentAppointmentDate = Validate.toIsoDate(now);
      bookings.put(bookingKey, new Booking(appointmentReference, treatmentAppointmentDate, attempts));

      return new BookingResult(
          true, true, appointmentReference, treatmentAppointmentDate, "confirmed", attempts, false);
    }
  }
}
