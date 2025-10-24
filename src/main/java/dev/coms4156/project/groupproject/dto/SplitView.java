package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Data;

/**
 * Split view for transaction responses. Includes computed amount after rounding and tail
 * allocation.
 */
@Data
@Schema(description = "Split view with computed amount")
public class SplitView {

  @Schema(description = "User ID", example = "123")
  private Long userId;

  @Schema(description = "Split method used", example = "EQUAL")
  private String splitMethod;

  @Schema(description = "Original share value", example = "0")
  private BigDecimal shareValue;

  @Schema(description = "Whether user was included", example = "true")
  private Boolean included;

  @Schema(
      description = "Final computed amount after rounding and tail allocation",
      example = "40.00")
  private BigDecimal computedAmount;
}
