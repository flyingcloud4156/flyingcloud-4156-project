package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

/** Preview of debt edge that will be generated from a transaction. */
@Data
@Schema(description = "Debt edge preview")
public class EdgePreview {

  @Schema(description = "Creditor user ID (who is owed money)", example = "111")
  private Long fromUserId;

  @Schema(description = "Debtor user ID (who owes money)", example = "222")
  private Long toUserId;

  @Schema(description = "Amount owed", example = "40.00")
  private BigDecimal amount;

  @Schema(description = "Currency of the debt", example = "USD")
  private String edgeCurrency;
}
