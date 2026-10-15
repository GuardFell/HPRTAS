package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.services.SchedulingService;

import java.util.Map;

/**
 * Worker: appointment-availability   (job type {@code check-appointment-availability})
 *
 * Asks the external scheduling service for a new patient appointment and reports what came back, so
 * the model can decide between booking, highlighting the case, and recording the telephone contact
 * the two-week rule requires (FR-007, BR-03, BR-17, EX-06, TC-01, TC-04, TC-05, TC-18).
 *
 * The appointment is recorded on the hospital's register through the simulated service, against the
 * patient and referral it is for. A booking request for the same patient and referral that asks for a
 * different window is a new booking, and the appointment the earlier request made is marked
 * superseded rather than being left on the register as though it were still going to happen.
 *
 * The simulated service returns three outcomes; see {@link SchedulingService}. The scenario decides
 * which one, through the optional {@code schedulingOutcome} variable, so a test can be reproduced
 * from its test data.
 */
public final class AppointmentAvailability implements WorkerModule {

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "speciality", FieldSpec.identifier().required(),
          "priority", FieldSpec.string().required().oneOf("urgent", "routine"),
          "requestedWindow", FieldSpec.days().required(),
          // Which patient and referral the appointment is for, so the register can record it against
          // them and a rebooking can supersede what it replaces. Optional: a fixture that drives the
          // worker without a pathway around it still works.
          "patientId", FieldSpec.identifier(),
          "referralId", FieldSpec.identifier(),
          // Supplied by the form as a hint. The value that is written back is always recomputed from
          // the slot actually found, because that is what the two-week contact rule applies to.
          "appointmentWithinTwoWeeks", FieldSpec.bool(),
          "schedulingOutcome",
              FieldSpec.string()
                  .oneOf(
                      SchedulingService.AVAILABLE,
                      SchedulingService.OUTSIDE_WINDOW,
                      SchedulingService.NONE));

  @Override
  public String name() {
    return "appointment-availability";
  }

  @Override
  public String taskType() {
    return "check-appointment-availability";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "appointment availability cannot be checked because the booking request is incomplete or"
              + " unusable: "
              + check.summarise(),
          Vars.of("slotAvailable", false));
    }

    final String patientId = (String) check.values().get("patientId");
    final String referralId = (String) check.values().get("referralId");
    final int requestedWindow = ((Number) check.values().get("requestedWindow")).intValue();

    // The key names the request: the instance and the window that was asked for. A retry of the same
    // request returns the appointment it made; a request for a different window is a different
    // booking, which is what lets a patient asking for another date get one.
    final String bookingKey =
        context.instanceKey() + ":patient-appointment:" + requestedWindow;

    final SchedulingService.Appointment result =
        context
            .services()
            .scheduling()
            .findAppointment(
                bookingKey,
                (String) check.values().get("schedulingOutcome"),
                (String) check.values().get("priority"),
                requestedWindow,
                context.now(),
                patientId,
                referralId,
                SchedulingService.PATIENT_APPOINTMENT);

    final boolean withinTwoWeeks =
        SchedulingService.appointmentWithinTwoWeeks(
            result.appointmentDate(),
            context.now(),
            context.config().businessCalendar().urgentTelephoneContactDays());

    context
        .log()
        .info(
            "appointment availability checked",
            Vars.of(
                "appointmentOutcome", result.outcome(),
                "slotAvailable", result.slotAvailable(),
                "appointmentDate", result.appointmentDate(),
                "duplicateRequest", result.duplicateRequest(),
                "supersededAppointment", result.supersededAppointment()));

    return Outcome.completed(
        Vars.of(
            "slotAvailable", result.slotAvailable(),
            "appointmentDate", result.appointmentDate(),
            "alternativeDate", result.alternativeDate(),
            "withinRequestedWindow", result.withinRequestedWindow(),
            "appointmentWithinTwoWeeks", withinTwoWeeks,
            "schedulingServiceOutcome", result.outcome(),
            "supersededAppointment", result.supersededAppointment()));
  }
}
