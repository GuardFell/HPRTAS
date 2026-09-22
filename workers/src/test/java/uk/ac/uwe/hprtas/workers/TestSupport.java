package uk.ac.uwe.hprtas.workers;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import uk.ac.uwe.hprtas.workers.services.CorrespondenceService;
import uk.ac.uwe.hprtas.workers.services.PaymentServiceProvider;
import uk.ac.uwe.hprtas.workers.services.SchedulingService;
import uk.ac.uwe.hprtas.workers.services.Services;
import uk.ac.uwe.hprtas.workers.services.TreatmentService;

/**
 * Shared test helpers.
 *
 * The tests exercise the worker handlers without a broker: a handler takes variables and returns an
 * outcome, which is the same code path the worker runs in production. The simulated services run for
 * real, with the artificial latency switched off so the suite stays fast.
 */
public final class TestSupport {

  private static final Path DEFAULTS = Path.of("config", "workers.default.json");

  /** The moment the tests fix their clock to, so the dates in the assertions do not move. */
  public static final Instant NOW = Instant.parse("2026-09-16T09:00:00Z");

  public static final long INSTANCE_KEY = 2251799813685249L;

  private TestSupport() {}

  /** The committed defaults, which is what a test starts from. */
  public static Map<String, Object> defaults() {
    try {
      return Json.mapper()
          .readValue(
              Files.readString(DEFAULTS, StandardCharsets.UTF_8),
              new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("the committed defaults could not be read: " + DEFAULTS, e);
    }
  }

  /** A configuration built from the committed defaults, with no artificial latency. */
  public static Config buildConfig() {
    return buildConfig(Map.of());
  }

  /** The same, with some of the configuration replaced. */
  @SuppressWarnings("unchecked")
  public static Config buildConfig(Map<String, Object> overrides) {
    final Map<String, Object> tree = merge(defaults(), overrides);
    for (Object service : ((Map<String, Object>) tree.get("simulatedServices")).values()) {
      ((Map<String, Object>) service).put("latencyMs", 0);
    }
    return Config.fromTree(tree, Path.of("").toAbsolutePath());
  }

  public static JsonLogger silentLogger() {
    return JsonLogger.silent();
  }

  /** A context of the same shape the worker builds from a real job. */
  public static WorkerContext buildContext(Config config) {
    return buildContext(config, NOW, INSTANCE_KEY);
  }

  public static WorkerContext buildContext(Config config, Instant now, long instanceKey) {
    return new WorkerContext(now, instanceKey, Services.create(config), config, silentLogger());
  }

  /** A context whose payment provider is the one given, so a test can watch what it is asked to do. */
  public static WorkerContext buildContext(Config config, CountingPaymentProvider provider) {
    final Config.SimulatedServices simulated = config.simulatedServices();
    final Services services =
        new Services(
            new SchedulingService(simulated.scheduling()),
            new TreatmentService(simulated.treatment()),
            provider,
            new CorrespondenceService(simulated.correspondence()));
    return new WorkerContext(NOW, INSTANCE_KEY, services, config, silentLogger());
  }

  /**
   * A payment provider that records whether the hospital asked it to move any money.
   *
   * The rule it exists to check is that the provider is not called at all - not that it answers
   * something particular. Its ledger is its own, which does not matter for that assertion.
   */
  public static final class CountingPaymentProvider extends PaymentServiceProvider {

    private final AtomicBoolean paymentRequested = new AtomicBoolean();
    private final AtomicBoolean refundRequested = new AtomicBoolean();

    public CountingPaymentProvider(Config.PaymentSettings settings) {
      super(settings);
    }

    public boolean wasAskedToTakePayment() {
      return paymentRequested.get();
    }

    public boolean wasAskedToRefund() {
      return refundRequested.get();
    }

    @Override
    public PaymentResult requestPayment(
        String paymentReference, Number chargeAmount, String requestedOutcome, Instant now) {
      paymentRequested.set(true);
      return super.requestPayment(paymentReference, chargeAmount, requestedOutcome, now);
    }

    @Override
    public RefundResult refundPayment(
        String paymentReference,
        Number refundAmount,
        String refundReason,
        String refundDecision,
        Instant now) {
      refundRequested.set(true);
      return super.refundPayment(
          paymentReference, refundAmount, refundReason, refundDecision, now);
    }
  }

  /** Records which job action a worker chose, as the broker would see it. */
  public static final class RecordingActions implements JobActions {

    public String kind;
    public Map<String, Object> variables;
    public String errorCode;
    public String errorMessage;
    public Integer retries;
    public Duration retryBackOff;

    @Override
    public void complete(Map<String, Object> variables) {
      this.kind = "complete";
      this.variables = variables;
    }

    @Override
    public void throwError(String errorCode, String errorMessage, Map<String, Object> variables) {
      this.kind = "error";
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
      this.variables = variables;
    }

    @Override
    public void fail(
        String errorMessage, Integer retries, Duration retryBackOff, Map<String, Object> variables) {
      this.kind = "fail";
      this.errorMessage = errorMessage;
      this.retries = retries;
      this.retryBackOff = retryBackOff;
      this.variables = variables;
    }
  }

  /** A job as the handler wrapper sees it. */
  public static Main.JobFacts buildJob(Map<String, Object> variables) {
    return new Main.JobFacts(
        4503599627370497L, INSTANCE_KEY, "ServiceTask_test", 3, variables);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> merge(
      Map<String, Object> base, Map<String, Object> override) {
    final Map<String, Object> result = new LinkedHashMap<>(base);
    for (Map.Entry<String, Object> entry : override.entrySet()) {
      final Object value = entry.getValue();
      final Object previous = result.get(entry.getKey());
      final boolean bothObjects = value instanceof Map && previous instanceof Map;
      result.put(
          entry.getKey(),
          bothObjects
              ? merge((Map<String, Object>) previous, (Map<String, Object>) value)
              : value);
    }
    return result;
  }

  /** Convenience for asserting on a list outcome. */
  public static List<Object> listOf(Object... items) {
    return Vars.list(items);
  }
}
