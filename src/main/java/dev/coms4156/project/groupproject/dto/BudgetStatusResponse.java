package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/** Response containing budget status items for a specific month. */
@Data
@Schema(description = "Budget status response")
public class BudgetStatusResponse {

  @Schema(description = "List of budget status items")
  private List<BudgetStatusItem> items;
}
