/**
 * Job outcomes.
 *
 * A worker handler does not touch the SDK job object directly. It returns one
 * of the outcomes below, which keeps the business logic testable on its own and
 * keeps the choice of job action in one place.
 *
 * The three outcomes map onto the three ways a job can end in Camunda 8:
 *
 *   completed(...)        the work finished and the process continues
 *   businessError(...)    a rule was broken, so the process takes its modelled
 *                         BPMN error path (an error catch event). If the model
 *                         has no catch event for the code, Zeebe raises an
 *                         incident, which is still a recorded, visible outcome.
 *   technicalFailure(...) something outside the business rules went wrong;
 *                         the job is retried according to the model's retries
 */

const OUTCOME = {
  COMPLETED: 'completed',
  BUSINESS_ERROR: 'businessError',
  TECHNICAL_FAILURE: 'technicalFailure',
}

/** The job succeeded. `variables` are merged into the process variables. */
function completed(variables = {}) {
  return { kind: OUTCOME.COMPLETED, variables }
}

/**
 * A business rule or the input data stopped the work.
 * `variables` are written before the error is thrown so the process can see
 * what was rejected and route accordingly.
 */
function businessError(errorCode, errorMessage, variables = {}) {
  return { kind: OUTCOME.BUSINESS_ERROR, errorCode, errorMessage, variables }
}

/** An unexpected condition. The job is retried; the process does not move on. */
function technicalFailure(errorMessage, { retries, retryBackOffMs, variables } = {}) {
  return {
    kind: OUTCOME.TECHNICAL_FAILURE,
    errorMessage,
    retries,
    retryBackOffMs,
    variables,
  }
}

/** True when the value is one of the outcomes above. Used to catch handler bugs. */
function isOutcome(value) {
  return Boolean(value) && Object.values(OUTCOME).includes(value.kind)
}

/**
 * Turns an outcome into the job action the SDK expects.
 * The handler must return a job action on every code path, which is why an
 * unrecognised value is reported as a technical failure rather than ignored.
 */
function applyOutcome(job, outcome) {
  switch (outcome.kind) {
    case OUTCOME.COMPLETED:
      return job.complete(outcome.variables)
    case OUTCOME.BUSINESS_ERROR:
      return job.error({
        errorCode: outcome.errorCode,
        errorMessage: outcome.errorMessage,
        variables: outcome.variables,
      })
    case OUTCOME.TECHNICAL_FAILURE: {
      const failure = { errorMessage: outcome.errorMessage }
      if (outcome.retries !== undefined) failure.retries = outcome.retries
      if (outcome.retryBackOffMs !== undefined) failure.retryBackOff = outcome.retryBackOffMs
      if (outcome.variables !== undefined) failure.variables = outcome.variables
      return job.fail(failure)
    }
    default:
      return job.fail({
        errorMessage: `worker handler returned an unrecognised outcome: ${JSON.stringify(outcome)}`,
      })
  }
}

module.exports = {
  OUTCOME,
  completed,
  businessError,
  technicalFailure,
  isOutcome,
  applyOutcome,
}
