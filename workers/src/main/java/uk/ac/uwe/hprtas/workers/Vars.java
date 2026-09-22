package uk.ac.uwe.hprtas.workers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small helpers for building the process variables an outcome carries.
 *
 * An ordered map built from alternating keys and values reads like the object literal it replaces,
 * and it is ordered on purpose: the variables are written into the engine as JSON, and the order
 * they are written in is the order a reader of the evidence sees them in.
 *
 * Unlike {@link Map#of}, these builders accept null values, which the variables deliberately carry:
 * a worker that reports "no appointment was found" has to say {@code appointmentDate = null} rather
 * than leave the key out, because leaving it out would leave the previous value in the process.
 */
public final class Vars {

  private Vars() {}

  /** An ordered map from alternating keys and values. */
  public static Map<String, Object> of(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("expected key, value, key, value, ...");
    }
    final Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return map;
  }

  /** An unmodifiable copy that keeps the order, for an outcome to hold on to. */
  public static Map<String, Object> copy(Map<String, Object> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source == null ? Map.of() : source));
  }

  public static List<Object> list(Object... items) {
    final List<Object> list = new ArrayList<>(items.length);
    Collections.addAll(list, items);
    return list;
  }
}
