package uk.ac.uwe.hprtas.workers;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input validation for the workers.
 *
 * The process variables come from Camunda Forms and from earlier steps in the process, so a worker
 * cannot assume they are present or well formed. Every worker checks its own inputs before doing
 * any work, and an unusable input is reported as a business error rather than allowed to throw: the
 * requirement is a controlled outcome, with no rebooking and no recharging.
 */
public final class Validate {

  private Validate() {}

  /** The problems found, and the values the handler should work with once they are empty. */
  public record CheckResult(List<String> problems, Map<String, Object> values) {

    public boolean hasProblems() {
      return !problems.isEmpty();
    }

    /** The problems as one clause, which is how they are written into a business error message. */
    public String summarise() {
      return String.join("; ", problems);
    }
  }

  /** Runs every specification and collects the problems into one result. */
  public static CheckResult checkVariables(
      Map<String, Object> variables, Map<String, FieldSpec> specifications) {

    final Map<String, Object> source = variables == null ? Map.of() : variables;
    final List<String> problems = new ArrayList<>();
    final Map<String, Object> values = new LinkedHashMap<>();

    for (Map.Entry<String, FieldSpec> specification : specifications.entrySet()) {
      final FieldCheck check =
          checkField(specification.getKey(), source.get(specification.getKey()), specification.getValue());
      problems.addAll(check.problems());
      values.put(specification.getKey(), check.value());
    }

    return new CheckResult(Collections.unmodifiableList(problems), values);
  }

  private record FieldCheck(List<String> problems, Object value) {}

  private static FieldCheck checkField(String name, Object value, FieldSpec spec) {
    final List<String> problems = new ArrayList<>();
    final boolean present = value != null && !(value instanceof String text && text.isEmpty());

    if (!present) {
      if (spec.isRequired()) {
        problems.add(name + " is required but was not supplied");
      }
      return new FieldCheck(problems, spec.defaultValue());
    }

    switch (spec.type()) {
      case STRING -> {
        if (!(value instanceof String text)) {
          problems.add(name + " must be text");
        } else if (spec.oneOf() != null && !spec.oneOf().contains(text)) {
          problems.add(
              name + " is \"" + text + "\" but must be one of " + String.join(", ", spec.oneOf()));
        }
      }

      case BOOLEAN -> {
        if (!(value instanceof Boolean)) {
          problems.add(name + " must be true or false, but was " + Json.write(value));
        }
      }

      case NUMBER, MONEY -> {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
          problems.add(name + " must be a number, but was " + Json.write(value));
        } else if (spec.type() == FieldSpec.Type.MONEY && number.doubleValue() <= 0) {
          problems.add(name + " must be greater than zero, but was " + jsNumber(number));
        } else if (spec.max() != null && number.doubleValue() > spec.max()) {
          problems.add(name + " must not be more than " + spec.max() + ", but was " + jsNumber(number));
        }
      }

      case DAYS -> {
        if (value instanceof Number number
            && isWholeNumber(number)
            && number.doubleValue() >= 1) {
          if (number.doubleValue() > 366) {
            problems.add(name + " must not be more than 366 days, but was " + jsNumber(number));
          }
        } else {
          problems.add(
              name + " must be a whole number of days of at least 1, but was " + Json.write(value));
        }
      }

      case IDENTIFIER -> {
        if (!(value instanceof String text) || text.trim().isEmpty()) {
          problems.add(name + " must be a non-empty reference");
        }
      }

      case LIST -> {
        final List<Object> entries = asList(value);
        if (entries.isEmpty()) {
          problems.add(name + " must not be empty");
        } else if (spec.max() != null && entries.size() > spec.max()) {
          problems.add(
              name
                  + " must not contain more than "
                  + spec.max()
                  + " entries, but had "
                  + entries.size());
        }
      }
    }

    return new FieldCheck(problems, value);
  }

  /**
   * Accepts a comma-separated string from a form field or a real list.
   *
   * The text form is trimmed, because the whitespace around a comma is the form's, not the value's.
   * A real list is filtered on the trimmed form but keeps the entries as they were, so values that
   * are not text - a number, a boolean - survive the round trip out to the engine unchanged.
   */
  public static List<Object> asList(Object value) {
    if (value instanceof List<?> entries) {
      final List<Object> kept = new ArrayList<>();
      for (Object entry : entries) {
        if (!String.valueOf(entry).trim().isEmpty()) {
          kept.add(entry);
        }
      }
      return kept;
    }

    if (value instanceof String text) {
      final List<Object> kept = new ArrayList<>();
      for (String entry : text.split(",", -1)) {
        final String trimmed = entry.trim();
        if (!trimmed.isEmpty()) {
          kept.add(trimmed);
        }
      }
      return kept;
    }

    return List.of();
  }

  /**
   * Card and security details must never reach the hospital system, so their presence is treated as
   * a defective input rather than something to ignore and pass on (BR-06, NFR-007). The list is
   * intentionally wider than the fields a form would use.
   */
  private static final List<String> PROHIBITED_FINANCIAL_FIELDS =
      List.of(
          "cardNumber",
          "cardnumber",
          "card_number",
          "pan",
          "cvv",
          "cvc",
          "cv2",
          "securityCode",
          "cardSecurityCode",
          "cardExpiry",
          "expiryDate",
          "cardHolderName",
          "magstripe",
          "trackData");

  /** @return the prohibited field names found in the variables. */
  public static List<String> findProhibitedFinancialFields(Map<String, Object> variables) {
    final List<String> found = new ArrayList<>();
    if (variables == null) {
      return found;
    }
    for (String field : PROHIBITED_FINANCIAL_FIELDS) {
      final Object value = variables.get(field);
      if (value != null && !(value instanceof String text && text.isEmpty())) {
        found.add(field);
      }
    }
    return found;
  }

  /** ISO calendar date (YYYY-MM-DD), which is the format used for every business date. */
  public static String toIsoDate(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
  }

  /** The same, a whole number of calendar days later. */
  public static String toIsoDatePlusDays(Instant instant, int days) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate().plusDays(days).toString();
  }

  /** Whole days from `from` to `to`, counted on UTC calendar dates. */
  public static int daysBetween(Instant from, LocalDate to) {
    return (int) ChronoUnit.DAYS.between(from.atZone(ZoneOffset.UTC).toLocalDate(), to);
  }

  private static boolean isWholeNumber(Number number) {
    final double value = number.doubleValue();
    return Double.isFinite(value) && value == Math.rint(value);
  }

  /** A number the way a JavaScript template literal renders it, so the messages read as they did. */
  private static String jsNumber(Number number) {
    final double value = number.doubleValue();
    if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1e15) {
      return String.valueOf((long) value);
    }
    return String.valueOf(value);
  }
}
