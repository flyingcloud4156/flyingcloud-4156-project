package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import lombok.Data;

/** Merchant-based transaction statistics. */
@Data
public class MerchantStat {
  private String label;
  private BigDecimal amount;
}
