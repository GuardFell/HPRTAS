package uk.ac.uwe.hprtas.workers;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the external workers.
 *
 * Three layers, each overriding the one before it:
 *
 * <ol>
 *   <li>{@code config/workers.default.json} - committed, the agreed defaults</li>
 *   <li>{@code config/workers.local.json} - optional, never committed, per machine</li>
 *   <li>environment variables - the {@code .env} file or the shell</li>
 * </ol>
 *
 * The connection settings are handed to the Camunda 8 Java client under the same names the Node SDK
 * read them by, so there is one vocabulary for the connection everywhere. The client is told not to
 * read the environment itself: every setting is resolved here first, because a value that reached
 * the client from the environment would bypass {@code .env} and the two would disagree.
 */
public final class Config {

  /** A worker as the configuration describes it: which job it serves, and how it asks for work. */
  public record WorkerSettings(String taskType, int maxJobsToActivate, long timeoutMs) {}

  public record SchedulingSettings(
      String outcome,
      long latencyMs,
      int slotLeadDaysUrgent,
      int slotLeadDaysRoutine,
      int outsideWindowExtraDays) {}

  public record TreatmentSettings(String outcome, long latencyMs) {}

  public record PaymentSettings(String outcome, long latencyMs) {}

  public record CorrespondenceSettings(String outcome, long latencyMs) {}

  /** The simulated external services, each with the outcome it has been forced to and its latency. */
  public record SimulatedServices(
      SchedulingSettings scheduling,
      TreatmentSettings treatment,
      PaymentSettings payment,
      CorrespondenceSettings correspondence) {}

  /** The day counts the process uses for its deadlines. */
  public record BusinessCalendar(
      int urgentTelephoneContactDays,
      int clinicLetterDays,
      int pathwayMonitoringMonthDays,
      int higherManagementEscalationDays) {}

  public record Logging(String level, Path file) {}

  /** Forced outcomes accepted per simulated service: the environment name, and the service it sets. */
  private static final Map<String, String> SERVICE_ENV =
      Map.of(
          "HPRTAS_SIM_SCHEDULING", "scheduling",
          "HPRTAS_SIM_TREATMENT", "treatment",
          "HPRTAS_SIM_PAYMENT", "payment",
          "HPRTAS_SIM_CORRESPONDENCE", "correspondence");

  private static final List<String> LOG_LEVELS = List.of("error", "warn", "info", "debug");

  private static final String DEFAULT_LOG_LEVEL = "info";

  private final Map<String, Object> tree;
  private final Map<String, String> dotEnv;
  private final boolean dotEnvLoaded;
  private final Path workersDir;
  private final Logging logging;
  private final Map<String, WorkerSettings> workers;
  private final SimulatedServices simulatedServices;
  private final BusinessCalendar businessCalendar;

  private Config(
      Map<String, Object> tree,
      Map<String, String> dotEnv,
      boolean dotEnvLoaded,
      Path workersDir,
      Logging logging,
      Map<String, WorkerSettings> workers,
      SimulatedServices simulatedServices,
      BusinessCalendar businessCalendar) {
    this.tree = tree;
    this.dotEnv = dotEnv;
    this.dotEnvLoaded = dotEnvLoaded;
    this.workersDir = workersDir;
    this.logging = logging;
    this.workers = workers;
    this.simulatedServices = simulatedServices;
    this.businessCalendar = businessCalendar;
  }

  public static Config load() {
    final Path workersDir = resolveWorkersDir();
    final Path dotEnvFile = workersDir.resolve(".env");
    final Map<String, String> dotEnv = loadDotEnv(dotEnvFile);

    final Map<String, Object> tree =
        deepMerge(
            readJson(workersDir.resolve("config").resolve("workers.default.json"), true),
            readJson(workersDir.resolve("config").resolve("workers.local.json"), false));

    applyServiceOverrides(tree, dotEnv);

    return build(tree, dotEnv, Files.exists(dotEnvFile), workersDir);
  }

