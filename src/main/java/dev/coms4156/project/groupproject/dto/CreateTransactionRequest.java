package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** Request for creating a new transaction with splits and debt edge generation. */
@Data
@Schema(description = "Request for creating a new transaction")
public class CreateTransactionRequest {

  @NotNull(message = "Transaction timestamp is required")
  @Schema(
      description = "When the transaction occurred",
      example = "2025-10-10T20:15:00Z",
      required = true)
  private LocalDateTime txnAt;

  @NotNull(message = "Transaction type is required")
  @Pattern(regexp = "EXPENSE|INCOME|LOAN", message = "Type must be EXPENSE, INCOME, or LOAN")
  @Schema(
      description = "Transaction type",
      example = "EXPENSE",
      allowableValues = {"EXPENSE", "INCOME", "LOAN"},
      required = true)
  private String type;

  @NotNull(message = "Currency is required")
  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter code")
  @Schema(description = "Transaction currency (ISO 4217)", example = "USD", required = true)
  private String currency;

  @NotNull(message = "Total amount is required")
  @DecimalMin(value = "0.01", message = "Amount must be positive")
  @Schema(description = "Total transaction amount", example = "120.00", required = true)
  private BigDecimal amountTotal;

  @Schema(description = "Category ID (optional)", example = "5")
  private Long categoryId;

  @Size(max = 500, message = "Note cannot exceed 500 characters")
  @Schema(description = "Transaction note", example = "Dinner", maxLength = 500)
  private String note;

  @Schema(description = "User ID who paid upfront (required for EXPENSE)", example = "111")
  private Long payerId;

  @Schema(description = "Whether transaction is private", example = "false", defaultValue = "false")
  private Boolean isPrivate = false;

  @Pattern(
      regexp = "NONE|ROUND_HALF_UP|TRIM_TO_UNIT",
      message = "Rounding strategy must be NONE, ROUND_HALF_UP, or TRIM_TO_UNIT")
  @Schema(
      description = "Rounding strategy",
      example = "ROUND_HALF_UP",
      allowableValues = {"NONE", "ROUND_HALF_UP", "TRIM_TO_UNIT"},
      defaultValue = "ROUND_HALF_UP")
  private String roundingStrategy = "ROUND_HALF_UP";

  @Pattern(
      regexp = "PAYER|LARGEST_SHARE|CREATOR",
      message = "Tail allocation must be PAYER, LARGEST_SHARE, or CREATOR")
  @Schema(
      description = "Tail allocation strategy",
      example = "PAYER",
      allowableValues = {"PAYER", "LARGEST_SHARE", "CREATOR"},
      defaultValue = "PAYER")
  private String tailAllocation = "PAYER";

  @Valid
  @Schema(description = "Split items (required for EXPENSE/INCOME, not allowed for LOAN)")
  private List<SplitItem> splits;

  @Valid
  @Schema(description = "Loan details (required for LOAN type only)")
  private LoanRequest loan;
}
