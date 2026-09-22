package uk.ac.uwe.hprtas.workers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a process variable must look like for a worker to accept it.
 *
 * A specification says which kind of value the field is, whether the process can continue without
 * it, which values are accepted where the process branches on the value, and any upper bound. The
 * builder reads like the declaration it replaces:
 *
 * <pre>{@code
 * FieldSpec.order(
 *     "priority", FieldSpec.string().required().oneOf("urgent", "routine"),
 *     "requestedWindow", FieldSpec.days().required())
 * }</pre>
 */
public final class FieldSpec {

  /** The value kinds a worker checks for. */
  public enum Type {
    STRING,
    BOOLEAN,
    NUMBER,
    MONEY,
    DAYS,
    IDENTIFIER,
    LIST
  }

  private final Type type;
  private boolean required;
  private List<String> oneOf;
  private Integer max;
  private Object defaultValue;

  private FieldSpec(Type type) {
    this.type = type;
  }

  public static FieldSpec string() {
    return new FieldSpec(Type.STRING);
  }

  public static FieldSpec bool() {
    return new FieldSpec(Type.BOOLEAN);
  }

  public static FieldSpec number() {
    return new FieldSpec(Type.NUMBER);
  }

  /** A number that has to be greater than zero. */
  public static FieldSpec money() {
    return new FieldSpec(Type.MONEY);
  }

  /** A whole number of days, at least one and at most a year. */
  public static FieldSpec days() {
    return new FieldSpec(Type.DAYS);
  }

  /** A non-empty reference. */
  public static FieldSpec identifier() {
    return new FieldSpec(Type.IDENTIFIER);
  }

  /** A list, accepted either as a real list or as the comma-separated text a form field produces. */
  public static FieldSpec list() {
    return new FieldSpec(Type.LIST);
  }

  /** The process cannot continue without this field. */
  public FieldSpec required() {
    this.required = true;
    return this;
  }

  /** The accepted values, for a field the process branches on. */
  public FieldSpec oneOf(String... accepted) {
    this.oneOf = List.of(accepted);
    return this;
  }

  /** The upper bound for a number, or the longest accepted list. */
  public FieldSpec max(int upperBound) {
    this.max = upperBound;
    return this;
  }

  /** The value used when the field is not supplied. */
  public FieldSpec defaultTo(Object value) {
    this.defaultValue = value;
    return this;
  }

  /**
   * The specifications in declaration order. The order matters: validation reports every problem it
   * finds in one message, and a reader of the evidence should see them in the order the fields are
   * declared rather than in whatever order a hash map happens to use.
   */
  public static Map<String, FieldSpec> order(Object... namesAndSpecifications) {
    if (namesAndSpecifications.length % 2 != 0) {
      throw new IllegalArgumentException("expected name, specification, name, specification, ...");
    }
    final Map<String, FieldSpec> specifications = new LinkedHashMap<>();
    for (int i = 0; i < namesAndSpecifications.length; i += 2) {
      specifications.put(
          (String) namesAndSpecifications[i], (FieldSpec) namesAndSpecifications[i + 1]);
    }
    return Collections.unmodifiableMap(specifications);
  }

  public Type type() {
    return type;
  }

  public boolean isRequired() {
    return required;
  }

  public List<String> oneOf() {
    return oneOf;
  }

  public Integer max() {
    return max;
  }

  public Object defaultValue() {
    return defaultValue;
  }
}
