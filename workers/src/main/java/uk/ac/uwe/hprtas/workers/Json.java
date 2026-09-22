package uk.ac.uwe.hprtas.workers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** The JSON the workers write: the log lines, and the values quoted back in a validation message. */
public final class Json {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Json() {}

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  /** Serialises a value, or falls back to its text form for something that cannot be serialised. */
  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }
}
