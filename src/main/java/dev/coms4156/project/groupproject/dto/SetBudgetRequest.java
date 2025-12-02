package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/** Request for setting or updating a budget. */
@Data
@Schema(description = "Set or update budget request")
public class SetBudgetRequest {

  @Schema(
      description = "Category ID (null for ledger-level budget, non-null for category-specific)",
      example = "5")
  private Long categoryId;

  @NotNull(message = "Year is required")
  @Min(value = 2020, message = "Year must be >= 2020")
  @Max(value = 2100, message = "Year must be <= 2100")
  @Schema(description = "Budget year", example = "2025")
  private Integer year;

  @NotNull(message = "Month is required")
  @Min(value = 1, message = "Month must be between 1 and 12")
  @Max(value = 12, message = "Month must be between 1 and 12")
  @Schema(description = "Budget month (1-12)", example = "11")
  private Integer month;

  @NotNull(message = "Limit amount is required")
  @DecimalMin(value = "0.01", message = "Limit amount must be positive")
  @Schema(description = "Budget limit amount", example = "1000.00")
  private BigDecimal limitAmount;
}
