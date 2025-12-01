package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class LedgerAnalyticsOverview {
  private String currency;
  private LocalDateTime rangeStart;
  private LocalDateTime rangeEnd;
  private BigDecimal totalIncome;
  private BigDecimal totalExpense;
  private BigDecimal netBalance;
  private List<PeriodStat> trend;
  private List<CategoryStat> byCategory;
  private List<UserArAp> arap;
  private List<MerchantStat> topMerchants;
  private List<RecommendationItem> recommendations;
}
