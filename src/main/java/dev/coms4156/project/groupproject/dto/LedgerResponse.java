package dev.coms4156.project.groupproject.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LedgerResponse {
  private Long ledgerId;
  private String name;
  private String ledgerType;
  private String baseCurrency;
  private LocalDate shareStartDate;
  private String role;
}
