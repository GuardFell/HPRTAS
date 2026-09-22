package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.services.TreatmentService;

import java.util.List;
import java.util.Map;

/**
 * Worker: treatment-availability   (job type {@code check-treatment-availability})
 *
 * Books the treatment appointment through the external treatment, laboratory and imaging service,
 * after the funding and payment gate.
 *
 * Three things from the case are implemented here:
 *
 * <ul>
 *   <li>An unauthorised Treatment Booking Request is refused. The request must have been completed
 *       and authorised by a clinical professional before administrative staff may process it (BR-04,
 *       FR-014, TC-06, AC-02).</li>
 *   <li>A service that is temporarily unavailable leaves the booking pending, with the attempt
 *       recorded and no duplicate appointment (EX-07, FR-016, TC-12, AC-10).</li>
 *   <li>Further attempts for the same booking return the appointment already made, so retrying
 *       cannot create a second one.</li>
 * </ul>
 */
public final class TreatmentAvailability implements WorkerModule {

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "proposedTreatment", FieldSpec.identifier().required(),
          "treatmentStartDate", FieldSpec.identifier().required(),
          "specialResources", FieldSpec.list().defaultTo(List.of()),
          // Written by the treatment authorisation form. Administrative staff cannot supply it,
          // which is the point of the check (BR-04, AS-06).
          "clinicalAuthorised", FieldSpec.bool().required(),
          "treatmentOutcome",
              FieldSpec.string()
                  .oneOf(TreatmentService.AVAILABLE, TreatmentService.UNAVAILABLE));

  @Override
  public String name() {
    return "treatment-availability";
  }

  @Override
  public String taskType() {
    return "check-treatment-availability";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the treatment booking cannot be processed because the request is incomplete or unusable: "
              + check.summarise(),
          Vars.of("bookingStatus", "not_processed"));
    }

    if (!Boolean.TRUE.equals(check.values().get("clinicalAuthorised"))) {
      return Outcome.businessError(
          ErrorCode.UNAUTHORISED_BOOKING_REQUEST,
          "the Treatment Booking Request has not been authorised by a clinical professional, so it"
              + " cannot be processed",
          Vars.of("bookingStatus", "not_processed", "treatmentSlotAvailable", false));
    }

    final String bookingKey = context.instanceKey() + ":treatment-appointment";
    final TreatmentService.BookingResult result =
        context
            .services()
            .treatment()
            .bookTreatment(
                bookingKey, (String) check.values().get("treatmentOutcome"), context.now());

    context
        .log()
        .info(
            "treatment booking attempted",
            Vars.of(
                "bookingStatus", result.bookingStatus(),
                "externalServiceAvailable", result.externalServiceAvailable(),
                "appointmentReference", result.appointmentReference(),
                "treatmentRetryCount", result.treatmentRetryCount(),
                "duplicateAttempt", result.duplicateAttempt()));

    return Outcome.completed(
        Vars.of(
            "externalServiceAvailable", result.externalServiceAvailable(),
            "treatmentSlotAvailable", result.treatmentSlotAvailable(),
            "appointmentReference", result.appointmentReference(),
            "treatmentAppointmentDate", result.treatmentAppointmentDate(),
            "bookingStatus", result.bookingStatus(),
            "treatmentRetryCount", result.treatmentRetryCount(),
            "duplicateAttempt", result.duplicateAttempt(),
            "proposedTreatment", check.values().get("proposedTreatment"),
            "treatmentStartDate", check.values().get("treatmentStartDate"),
            "specialResources", Validate.asList(check.values().get("specialResources"))));
  }
}
