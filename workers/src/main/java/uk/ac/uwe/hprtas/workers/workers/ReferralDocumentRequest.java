package uk.ac.uwe.hprtas.workers.workers;

import uk.ac.uwe.hprtas.workers.ErrorCode;
import uk.ac.uwe.hprtas.workers.FieldSpec;
import uk.ac.uwe.hprtas.workers.Outcome;
import uk.ac.uwe.hprtas.workers.Validate;
import uk.ac.uwe.hprtas.workers.Vars;
import uk.ac.uwe.hprtas.workers.WorkerContext;
import uk.ac.uwe.hprtas.workers.WorkerModule;

import java.util.List;
import java.util.Map;

/**
 * Worker: referral-document-request   (job type {@code request-referral-documents})
 *
 * Asks the referring organisation for the documentation a referral is missing, by publishing a BPMN
 * message rather than by calling a service: the referring organisation is a separate participant, not
 * a system the hospital calls, and the referral waits for what it sends back.
 *
 * This is the sending half of the exchange {@code core-5-missing-information-message-exchange}
 * models, and what that model's waiting step depends on is the value this worker correlates on:
 *
 * <ul>
 *   <li>the message is named {@code missing-information-requested}</li>
 *   <li>the correlation key is {@code referralId}, which is the value the receiving side names in its
 *       subscription, so the answer reaches the referral it belongs to and not another patient's
 *       (NFR-004)</li>
 *   <li>the publication is made idempotent with an id derived from the job key, so a job the broker
 *       retries after a publication it could not confirm sends one message rather than two</li>
 * </ul>
 *
 * The request is administrative: the Medical Secretaries record what is missing and ask for it, and
 * they do not assess the patient's clinical suitability (BR-02). The worker therefore reads and
 * returns documentation items only, and adds to the process nothing the request form already
 * recorded except the two things only it can know - that the request went out, and the key the engine
 * recorded it under.
 */
public final class ReferralDocumentRequest implements WorkerModule {

  /** The message the referring organisation receives, and the name its subscription waits on. */
  public static final String MESSAGE_NAME = "missing-information-requested";

  public static final Map<String, FieldSpec> INPUT_VARIABLES =
      FieldSpec.order(
          "referralId", FieldSpec.identifier().required(),
          "requestedFrom", FieldSpec.identifier().required(),
          // A request that names nothing cannot be answered, so a required list is also a non-empty
          // one: Validate reports an empty list as a problem rather than sending it (FR-003, EX-01).
          "requestedItems", FieldSpec.list().required().max(20));

  @Override
  public String name() {
    return "referral-document-request";
  }

  @Override
  public String taskType() {
    return "request-referral-documents";
  }

  @Override
  public Outcome handle(Map<String, Object> variables, WorkerContext context) {
    final Validate.CheckResult check = Validate.checkVariables(variables, INPUT_VARIABLES);

    if (check.hasProblems()) {
      return Outcome.businessError(
          ErrorCode.INVALID_VARIABLE,
          "the request for missing information cannot be sent because it is incomplete or unusable: "
              + check.summarise(),
          Vars.of("informationRequestStatus", "not_sent"));
    }

    final String referralId = (String) check.values().get("referralId");
    final String requestedFrom = (String) check.values().get("requestedFrom");
    final List<Object> requestedItems = Validate.asList(check.values().get("requestedItems"));

    // A publication that fails throws, and the handler wrapper in Main turns that into a failed job
    // with one fewer retry. That is deliberate: a message the hospital could not send is not a
    // business rule being broken, so it must not travel a modelled error path as if the request had
    // been refused.
    final long messageKey =
        context
            .messages()
            .publish(
                MESSAGE_NAME,
                referralId,
                messageIdFor(referralId, context.jobKey()),
                Vars.of(
                    "referralId", referralId,
                    "requestedItems", requestedItems,
                    "requestedFrom", requestedFrom));

    context
        .log()
        .info(
            "request for missing information published",
            Vars.of(
                "messageName", MESSAGE_NAME,
                "correlationKey", referralId,
                "messageKey", String.valueOf(messageKey),
                "itemCount", requestedItems.size()));

    return Outcome.completed(
        Vars.of(
            "informationRequestStatus", "sent",
            "informationRequestMessageKey", String.valueOf(messageKey)));
  }

  /**
   * The id the publication is made idempotent with.
   *
   * The job key is part of it because it is what a retry keeps: the same job publishing twice is one
   * publication, while a genuinely new job - the same referral chased a second time after the
   * referring organisation answered only part of the request - publishes again.
   */
  public static String messageIdFor(String referralId, long jobKey) {
    return MESSAGE_NAME + ":" + referralId + ":" + jobKey;
  }
}
