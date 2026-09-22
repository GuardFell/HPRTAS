package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;

/**
 * The simulated external services, built once and shared by every worker.
 *
 * They are constructed from the {@code simulatedServices} section of the configuration, so the whole
 * set can be pointed at a different outcome without touching the code.
 *
 * The ledgers inside them are in memory, so they are per worker process and are cleared when it
 * restarts. That is deliberate and is recorded as a limitation in {@code workers/README.md}: what the
 * simulations are for is making "no duplicate appointment", "no second charge" and "no second refund"
 * testable within a run.
 */
public final class Services {

  private final SchedulingService scheduling;
  private final TreatmentService treatment;
  private final PaymentServiceProvider payment;
  private final CorrespondenceService correspondence;

  public Services(
      SchedulingService scheduling,
      TreatmentService treatment,
      PaymentServiceProvider payment,
      CorrespondenceService correspondence) {
    this.scheduling = scheduling;
    this.treatment = treatment;
    this.payment = payment;
    this.correspondence = correspondence;
  }

  public static Services create(Config config) {
    final Config.SimulatedServices simulated = config.simulatedServices();
    return new Services(
        new SchedulingService(simulated.scheduling()),
        new TreatmentService(simulated.treatment()),
        new PaymentServiceProvider(simulated.payment()),
        new CorrespondenceService(simulated.correspondence()));
  }

  public SchedulingService scheduling() {
    return scheduling;
  }

  public TreatmentService treatment() {
    return treatment;
  }

  public PaymentServiceProvider payment() {
    return payment;
  }

  public CorrespondenceService correspondence() {
    return correspondence;
  }
}
