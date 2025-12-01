package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

/** Single budget status item with spending information and alert status. */
@Data
@Schema(description = "Budget status item")
public class BudgetStatusItem {

  @Schema(description = "Budget ID", example = "1")
  private Long budgetId;

  @Schema(description = "Category ID (null for ledger-level budget)", example = "5")
  private Long categoryId;

  @Schema(description = "Category name", example = "Food")
  private String categoryName;

  @Schema(description = "Budget limit amount", example = "1000.00")
  private BigDecimal limitAmount;

  @Schema(description = "Spent amount", example = "850.00")
  private BigDecimal spentAmount;

  @Schema(description = "Usage ratio (0.85 means 85%)", example = "0.85")
  private String ratio;

  @Schema(
      description = "Budget status",
      example = "NEAR_LIMIT",
      allowableValues = {"OK", "NEAR_LIMIT", "EXCEEDED"})
  private String status;
}
