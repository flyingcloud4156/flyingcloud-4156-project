package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** Response for transaction details including splits and edge previews. */
@Data
@Schema(description = "Transaction details response")
public class TransactionResponse {

  @Schema(description = "Transaction ID", example = "1001")
  private Long transactionId;

  @Schema(description = "Ledger ID", example = "456")
  private Long ledgerId;

  @Schema(description = "Transaction timestamp", example = "2025-10-10T20:15:00Z")
  private LocalDateTime txnAt;

  @Schema(description = "Transaction type", example = "EXPENSE")
  private String type;

  @Schema(description = "Transaction currency", example = "USD")
  private String currency;

  @Schema(description = "Total transaction amount", example = "120.00")
  private BigDecimal amountTotal;

  @Schema(description = "Category ID", example = "5")
  private Long categoryId;

  @Schema(description = "Transaction note", example = "Dinner")
  private String note;

  @Schema(description = "Payer user ID", example = "111")
  private Long payerId;

  @Schema(description = "Created by user ID", example = "111")
  private Long createdBy;

  @Schema(description = "Rounding strategy used", example = "ROUND_HALF_UP")
  private String roundingStrategy;

  @Schema(description = "Tail allocation strategy used", example = "PAYER")
  private String tailAllocation;

  @Schema(description = "Transaction splits with computed amounts")
  private List<SplitView> splits;

  @Schema(description = "Preview of debt edges that will be generated")
  private List<EdgePreview> edgesPreview;
}
