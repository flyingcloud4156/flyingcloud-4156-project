package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Loan request details for LOAN type transactions. */
@Data
@Schema(description = "Loan request details")
public class LoanRequest {

  @NotNull(message = "Creditor user ID is required")
  @Schema(description = "User ID of the creditor (lender)", example = "111", required = true)
  private Long creditorUserId;

  @NotNull(message = "Debtor user ID is required")
  @Schema(description = "User ID of the debtor (borrower)", example = "222", required = true)
  private Long debtorUserId;
}
