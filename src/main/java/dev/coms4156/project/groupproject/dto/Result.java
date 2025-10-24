package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Unified response wrapper. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {
  @Schema(description = "Whether the request succeeded")
  private boolean success;

  @Schema(description = "Message for humans")
  private String message;

  @Schema(description = "Data payload")
  private T data;

  @Schema(description = "Total count for list payloads")
  private Long total;

  public static <T> Result<T> ok() {
    return new Result<T>(true, "OK", null, null);
  }

  public static <T> Result<T> ok(T data) {
    return new Result<T>(true, "OK", data, null);
  }

  public static <T> Result<T> ok(T data, long total) {
    return new Result<T>(true, "OK", data, total);
  }

  public static <T> Result<T> fail(String msg) {
    return new Result<T>(false, msg, null, null);
  }
}
