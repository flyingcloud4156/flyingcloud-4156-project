package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for category information. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Category information")
public class CategoryResponse {
  @Schema(description = "Category ID", example = "1")
  private Long id;

  @Schema(description = "Ledger ID", example = "1")
  private Long ledgerId;

  @Schema(description = "Category name", example = "Food")
  private String name;

  @Schema(description = "Category kind", example = "EXPENSE")
  private String kind;

  @Schema(description = "Whether category is active", example = "true")
  private Boolean isActive;
}
