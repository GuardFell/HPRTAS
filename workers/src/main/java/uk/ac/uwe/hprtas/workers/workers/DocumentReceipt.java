package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;
import uk.ac.uwe.hprtas.workers.db.Database;

import java.util.Map;
import java.util.Optional;

/**
 * Worker: document-receipt   (job type {@code record-document-receipt})
 *
 * Records that the documentation requested from the referring organisation has arrived.
 *
 * This is the updating half of the missing-information exchange. The request was recorded as
 * outstanding when it went out, and the point at which the reply arrives - the message the referring
 * organisation sends - is the point at which that record changes. Nothing else in the model is in a
 * position to make the change: the reply is an external message and the task that follows it is a
 * person checking what came back, not the thing that received it.
 *
 * It is deliberately simple. It reads the referral the instance is waiting on, marks that referral's
 * information request supplied, and writes back what it recorded. It does not decide whether the
 * documentation is complete - that is the check the Medical Secretaries make next, and the
 * administrative rule that they may not assess clinical suitability applies to a receipt as much as
 * to anything else.
 */
public final class DocumentReceipt implements WorkerModule {

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order("referralId", FieldSpec.identifier().required());

  @Override
  public String name() {
    return "document-receipt";
  }

  @Override
  public String taskType() {
    return "record-document-receipt";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the receipt cannot be recorded because it names no referral: " + check.summarise(),
          Vars.of("informationRequestStatus", "not_recorded"));
    }

    final String referralId = (String) check.values().get("referralId");
    final Database database = context.services().database();
    final Optional<Database.InformationRequest> request =
        database.findInformationRequest(referralId);
    final int updated = database.markDocumentsSupplied(referralId, context.now());

    // A receipt for a referral with no outstanding request is recorded as such rather than refused:
    // the documentation really did arrive, and which request it answers is a person's to say. The
    // status is what distinguishes the two cases for whoever reads the log.
    final String status = updated > 0 ? "supplied" : "no_outstanding_request";

    context
        .log()
        .info(
            "supplied documentation recorded",
            Vars.of(
                "referralId", referralId,
                "informationRequestStatus", status,
                "requestedItems",
                request.map(Database.InformationRequest::requestedItems).orElse(null)));

    return Outcome.completed(
        Vars.of(
            "informationRequestStatus", status,
            "documentsReceivedDate", Validate.toIsoDate(context.now())));
  }
}
