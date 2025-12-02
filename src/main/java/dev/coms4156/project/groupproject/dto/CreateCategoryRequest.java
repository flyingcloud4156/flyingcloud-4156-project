package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Request to create a category. */
@Data
@Schema(description = "Request to create a category")
public class CreateCategoryRequest {

  @NotBlank(message = "Category name is required")
  @Schema(description = "Category name", example = "Food", required = true)
  private String name;

  @NotBlank(message = "Category kind is required")
  @Pattern(
      regexp = "EXPENSE|INCOME|TRANSFER",
      message = "Category kind must be EXPENSE, INCOME, or TRANSFER")
  @Schema(
      description = "Category kind",
      example = "EXPENSE",
      allowableValues = {"EXPENSE", "INCOME", "TRANSFER"},
      required = true)
  private String kind;

  @Schema(description = "Whether category is active", example = "true", defaultValue = "true")
  private Boolean isActive = true;
}
