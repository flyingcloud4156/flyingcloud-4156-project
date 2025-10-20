package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** Request for updating an existing transaction. Only provided fields will be updated. */
@Data
@Schema(description = "Request for updating an existing transaction")
public class UpdateTransactionRequest {

  @Schema(description = "New transaction timestamp", example = "2025-10-10T20:30:00Z")
  private LocalDateTime txnAt;

  @Schema(description = "New transaction note", example = "Dinner @Sushi", maxLength = 500)
  @Size(max = 500, message = "Note cannot exceed 500 characters")
  private String note;

  @Schema(description = "New category ID", example = "5")
  private Long categoryId;

  @Schema(description = "New payer ID")
  private Long payerId;

  @Schema(description = "Whether transaction is private", example = "false")
  private Boolean isPrivate;

  @Pattern(
      regexp = "NONE|ROUND_HALF_UP|TRIM_TO_UNIT",
      message = "Rounding strategy must be NONE, ROUND_HALF_UP, or TRIM_TO_UNIT")
  @Schema(
      description = "New rounding strategy",
      allowableValues = {"NONE", "ROUND_HALF_UP", "TRIM_TO_UNIT"})
  private String roundingStrategy;

  @Pattern(
      regexp = "PAYER|LARGEST_SHARE|CREATOR",
      message = "Tail allocation must be PAYER, LARGEST_SHARE, or CREATOR")
  @Schema(
      description = "New tail allocation strategy",
      allowableValues = {"PAYER", "LARGEST_SHARE", "CREATOR"})
  private String tailAllocation;

  @Valid
  @Schema(description = "New split items (will trigger recalculation)")
  private List<SplitItem> splits;
}
