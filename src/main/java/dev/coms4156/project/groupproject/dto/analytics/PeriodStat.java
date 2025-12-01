package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class PeriodStat {
  private String period;
  private BigDecimal income;
  private BigDecimal expense;
}
