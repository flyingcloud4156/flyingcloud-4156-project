package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** Request to create a new ledger. */
@Data
public class CreateLedgerRequest {
  @NotBlank
  @Size(max = 120)
  @Schema(example = "Family 2025")
  private String name;

  @NotBlank
  @Schema(example = "GROUP_BALANCE")
  private String ledgerType;

  @NotBlank
  @Schema(example = "USD")
  private String baseCurrency;

  @Schema(example = "2025-01-01")
  private LocalDate shareStartDate;

  @Valid
  @NotNull(message = "Categories cannot be null")
  @Size(min = 1, message = "At least one category is required")
  @Schema(description = "A list of categories to create for the ledger")
  private List<CreateCategoryRequest> categories;
}
