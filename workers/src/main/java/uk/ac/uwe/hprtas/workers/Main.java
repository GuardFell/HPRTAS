package uk.ac.uwe.hprtas.workers;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import uk.ac.uwe.hprtas.workers.services.Services;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * External workers for the Hospital Patient Referral, Treatment and Administration System.
 *
 * Run them with {@code mvn exec:java}. They connect to the Zeebe gRPC gateway, register one worker
 * per service task in the operational models, and log every job with the process instance and the
 * outcome so the log can be used as test evidence.
 *
 * {@code mvn exec:java -Dexec.args=--check} validates the configuration and the wiring without
 * connecting to an engine, which is useful before a demonstration.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    try {
      run(args);
    } catch (Throwable error) {
      Console.error("The workers could not start: %s", messageOf(error));
      System.exit(1);
    }
  }

  static void run(String[] args) {
    // The client logs through slf4j, and the binding writes to standard error. Error is the level
    // the Node SDK was held at: the SDK's own connection lifecycle at info buries the JSON evidence
    // the workers write, but a connection that genuinely fails still has to be visible.
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
    System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");

    final Config config = Config.load();

    for (String argument : args) {
      if ("--check".equals(argument)) {
        check(config);
        return;
      }
    }

    serve(config);
  }

  /**
   * Wraps a worker's business handler in the job contract: it must return an outcome on every path,
   * and an unexpected exception has to be reported rather than left to stall the job.
   */
  public static JobHandler createTaskHandler(
      WorkerModule workerModule, Config config, JsonLogger log, Services services) {

    return (jobClient, job) ->
        handle(
            workerModule,
            config,
            log,
            services,
            new ClientJobActions(jobClient, job),
            new JobFacts(
                job.getKey(),
                job.getProcessInstanceKey(),
                job.getElementId(),
                job.getRetries(),
                job.getVariablesAsMap()));
  }

  /** The parts of an activated job the handler needs, so the contract can be tested without one. */
  public record JobFacts(
      long key,
      long processInstanceKey,
      String elementId,
      int retries,
      Map<String, Object> variables) {}

  /** Runs one job and tells the broker how it ended. */
  static void handle(
      WorkerModule workerModule,
      Config config,
      JsonLogger log,
      Services services,
      JobActions actions,
      JobFacts job) {

    final JsonLogger workerLog =
        log.child(Vars.of("worker", workerModule.name(), "taskType", workerModule.taskType()));
    final long startedAt = System.currentTimeMillis();

    try {
      final WorkerContext context =
          new WorkerContext(Instant.now(), job.processInstanceKey(), services, config, workerLog);

      final Outcome outcome = workerModule.handle(job.variables(), context);

      if (outcome == null) {
        throw new IllegalStateException("the handler returned no job outcome");
      }

      final Map<String, Object> fields =
          Vars.of(
              "jobKey", String.valueOf(job.key()),
              "processInstanceKey", String.valueOf(job.processInstanceKey()),
              "elementId", job.elementId(),
              "durationMs", System.currentTimeMillis() - startedAt,
              "outcome", outcome.kind());

      // A job that completed has no error code, and the line does not carry the field at all rather
      // than carrying a null: an absent field and an empty one read differently in the evidence, and
      // only one of them means "there was no error".
      if (outcome instanceof Outcome.BusinessError error) {
        fields.put("errorCode", error.errorCode());
      }

      switch (outcome.kind()) {
        case "businessError" -> workerLog.warn("job ended with a business error", fields);
        case "technicalFailure" -> workerLog.error("job ended with a technical failure", fields);
        default -> workerLog.info("job completed", fields);
      }

      Outcome.applyOutcome(actions, outcome);

    } catch (Exception error) {
      // Anything reaching here is a defect or an infrastructure problem, not a business outcome.
      // The job is failed so the broker retries it.
      workerLog.error(
          "unhandled error while handling a job",
          Vars.of(
              "jobKey", String.valueOf(job.key()),
              "processInstanceKey", String.valueOf(job.processInstanceKey()),
              "elementId", job.elementId(),
              "durationMs", System.currentTimeMillis() - startedAt,
              "error", messageOf(error),
              "stack", stackTraceOf(error)));

      Outcome.applyOutcome(
          actions,
          Outcome.technicalFailure("the worker could not handle the job: " + messageOf(error)));
    }
  }

  /** Validates the configuration and prints what would be registered. */
  static void check(Config config) {
    // Built and discarded, so the wiring is exercised: a simulated service that cannot be
    // constructed from the configuration fails here rather than at the first job.
    Services.create(config);

    final Map<String, Object> connection = config.connection();

    Console.out("Configuration loaded from %s", config.workersDir());
    Console.out("  .env file present: %s", config.dotEnvLoaded());
    Console.out("  Zeebe gRPC address: %s", connection.get("ZEEBE_GRPC_ADDRESS"));
    Console.out("  authentication: %s", connection.get("CAMUNDA_AUTH_STRATEGY"));
    Console.out("  log level: %s", config.logging().level());
    Console.out("Workers that would be registered:");
    for (WorkerModule worker : Workers.ALL) {
      final Config.WorkerSettings settings = config.worker(worker.name());
      Console.out(
          "  %-24s job type %-30s concurrency %d, job timeout %d ms",
          worker.name(),
          settings.taskType(),
          settings.maxJobsToActivate(),
          settings.timeoutMs());
    }
    Console.out("Configuration and wiring are valid.");
  }

  /** Connects, registers every worker, and runs until the process is asked to stop. */
  static void serve(Config config) {
    final JsonLogger log = JsonLogger.create(config.logging().level(), config.logging().file());
    final Services services = Services.create(config);
    final Map<String, Object> connection = config.connection();

    log.info(
        "starting external workers",
        Vars.of(
            "zeebeGrpcAddress", connection.get("ZEEBE_GRPC_ADDRESS"),
            "authStrategy", connection.get("CAMUNDA_AUTH_STRATEGY"),
            "dotEnvLoaded", config.dotEnvLoaded()));

    final CamundaClient client = CamundaClients.create(config);

    // The Java client has no onReady callback, so the gateway is asked directly before anything is
    // registered. A worker that cannot reach the gateway would otherwise start, look alive, and
    // quietly take no work at all - which is the stalled job the worker README warns about.
    try {
      client.newTopologyRequest().send().join();
    } catch (RuntimeException error) {
      client.close();
      log.close();
      throw new IllegalStateException(
          "the Zeebe gateway at "
              + connection.get("ZEEBE_GRPC_ADDRESS")
              + " could not be reached ("
              + messageOf(error)
              + "). Start the engine with camunda-runtime\\start-camunda.bat and try again.",
          error);
    }

    final long processId = ProcessHandle.current().pid();
    final List<String> jobTypes = new ArrayList<>();
    final List<JobWorker> workers = new ArrayList<>();

    for (WorkerModule workerModule : Workers.ALL) {
      final Config.WorkerSettings settings = config.worker(workerModule.name());
      workers.add(
          client
              .newWorker()
              .jobType(settings.taskType())
              .handler(createTaskHandler(workerModule, config, log, services))
              .maxJobsActive(settings.maxJobsToActivate())
              .timeout(Duration.ofMillis(settings.timeoutMs()))
              .name(workerModule.name() + "-" + processId)
              .open());
      jobTypes.add(settings.taskType());
    }

    log.info(
        "workers registered", Vars.of("jobTypes", jobTypes, "processId", processId));

    Console.out(
        "%nHPRTAS external workers are running (%d workers). Press Ctrl-C to stop.",
        workers.size());
    for (String jobType : jobTypes) {
      Console.out("  %s", jobType);
    }

    final AtomicBoolean stopping = new AtomicBoolean();
    final CountDownLatch blocked = new CountDownLatch(1);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (!stopping.compareAndSet(false, true)) {
                    return;
                  }
                  // The hook cannot tell which signal raised it, so the log records that the
                  // process was asked to stop rather than naming a signal it did not see.
                  log.info("stopping workers", Vars.of("signal", "termination"));
                  for (JobWorker worker : workers) {
                    worker.close();
                  }
                  client.close();
                  log.close();
                  Console.out("%nWorkers stopped.");
                },
                "workers-shutdown"));

    try {
      blocked.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String messageOf(Throwable error) {
    final String message = error.getMessage();
    return message != null && !message.isEmpty() ? message : error.getClass().getSimpleName();
  }

  private static String stackTraceOf(Throwable error) {
    final StringWriter text = new StringWriter();
    error.printStackTrace(new PrintWriter(text));
    return text.toString();
  }
}
