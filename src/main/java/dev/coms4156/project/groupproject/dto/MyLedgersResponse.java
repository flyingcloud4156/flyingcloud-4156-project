package dev.coms4156.project.groupproject.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for listing the current user's ledgers. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyLedgersResponse {
  private List<LedgerItem> items;

  /** Represents a single ledger item. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LedgerItem {
    private Long ledgerId;
    private String name;
    private String ledgerType;
    private String baseCurrency;
    private String role;
  }
}
