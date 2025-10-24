package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Change password request. */
@Data
public class ChangePasswordRequest {
  @NotBlank
  @Size(min = 6, max = 64)
  @Schema(example = "123456")
  private String oldPassword;

  @NotBlank
  @Size(min = 6, max = 64)
  @Schema(example = "S3cure!1")
  private String newPassword;

  @Override
  public String toString() {
    return "ChangePasswordRequest{"
        + "oldPassword=\'[MASKED]\'"
        + ", newPassword=\'[MASKED]\'"
        + '}';
  }
}
