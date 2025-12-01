package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for net balance calculation across all transactions in a ledger. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Net balance response showing who owes whom across all transactions")
public class NetBalanceResponse {
  @Schema(description = "Ledger ID", example = "456")
  private Long ledgerId;

  @Schema(description = "Currency of the balances", example = "USD")
  private String currency;

  @Schema(description = "List of net debt relationships")
  private List<NetBalanceItem> balances;

  /** Represents a single net balance item between two users. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "Net balance between two users")
  public static class NetBalanceItem {
    @Schema(description = "Creditor user ID (who is owed money)", example = "111")
    private Long creditorId;

    @Schema(description = "Creditor user name", example = "Alice")
    private String creditorName;

    @Schema(description = "Debtor user ID (who owes money)", example = "222")
    private Long debtorId;

    @Schema(description = "Debtor user name", example = "Bob")
    private String debtorName;

    @Schema(description = "Net amount owed (always positive)", example = "25.50")
    private BigDecimal amount;
  }
}
