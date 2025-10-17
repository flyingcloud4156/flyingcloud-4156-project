package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

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
}
