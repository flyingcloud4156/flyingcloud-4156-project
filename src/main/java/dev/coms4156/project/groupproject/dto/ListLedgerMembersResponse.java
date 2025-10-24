package dev.coms4156.project.groupproject.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for listing ledger members. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListLedgerMembersResponse {
  private List<LedgerMemberItem> items;

  /** Represents a single member item in the list. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LedgerMemberItem {
    private Long userId;
    private String name;
    private String role;
  }
}