  /**
   * A configuration built from a tree that has already been merged, with nothing read from disk.
   *
   * The tests build their configuration this way, so a case can force an outcome or take the
   * artificial latency away without a configuration file on disk to do it with. {@link #load()} is
   * the path the workers themselves take.
   */
  static Config fromTree(Map<String, Object> tree, Path workersDir) {
    return build(tree, Map.of(), false, workersDir);
  }

  private static Config build(
      Map<String, Object> tree,
      Map<String, String> dotEnv,
      boolean dotEnvLoaded,
      Path workersDir) {

    // An empty string means "not set", so leaving a commented line in `.env` changes nothing.
    final String logLevel =
        valueOr(environment(dotEnv, "HPRTAS_LOG_LEVEL"), DEFAULT_LOG_LEVEL).trim();
    final String logFile = valueOr(environment(dotEnv, "HPRTAS_LOG_FILE"), "").trim();
    final Logging logging =
        new Logging(logLevel, logFile.isEmpty() ? null : workersDir.resolve(logFile).normalize());

    final Map<String, WorkerSettings> workers = readWorkers(tree);
    final SimulatedServices simulatedServices = readSimulatedServices(tree);
    final BusinessCalendar businessCalendar = readBusinessCalendar(tree);

    validate(logging, workers, tree, dotEnv);

    return new Config(
        tree, dotEnv, dotEnvLoaded, workersDir, logging, workers, simulatedServices, businessCalendar);
  }

  /** Forces each simulated service's outcome from its environment variable, where one is set. */
  private static void applyServiceOverrides(Map<String, Object> tree, Map<String, String> dotEnv) {
    final Map<String, Object> simulated = section(tree, "simulatedServices");
    for (Map.Entry<String, String> entry : SERVICE_ENV.entrySet()) {
      final String forced = valueOr(environment(dotEnv, entry.getKey()), "").trim();
      if (!forced.isEmpty()) {
        final Map<String, Object> settings = section(simulated, entry.getValue());
        if (!settings.isEmpty()) {
          settings.put("outcome", forced);
        }
      }
    }
  }

