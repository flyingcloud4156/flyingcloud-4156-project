package dev.coms4156.project.groupproject.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tiny JSON helper using Jackson. */
public final class Jsons {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Jsons() {}

  public static String toJson(Object o) {
    try {
      return MAPPER.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T fromJson(String s, Class<T> type) {
    try {
      return MAPPER.readValue(s, type);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
