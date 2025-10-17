package dev.coms4156.project.groupproject.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListLedgerMembersResponse {
  private List<LedgerMemberItem> items;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LedgerMemberItem {
    private Long userId;
    private String name;
    private String role;
  }
}
