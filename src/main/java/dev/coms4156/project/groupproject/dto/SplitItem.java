package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Split item for transaction creation/update requests. Represents how a user should be included in
 * a transaction split.
 */
@Data
@Schema(description = "Split item for transaction creation/update")
public class SplitItem {

  @NotNull(message = "User ID is required")
  @Schema(description = "User ID", example = "123", required = true)
  private Long userId;

  @NotNull(message = "Split method is required")
  @Schema(
      description = "Split method: EQUAL, PERCENT, WEIGHT, or EXACT",
      example = "EQUAL",
      required = true)
  private String splitMethod;

  @DecimalMin(value = "0.0", message = "Share value must be non-negative")
  @Schema(
      description = "Share value (meaning depends on split method)",
      example = "0",
      required = true)
  private BigDecimal shareValue;

  @Schema(
      description = "Whether user is included in this split",
      example = "true",
      defaultValue = "true")
  private Boolean included = true;
}
