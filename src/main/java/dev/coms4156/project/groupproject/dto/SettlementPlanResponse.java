package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for settlement plan showing minimal transfer list to settle all debts. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Settlement plan with minimal transfers to settle all debts")
public class SettlementPlanResponse {
  @Schema(description = "Ledger ID", example = "456")
  private Long ledgerId;

  @Schema(description = "Currency of the settlement", example = "USD")
  private String currency;

  @Schema(description = "Total number of transfers in the plan", example = "3")
  private Integer transferCount;

  @Schema(description = "List of transfer instructions (who pays whom how much)")
  private List<TransferItem> transfers;

  /** Represents a single transfer in the settlement plan. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Single transfer instruction")
  public static class TransferItem {
    @Schema(description = "Payer user ID (who pays)", example = "222")
    private Long fromUserId;

    @Schema(description = "Payer user name", example = "Bob")
    private String fromUserName;

    @Schema(description = "Receiver user ID (who receives)", example = "111")
    private Long toUserId;

    @Schema(description = "Receiver user name", example = "Alice")
    private String toUserName;

    @Schema(description = "Transfer amount (always positive)", example = "25.50")
    private BigDecimal amount;
  }
}
