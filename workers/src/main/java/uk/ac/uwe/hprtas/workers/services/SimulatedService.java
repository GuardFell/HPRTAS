package uk.ac.uwe.hprtas.workers.services;

/**
 * What every simulated external service has in common.
 *
 * The hospital has no interface to the real systems, so the workers call simulations. A real call
 * takes time, and the latency is part of what is being simulated: it is what makes a job take long
 * enough for the job deadline, the retries and the worker's concurrency to mean anything. The
 * configured latency is per service, and the tests set it to zero so the suite stays fast.
 */
abstract class SimulatedService {

  private final long latencyMs;

  protected SimulatedService(long latencyMs) {
    this.latencyMs = latencyMs;
  }

  /** Waits for the service to answer, which is what makes the call take the time a real one would. */
  protected final void awaitLatency() {
    if (latencyMs <= 0) {
      return;
    }
    try {
      Thread.sleep(latencyMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for the simulated service", e);
    }
  }

  /**
   * The first of the candidates that is set.
   *
   * This is how the outcome is chosen: the scenario data first, then the configured outcome, then
   * the service's own default. A test run is therefore reproducible from its test data, and a
   * demonstrator can still force one outcome for a whole session without editing anything.
   */
  protected static String firstSet(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isEmpty()) {
        return candidate;
      }
    }
    return null;
  }
}
