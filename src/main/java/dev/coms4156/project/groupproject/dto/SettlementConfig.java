package dev.coms4156.project.groupproject.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for settlement plan generation, including constraints, rounding rules, and
 * algorithm selection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Settlement configuration with constraints and options")
public class SettlementConfig {

  @Schema(
      description = "Rounding strategy: ROUND_HALF_UP, TRIM_TO_UNIT, or NONE",
      example = "ROUND_HALF_UP")
  private String roundingStrategy = "ROUND_HALF_UP";

  @Schema(
      description = "Maximum transfer amount per transaction (cap). Null means no cap.",
      example = "1000.00")
  private BigDecimal maxTransferAmount;

  @Schema(
      description =
          "Map of user ID pairs (fromUserId-toUserId) to allowed payment channels. "
              + "Empty means all channels allowed.",
      example = "{\"1-2\": [\"BANK\", \"VENMO\"]}")
  private Map<String, Set<String>> paymentChannels;

  @Schema(
      description =
          "Force use of min-cost flow algorithm instead of heap-greedy. "
              + "Useful for complex constraint scenarios.",
      example = "false")
  private Boolean forceMinCostFlow = false;

  @Schema(
      description =
          "Threshold for switching to min-cost flow. If heap-greedy produces more transfers "
              + "than this threshold, fallback to min-cost flow.",
      example = "10")
  private Integer minCostFlowThreshold;

  @Schema(
      description =
          "Currency conversion rates map (fromCurrency-toCurrency -> rate). If not "
              + "provided, assumes 1:1 for same currency or throws error for different currencies.",
      example = "{\"EUR-USD\": 1.1, \"GBP-USD\": 1.25}")
  private Map<String, BigDecimal> currencyRates;
}
