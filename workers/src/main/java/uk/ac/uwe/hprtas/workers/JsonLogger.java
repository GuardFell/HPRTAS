package uk.ac.uwe.hprtas.workers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logging for the external workers.
 *
 * Every line is one JSON object carrying the correlation details the test evidence needs: the
 * worker, the job key, the process instance and the outcome. The worker log is one of the artefacts
 * the definition of done asks for, so the lines are written for a reader, not only for a debugger.
 *
 * Job workers run on more than one thread, so a line is written under a lock: two jobs finishing at
 * the same moment must not produce a line each other's reader cannot parse.
 *
 * The streams are pinned to UTF-8 rather than left to the platform. The evidence files are read on
 * whatever machine the reader is using, and a log line that decodes differently depending on the
 * console code page is not evidence.
 */
public final class JsonLogger implements AutoCloseable {

  /** The levels, with the threshold each one sits at. */
  public enum Level {
    ERROR(0, "error"),
    WARN(1, "warn"),
    INFO(2, "info"),
    DEBUG(3, "debug");

    private final int severity;
    private final String wireName;

    Level(int severity, String wireName) {
      this.severity = severity;
      this.wireName = wireName;
    }

    public String wireName() {
      return wireName;
    }

    /** The named level, defaulting to info for anything unrecognised. */
    public static Level of(String name) {
      if (name != null) {
        for (Level level : values()) {
          if (level.wireName.equals(name)) {
            return level;
          }
        }
      }
      return INFO;
    }
  }

  /** Where the lines go. Shared with every child, so one log file has one writer and one lock. */
  private static final class Sink {
    private final PrintStream out;
    private final PrintStream err;
    private final Writer file;
    private final Object lock = new Object();

    Sink(PrintStream out, PrintStream err, Writer file) {
      this.out = out;
      this.err = err;
      this.file = file;
    }
  }

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

  private final Level threshold;
  private final String name;
  private final Map<String, Object> extra;
  private final Sink sink;

  private JsonLogger(Level threshold, String name, Map<String, Object> extra, Sink sink) {
    this.threshold = threshold;
    this.name = name;
    this.extra = extra;
    this.sink = sink;
  }

  /**
   * The workers' logger: JSON lines on standard output, errors on standard error, and optionally
   * the same lines appended to a file.
   *
   * @param levelName error, warn, info or debug
   * @param file a log file to append to, or null to write only to the console
   */
  public static JsonLogger create(String levelName, Path file) {
    Writer writer = null;
    if (file != null) {
      try {
        if (file.getParent() != null) {
          Files.createDirectories(file.getParent());
        }
        writer =
            Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE);
      } catch (IOException e) {
        throw new UncheckedIOException("the log file could not be opened: " + file, e);
      }
    }
    return new JsonLogger(
        Level.of(levelName), "workers", Map.of(), new Sink(System.out, System.err, writer));
  }

  /** A logger that discards everything, for the tests and for the configuration check. */
  public static JsonLogger silent() {
    final PrintStream discarded =
        new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
    return new JsonLogger(Level.DEBUG, "workers", Map.of(), new Sink(discarded, discarded, null));
  }

  /**
   * A logger carrying extra fields on every line, for example the worker name.
   *
   * A field supplied at the call site wins over the same field on the child, which is what makes a
   * child's fields a default rather than something a caller cannot override.
   */
  public JsonLogger child(Map<String, Object> extraFields) {
    final Map<String, Object> merged = new LinkedHashMap<>(extra);
    if (extraFields != null) {
      merged.putAll(extraFields);
    }
    return new JsonLogger(threshold, name, merged, sink);
  }

  public void error(String message, Map<String, Object> fields) {
    write(Level.ERROR, message, fields);
  }

  public void warn(String message, Map<String, Object> fields) {
    write(Level.WARN, message, fields);
  }

  public void info(String message, Map<String, Object> fields) {
    write(Level.INFO, message, fields);
  }

  public void debug(String message, Map<String, Object> fields) {
    write(Level.DEBUG, message, fields);
  }

  private void write(Level level, String message, Map<String, Object> fields) {
    if (level.severity > threshold.severity) {
      return;
    }

    final Map<String, Object> line = new LinkedHashMap<>();
    line.put("time", TIMESTAMP.format(Instant.now()));
    line.put("level", level.wireName);
    line.put("logger", name);
    line.put("message", message);
    line.putAll(extra);
    if (fields != null) {
      line.putAll(fields);
    }

    final String json = Json.write(line);
    synchronized (sink.lock) {
      final PrintStream stream = level == Level.ERROR ? sink.err : sink.out;
      stream.print(json + "\n");
      // Flushed per line rather than on exit: a run that is interrupted still leaves the evidence
      // of everything that happened before it.
      stream.flush();
      if (sink.file != null) {
        try {
          sink.file.write(json);
          sink.file.write("\n");
          sink.file.flush();
        } catch (IOException e) {
          throw new UncheckedIOException("the log file could not be written", e);
        }
      }
    }
  }

  @Override
  public void close() {
    synchronized (sink.lock) {
      if (sink.file == null) {
        return;
      }
      try {
        sink.file.flush();
        sink.file.close();
      } catch (IOException e) {
        throw new UncheckedIOException("the log file could not be closed", e);
      }
    }
  }
}
