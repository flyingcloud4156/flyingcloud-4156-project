package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for ledger-related operations. */
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

  @Schema(description = "Category associated with this ledger")
  private CategoryResponse category;
}
