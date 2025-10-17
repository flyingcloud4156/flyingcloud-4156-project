package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Update profile request. */
@Data
public class UpdateProfileRequest {
  @Size(min = 1, max = 80)
  @Schema(example = "New Name")
  private String name;

  @Size(min = 1, max = 64)
  @Schema(example = "America/New_York")
  private String timezone;
}
