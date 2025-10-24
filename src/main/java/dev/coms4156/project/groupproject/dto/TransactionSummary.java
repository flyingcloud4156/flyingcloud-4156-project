package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** Summary view of a transaction for list responses. */
@Data
@Schema(description = "Transaction summary for list responses")
public class TransactionSummary {

  @Schema(description = "Transaction ID", example = "1001")
  private Long transactionId;

  @Schema(description = "Transaction timestamp", example = "2025-10-10T20:15:00Z")
  private LocalDateTime txnAt;

  @Schema(description = "Transaction type", example = "EXPENSE")
  private String type;

  @Schema(description = "Transaction currency", example = "USD")
  private String currency;

  @Schema(description = "Total transaction amount", example = "120.00")
  private BigDecimal amountTotal;

  @Schema(description = "Payer user ID", example = "111")
  private Long payerId;

  @Schema(description = "Created by user ID", example = "111")
  private Long createdBy;

  @Schema(description = "Transaction note", example = "Dinner")
  private String note;
}
