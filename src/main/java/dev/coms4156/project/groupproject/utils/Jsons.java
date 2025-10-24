package dev.coms4156.project.groupproject.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tiny JSON helper using Jackson. */
public final class Jsons {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Jsons() {}

  /**
   * Converts an object to a JSON string.
   *
   * @param o the object to convert.
   * @return the JSON string.
   */
  public static String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Converts a JSON string to an object.
   *
   * @param s the JSON string.
   * @param type the type of the object.
   * @param <T> the type of the object.
   * @return the object.
   */
  public static <T> T fromJson(String s, Class<T> type) {
    try {
      return MAPPER.readValue(s, type);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
