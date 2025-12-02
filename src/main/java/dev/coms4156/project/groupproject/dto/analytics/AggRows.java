package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import lombok.Data;

/** Container for analytics aggregation row types. */
public final class AggRows {
  private AggRows() {}

  /** Income and expense totals. */
  @Data
  public static class IncomeExpenseRow {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
  }

  /** Monthly income and expense data. */
  @Data
  public static class MonthlyRow {
    private String period;
    private BigDecimal income;
    private BigDecimal expense;
  }

  /** Category-based expense aggregation. */
  @Data
  public static class CategoryRow {
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
  }

  /** Merchant-based transaction aggregation. */
  @Data
  public static class MerchantRow {
    private String label;
    private BigDecimal amount;
  }

  /** User amount aggregation. */
  @Data
  public static class UserAmountRow {
    private Long userId;
    private BigDecimal amount;
  }
}
