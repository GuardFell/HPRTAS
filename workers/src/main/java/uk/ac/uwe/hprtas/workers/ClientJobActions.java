package uk.ac.uwe.hprtas.workers;

import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import java.time.Duration;
import java.util.Map;

/** Turns what a handler decided into the commands the gateway understands. */
final class ClientJobActions implements JobActions {

  private final JobClient jobClient;
  private final ActivatedJob job;

  ClientJobActions(JobClient jobClient, ActivatedJob job) {
    this.jobClient = jobClient;
    this.job = job;
  }

  @Override
  public void complete(Map<String, Object> variables) {
    jobClient.newCompleteCommand(job).variables(variables).send().join();
  }

  @Override
  public void throwError(String errorCode, String errorMessage, Map<String, Object> variables) {
    jobClient
        .newThrowErrorCommand(job)
        .errorCode(errorCode)
        .errorMessage(errorMessage)
        .variables(variables)
        .send()
        .join();
  }

  @Override
  public void fail(
      String errorMessage, Integer retries, Duration retryBackOff, Map<String, Object> variables) {

    // The client has no way to leave retries unset, and an unset retries value reaches the broker
    // as zero, which raises an incident at once. One fewer than the job currently has is what
    // consumes the model's own retry count and leaves an incident for when that count is spent,
    // which is what the worker README means by "the job is retried according to the model's
    // retries".
    final int effectiveRetries =
        retries != null ? retries : Math.max(job.getRetries() - 1, 0);

    FailJobCommandStep1.FailJobCommandStep2 command =
        jobClient.newFailCommand(job).retries(effectiveRetries).errorMessage(errorMessage);

    if (retryBackOff != null) {
      command = command.retryBackoff(retryBackOff);
    }
    if (variables != null) {
      command = command.variables(variables);
    }
    command.send().join();
  }
}
