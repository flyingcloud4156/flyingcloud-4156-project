package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import lombok.Data;

/** Category statistics for expense breakdown. */
@Data
public class CategoryStat {
  private Long categoryId;
  private String categoryName;
  private BigDecimal amount;
  private BigDecimal ratio;
}
