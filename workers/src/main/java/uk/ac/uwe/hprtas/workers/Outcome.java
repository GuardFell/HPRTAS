package uk.ac.uwe.hprtas.workers;

import java.time.Duration;
import java.util.Map;

/**
 * Job outcomes.
 *
 * A worker handler does not touch the job object directly. It returns one of the outcomes below,
 * which keeps the business logic testable on its own and keeps the choice of job action in one
 * place. The three outcomes map onto the three ways a job can end in Camunda 8:
 *
 * <ul>
 *   <li>{@link Completed} - the work finished and the process continues</li>
 *   <li>{@link BusinessError} - a rule was broken, so the process takes its modelled BPMN error
 *       path (an error catch event). If the model has no catch event for the code, Zeebe raises an
 *       incident, which is still a recorded, visible outcome.</li>
 *   <li>{@link TechnicalFailure} - something outside the business rules went wrong; the job is
 *       failed so the broker retries it rather than leaving it stalled</li>
 * </ul>
 *
 * The interface is sealed, so {@link #applyOutcome} is checked at compile time for every outcome a
 * handler can return.
 */
public sealed interface Outcome
    permits Outcome.Completed, Outcome.BusinessError, Outcome.TechnicalFailure {

  /** What the job log records as {@code outcome}. */
  String kind();

  /** The job succeeded. {@code variables} are merged into the process variables. */
  record Completed(Map<String, Object> variables) implements Outcome {

    public Completed {
      variables = Vars.copy(variables);
    }

    @Override
    public String kind() {
      return "completed";
    }
  }

  /**
   * A business rule or the input data stopped the work.
   *
   * The variables are written before the error is thrown. A model has to distinguish the reason for
   * a failure by which catch event was taken rather than by reading one of these variables: the
   * variables attached to a BPMN error are written to the scope of the activity that raised it and
   * are no longer visible in the process scope once the token has moved on.
   */
  record BusinessError(String errorCode, String errorMessage, Map<String, Object> variables)
      implements Outcome {

    public BusinessError {
      variables = Vars.copy(variables);
    }

    @Override
    public String kind() {
      return "businessError";
    }
  }

  /** An unexpected condition. The job is failed; the process does not move on. */
  record TechnicalFailure(
      String errorMessage, Integer retries, Duration retryBackOff, Map<String, Object> variables)
      implements Outcome {

    public TechnicalFailure {
      variables = variables == null ? null : Vars.copy(variables);
    }

    @Override
    public String kind() {
      return "technicalFailure";
    }
  }

  static Completed completed(Map<String, Object> variables) {
    return new Completed(variables);
  }

  static BusinessError businessError(
      ErrorCode errorCode, String errorMessage, Map<String, Object> variables) {
    return new BusinessError(errorCode.name(), errorMessage, variables);
  }

  static TechnicalFailure technicalFailure(String errorMessage) {
    return new TechnicalFailure(errorMessage, null, null, null);
  }

  /** Turns an outcome into the job action the broker is given. */
  static void applyOutcome(JobActions actions, Outcome outcome) {
    switch (outcome) {
      case Completed completed -> actions.complete(completed.variables());

      case BusinessError error ->
          actions.throwError(error.errorCode(), error.errorMessage(), error.variables());

      case TechnicalFailure failure ->
          actions.fail(
              failure.errorMessage(),
              failure.retries(),
              failure.retryBackOff(),
              failure.variables());
    }
  }
}
