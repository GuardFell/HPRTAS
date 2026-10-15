package uk.ac.uwe.hprtas.workers.services;

import uk.ac.uwe.hprtas.workers.Config;
import uk.ac.uwe.hprtas.workers.db.Database;

/**
 * The simulated external services and the record store, built once and shared by every worker.
 *
 * They are constructed from the {@code simulatedServices} and {@code database} sections of the
 * configuration, so the whole set can be pointed at a different outcome - or at a throwaway store -
 * without touching the code.
 *
 * The simulated services keep their ledgers in the hospital's store ({@link Database}) rather than in
 * their own memory, so what they have recorded survives the worker process: an appointment, a
 * payment or a refund made before a restart is still known after it.
 */
public final class Services {

  private final Database database;
  private final SchedulingService scheduling;
  private final TreatmentService treatment;
  private final PaymentServiceProvider payment;
  private final CorrespondenceService correspondence;

  public Services(
      Database database,
      SchedulingService scheduling,
      TreatmentService treatment,
      PaymentServiceProvider payment,
      CorrespondenceService correspondence) {
    this.database = database;
    this.scheduling = scheduling;
    this.treatment = treatment;
    this.payment = payment;
    this.correspondence = correspondence;
  }

  public static Services create(Config config) {
    final Config.SimulatedServices simulated = config.simulatedServices();
    final Database database = Database.open(config.database());
    return new Services(
        database,
        new SchedulingService(simulated.scheduling(), database),
        new TreatmentService(simulated.treatment(), database),
        new PaymentServiceProvider(simulated.payment(), database),
        new CorrespondenceService(simulated.correspondence(), database));
  }

  /** The hospital's own records: the register, the referrals, the appointments and the ledgers. */
  public Database database() {
    return database;
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
