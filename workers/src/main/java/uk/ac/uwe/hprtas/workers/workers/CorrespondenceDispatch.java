package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.services.CorrespondenceService;

import java.util.List;
import java.util.Map;

/**
 * Worker: correspondence-dispatch   (job type {@code send-correspondence})
 *
 * Sends appointment letters and clinic letters through the external correspondence service and
 * records the dispatch date, the channel and a reference, which is what the case asks the system to
 * keep (FR-008, FR-037).
 *
 * The patient's channel preference is used where it is recorded and defaults to letter by post where
 * it is not (NFR-012, AS-12). The two-week telephone contact is a safety net that applies whatever
 * the preference is, so it is not decided here; the model handles it from
 * {@code appointmentWithinTwoWeeks}.
 */
public final class CorrespondenceDispatch implements WorkerModule {

  public static final List<String> DOCUMENT_TYPES =
      List.of(
          "new_patient_clinic_letter",
          "follow_up_clinic_letter",
          "appointment_letter",
          "referral_outcome_letter",
          "deferral_or_cancellation_letter");

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "recipients", FieldSpec.list().required().max(20),
          "channelPreference", FieldSpec.string().oneOf(CorrespondenceService.CHANNELS.toArray(new String[0])),
          "documentType",
              FieldSpec.string().required().oneOf(DOCUMENT_TYPES.toArray(new String[0])),
          "correspondenceOutcome",
              FieldSpec.string().oneOf(CorrespondenceService.DISPATCHED));

  @Override
  public String name() {
    return "correspondence-dispatch";
  }

  @Override
  public String taskType() {
    return "send-correspondence";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the correspondence cannot be dispatched because the request is incomplete or unusable: "
              + check.summarise(),
          Vars.of("dispatchChannel", null));
    }

    final List<Object> recipients = Validate.asList(check.values().get("recipients"));
    final String documentType = (String) check.values().get("documentType");

    final CorrespondenceService.DispatchResult result =
        context
            .services()
            .correspondence()
            .dispatch(
                recipients, (String) check.values().get("channelPreference"), documentType, context.now());

    context
        .log()
        .info(
            "correspondence dispatched",
            Vars.of(
                "documentType", documentType,
                "channel", result.dispatchChannel(),
                "recipientCount", recipients.size(),
                "dispatchReference", result.dispatchReference()));

    return Outcome.completed(
        Vars.of(
            "dispatchDate", result.dispatchDate(),
            "dispatchChannel", result.dispatchChannel(),
            "dispatchReference", result.dispatchReference(),
            "documentType", documentType,
            "recipients", recipients));
  }
}
