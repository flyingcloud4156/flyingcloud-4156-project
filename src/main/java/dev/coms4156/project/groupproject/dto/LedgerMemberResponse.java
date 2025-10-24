package dev.coms4156.project.groupproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for ledger member operations. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerMemberResponse {
  private Long ledgerId;
  private Long userId;
  private String role;
}