  private static Map<String, WorkerSettings> readWorkers(Map<String, Object> tree) {
    final Map<String, Object> configured = section(tree, "workers");
    final Map<String, WorkerSettings> settings = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : configured.entrySet()) {
      if (entry.getValue() instanceof Map) {
        final Map<String, Object> worker = section(configured, entry.getKey());
        settings.put(
            entry.getKey(),
            new WorkerSettings(
                stringAt(worker, "taskType"),
                intAt(worker, "maxJobsToActivate", 0),
                longAt(worker, "timeoutMs", 0)));
      }
    }
    return settings;
  }

  private static SimulatedServices readSimulatedServices(Map<String, Object> tree) {
    final Map<String, Object> section = section(tree, "simulatedServices");
    final Map<String, Object> scheduling = section(section, "scheduling");
    final Map<String, Object> treatment = section(section, "treatment");
    final Map<String, Object> payment = section(section, "payment");
    final Map<String, Object> correspondence = section(section, "correspondence");

    return new SimulatedServices(
        new SchedulingSettings(
            stringAt(scheduling, "outcome"),
            longAt(scheduling, "latencyMs", 0),
            intAt(scheduling, "slotLeadDaysUrgent", 0),
            intAt(scheduling, "slotLeadDaysRoutine", 0),
            intAt(scheduling, "outsideWindowExtraDays", 0)),
        new TreatmentSettings(stringAt(treatment, "outcome"), longAt(treatment, "latencyMs", 0)),
        new PaymentSettings(stringAt(payment, "outcome"), longAt(payment, "latencyMs", 0)),
        new CorrespondenceSettings(
            stringAt(correspondence, "outcome"), longAt(correspondence, "latencyMs", 0)));
  }

  private static BusinessCalendar readBusinessCalendar(Map<String, Object> tree) {
    final Map<String, Object> calendar = section(tree, "businessCalendar");
    return new BusinessCalendar(
        intAt(calendar, "urgentTelephoneContactDays", 0),
        intAt(calendar, "clinicLetterDays", 0),
        intAt(calendar, "pathwayMonitoringMonthDays", 0),
        intAt(calendar, "higherManagementEscalationDays", 0));
  }

  /** The connection settings handed to the Camunda 8 Java client. */
  public Map<String, Object> connection() {
    final Map<String, Object> connection = new LinkedHashMap<>(section(tree, "connection"));
    for (String key : new ArrayList<>(connection.keySet())) {
      final String value = environment(key);
      if (value != null && !value.isEmpty()) {
        connection.put(key, value);
      }
    }
    return connection;
  }

  /**
   * The environment as the workers see it: the real environment, plus any variable only the
   * {@code .env} file defines. A variable the environment already sets always wins, which is what
   * the Node loader did and what stops a committed file from overriding a machine's own setting.
   */
  public String environment(String name) {
    return environment(dotEnv, name);
  }

  private static String environment(Map<String, String> dotEnv, String name) {
    final String fromProcess = System.getenv(name);
    return fromProcess != null ? fromProcess : dotEnv.get(name);
  }

  public Map<String, WorkerSettings> workers() {
    return workers;
  }

  public WorkerSettings worker(String name) {
    final WorkerSettings settings = workers.get(name);
    if (settings == null) {
      throw new IllegalStateException("there is no configuration for the worker " + name);
    }
    return settings;
  }

  public SimulatedServices simulatedServices() {
    return simulatedServices;
  }

  public BusinessCalendar businessCalendar() {
    return businessCalendar;
  }

  public Logging logging() {
    return logging;
  }

  public boolean dotEnvLoaded() {
    return dotEnvLoaded;
  }

  public Path workersDir() {
    return workersDir;
  }

  /**
   * The workers directory, which is where the configuration is read from.
   *
   * It is the working directory the workers were started from, unless {@code HPRTAS_WORKERS_DIR}
   * (or the {@code hprtas.workersDir} system property) names another one. A missing configuration
   * file is reported against the directory that was searched, so the reason is never a mystery.
   */
  private static Path resolveWorkersDir() {
    final String systemProperty = System.getProperty("hprtas.workersDir");
    if (systemProperty != null && !systemProperty.isBlank()) {
      return Path.of(systemProperty).toAbsolutePath().normalize();
    }
    final String fromEnvironment = System.getenv("HPRTAS_WORKERS_DIR");
    if (fromEnvironment != null && !fromEnvironment.isBlank()) {
      return Path.of(fromEnvironment).toAbsolutePath().normalize();
    }
    return Path.of("").toAbsolutePath().normalize();
  }

  /** Fails fast with a readable message rather than letting the client fail later. */
  private static void validate(
      Logging logging,
      Map<String, WorkerSettings> workers,
      Map<String, Object> tree,
      Map<String, String> dotEnv) {

    final List<String> problems = new ArrayList<>();

    if (!LOG_LEVELS.contains(logging.level())) {
      problems.add(
          "HPRTAS_LOG_LEVEL is \""
              + logging.level()
              + "\" but must be one of "
              + String.join(", ", LOG_LEVELS));
    }

    for (Map.Entry<String, WorkerSettings> entry : workers.entrySet()) {
      final WorkerSettings worker = entry.getValue();
      if (worker.taskType() == null || worker.taskType().isEmpty()) {
        problems.add("workers." + entry.getKey() + " has no taskType");
      }
      if (worker.maxJobsToActivate() < 1) {
        problems.add("workers." + entry.getKey() + ".maxJobsToActivate must be a positive integer");
      }
      if (worker.timeoutMs() < 1000) {
        problems.add("workers." + entry.getKey() + ".timeoutMs must be at least 1000 ms");
      }
    }

    final List<String> addresses = new ArrayList<>();
    addIfSet(addresses, stringAt(section(tree, "connection"), "ZEEBE_GRPC_ADDRESS"));
    addIfSet(addresses, environment(dotEnv, "ZEEBE_GRPC_ADDRESS"));
    addIfSet(addresses, environment(dotEnv, "ZEEBE_ADDRESS"));
    if (!addresses.isEmpty() && !addresses.get(0).matches("^(grpc|grpcs)://.*")) {
      problems.add(
          "the Zeebe gRPC address \""
              + addresses.get(0)
              + "\" must include its protocol, for example grpc://localhost:26500");
    }

    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Invalid configuration:\n  - " + String.join("\n  - ", problems));
    }
  }

  private static void addIfSet(List<String> addresses, String address) {
    if (address != null && !address.isEmpty()) {
      addresses.add(address);
    }
  }

  private static Map<String, Object> readJson(Path file, boolean required) {
    if (!Files.exists(file)) {
      if (required) {
        throw new IllegalStateException(
            "Configuration file is missing: "
                + file
                + "\nRun the workers from the workers directory, or set HPRTAS_WORKERS_DIR to it.");
      }
      return new LinkedHashMap<>();
    }
    try {
      return Json.mapper()
          .readValue(
              Files.readString(file, StandardCharsets.UTF_8),
              new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (IOException e) {
      throw new IllegalStateException(
          "Configuration file is not valid JSON: " + file + " (" + e.getMessage() + ")", e);
    }
  }

  /** Loads {@code .env} when it exists, so the project needs no dependency for configuration. */
  private static Map<String, String> loadDotEnv(Path file) {
    final Map<String, String> values = new LinkedHashMap<>();
    if (!Files.exists(file)) {
      return values;
    }
    final List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("The .env file could not be read: " + file, e);
    }
    for (String raw : lines) {
      String line = raw.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      if (line.startsWith("export ")) {
        line = line.substring("export ".length()).trim();
      }
      final int separator = line.indexOf('=');
      if (separator <= 0) {
        continue;
      }
      final String key = line.substring(0, separator).trim();
      if (!key.isEmpty()) {
        values.put(key, unquote(line.substring(separator + 1).trim()));
      }
    }
    return values;
  }

  private static String unquote(String value) {
    if (value.length() >= 2) {
      final char first = value.charAt(0);
      final char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
    }
    return value;
  }

  private static Map<String, Object> deepMerge(
      Map<String, Object> base, Map<String, Object> override) {
    final Map<String, Object> result = new LinkedHashMap<>(base);
    for (Map.Entry<String, Object> entry : override.entrySet()) {
      final Object value = entry.getValue();
      final Object previous = result.get(entry.getKey());
      final boolean bothPlainObjects = value instanceof Map && previous instanceof Map;
      result.put(
          entry.getKey(), bothPlainObjects ? deepMerge(asMap(previous), asMap(value)) : value);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  /** A named section of the tree, or an empty map when the section is absent. */
  private static Map<String, Object> section(Map<String, Object> source, String name) {
    final Object nested = source == null ? null : source.get(name);
    return nested instanceof Map ? asMap(nested) : new LinkedHashMap<>();
  }

  private static String stringAt(Map<String, Object> source, String key) {
    final Object value = source == null ? null : source.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static int intAt(Map<String, Object> source, String key, int fallback) {
    final Object value = source == null ? null : source.get(key);
    return value instanceof Number number ? number.intValue() : fallback;
  }

  private static long longAt(Map<String, Object> source, String key, long fallback) {
    final Object value = source == null ? null : source.get(key);
    return value instanceof Number number ? number.longValue() : fallback;
  }

  private static String valueOr(String value, String fallback) {
    return value != null ? value : fallback;
  }
}
