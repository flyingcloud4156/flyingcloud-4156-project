package dev.coms4156.project.groupproject.mapper;

import dev.coms4156.project.groupproject.dto.analytics.AggRows;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Mapper for analytics aggregation queries. */
@Mapper
public interface AnalyticsAggMapper {

  @Select(
      """
      SELECT
        COALESCE(SUM(CASE WHEN type = 'INCOME'  THEN amount_total END), 0) AS totalIncome,
        COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_total END), 0) AS totalExpense
      FROM transactions
      WHERE ledger_id = #{ledgerId}
        AND txn_at >= #{start}
        AND txn_at <  #{end}
        AND type IN ('INCOME','EXPENSE')
        AND (is_private = 0 OR created_by = #{currentUserId})
      """)
  AggRows.IncomeExpenseRow sumIncomeExpense(
      @Param("ledgerId") Long ledgerId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("currentUserId") Long currentUserId);

  @Select(
      """
      SELECT
        DATE_FORMAT(txn_at, '%Y-%m') AS period,
        COALESCE(SUM(CASE WHEN type = 'INCOME'  THEN amount_total END), 0) AS income,
        COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_total END), 0) AS expense
      FROM transactions
      WHERE ledger_id = #{ledgerId}
        AND txn_at >= #{start}
        AND txn_at <  #{end}
        AND type IN ('INCOME','EXPENSE')
        AND (is_private = 0 OR created_by = #{currentUserId})
      GROUP BY DATE_FORMAT(txn_at, '%Y-%m')
      ORDER BY period ASC
      """)
  List<AggRows.MonthlyRow> monthlyStats(
      @Param("ledgerId") Long ledgerId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("currentUserId") Long currentUserId);

  @Select(
      """
      SELECT
        t.category_id AS categoryId,
        c.name AS categoryName,
        COALESCE(SUM(t.amount_total), 0) AS amount
      FROM transactions t
      LEFT JOIN categories c ON c.id = t.category_id
      WHERE t.ledger_id = #{ledgerId}
        AND t.txn_at >= #{start}
        AND t.txn_at <  #{end}
        AND t.type = 'EXPENSE'
        AND (t.is_private = 0 OR t.created_by = #{currentUserId})
      GROUP BY t.category_id, c.name
      ORDER BY amount DESC
      """)
  List<AggRows.CategoryRow> categoryStats(
      @Param("ledgerId") Long ledgerId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("currentUserId") Long currentUserId);

  @Select(
      """
      SELECT
        note AS label,
        COALESCE(SUM(amount_total), 0) AS amount
      FROM transactions
      WHERE ledger_id = #{ledgerId}
        AND txn_at >= #{start}
        AND txn_at <  #{end}
        AND type = 'EXPENSE'
        AND note IS NOT NULL
        AND note <> ''
        AND (is_private = 0 OR created_by = #{currentUserId})
      GROUP BY note
      ORDER BY amount DESC
      LIMIT #{limit}
      """)
  List<AggRows.MerchantRow> topMerchants(
      @Param("ledgerId") Long ledgerId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end,
      @Param("limit") int limit,
      @Param("currentUserId") Long currentUserId);

  @Select(
      """
      SELECT
        de.from_user_id AS userId,
        COALESCE(SUM(de.amount), 0) AS amount
      FROM debt_edges de
      LEFT JOIN transactions t ON t.id = de.transaction_id
      WHERE de.ledger_id = #{ledgerId}
        AND (
          de.transaction_id IS NULL
          OR t.is_private = 0
          OR t.created_by = #{currentUserId}
        )
      GROUP BY de.from_user_id
      """)
  List<AggRows.UserAmountRow> arByLedger(
      @Param("ledgerId") Long ledgerId, @Param("currentUserId") Long currentUserId);

  @Select(
      """
      SELECT
        de.to_user_id AS userId,
        COALESCE(SUM(de.amount), 0) AS amount
      FROM debt_edges de
      LEFT JOIN transactions t ON t.id = de.transaction_id
      WHERE de.ledger_id = #{ledgerId}
        AND (
          de.transaction_id IS NULL
          OR t.is_private = 0
          OR t.created_by = #{currentUserId}
        )
      GROUP BY de.to_user_id
      """)
  List<AggRows.UserAmountRow> apByLedger(
      @Param("ledgerId") Long ledgerId, @Param("currentUserId") Long currentUserId);
}
