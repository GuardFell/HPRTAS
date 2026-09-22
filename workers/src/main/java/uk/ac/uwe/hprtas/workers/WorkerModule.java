package uk.ac.uwe.hprtas.workers;

import java.util.Map;

/**
 * One external worker: the job type it serves, and what it does with a job of that type.
 *
 * A handler does not touch the job object. It is given the process variables and the context, and it
 * returns an {@link Outcome} - which is what makes the business logic testable without a broker, and
 * what makes it impossible for a handler to return something the job contract cannot carry.
 */
public interface WorkerModule {

  /** The worker's name, which is what the log and the configuration key it by. */
  String name();

  /**
   * The job type it serves. This has to match the {@code <zeebe:taskDefinition type="...">} of the
   * service task it is bound to; it is the only thing that connects a worker to a model.
   */
  String taskType();

  Outcome handle(Map<String, Object> variables, WorkerContext context);
}
