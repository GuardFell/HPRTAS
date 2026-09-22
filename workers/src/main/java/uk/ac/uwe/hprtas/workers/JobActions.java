package uk.ac.uwe.hprtas.workers;

import java.time.Duration;
import java.util.Map;

/**
 * The three things a worker can tell the broker about a job.
 *
 * This is the seam between the business logic and the client. A handler decides what happened and
 * says so in these terms; how that becomes a gRPC command to the gateway is
 * {@link ClientJobActions}' business and nothing else's. It is what lets the job contract - "a
 * completed outcome completes the job, a broken rule throws a BPMN error, a defect fails the job" -
 * be tested without a broker and without standing in for the client's command builders.
 */
public interface JobActions {

  /** The job succeeded. The variables are merged into the process variables. */
  void complete(Map<String, Object> variables);

  /** The job ended on a business rule. The model's error catch event for the code takes over. */
  void throwError(String errorCode, String errorMessage, Map<String, Object> variables);

  /**
   * The job failed. The process does not move on.
   *
   * @param retries how many retries to leave the job, or null to consume one of the model's own
   * @param retryBackOff how long to wait before the job may be activated again, or null for none
   * @param variables variables to record with the failure, or null for none
   */
  void fail(
      String errorMessage, Integer retries, Duration retryBackOff, Map<String, Object> variables);
}
