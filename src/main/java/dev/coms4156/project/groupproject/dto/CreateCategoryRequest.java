package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryRequest {
  @NotBlank
  @Schema(example = "Food")
  private String name;

  @NotBlank
  @Schema(example = "EXPENSE")
  private String kind;

  @Schema(example = "1")
  private Integer sortOrder = 0;
}
