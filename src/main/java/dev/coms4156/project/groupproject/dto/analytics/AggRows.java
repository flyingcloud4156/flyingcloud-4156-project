package dev.coms4156.project.groupproject.dto.analytics;

import java.math.BigDecimal;
import lombok.Data;

public final class AggRows {
  private AggRows() {}

  @Data
  public static class IncomeExpenseRow {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
  }

  @Data
  public static class MonthlyRow {
    private String period;
    private BigDecimal income;
    private BigDecimal expense;
  }

  @Data
  public static class CategoryRow {
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
  }

  @Data
  public static class MerchantRow {
    private String label;
    private BigDecimal amount;
  }

  @Data
  public static class UserAmountRow {
    private Long userId;
    private BigDecimal amount;
  }
}
