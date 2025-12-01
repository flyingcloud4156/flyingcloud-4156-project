package dev.coms4156.project.groupproject.mapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * SQL contract tests for {@link AnalyticsAggMapper}.
 *
 * <p>These are pure unit tests (no DB). We validate critical SQL invariants:
 *
 * <ul>
 *   <li>Ledger scoping by ledger_id
 *   <li>Time window filtering (txn_at >= start AND txn_at < end)
 *   <li>Privacy filter: (is_private=0 OR created_by=currentUserId) for transaction-derived data
 *   <li>Group/order/limit clauses exist where needed
 * </ul>
 *
 * <p>The assertions use only high-value substrings (not full SQL equality) to avoid brittleness.
 */
class AnalyticsAggMapperSqlContractTest {

  private static String sqlOf(String methodName, Class<?>... argTypes) throws Exception {
    Method m = AnalyticsAggMapper.class.getMethod(methodName, argTypes);
    Select s = m.getAnnotation(Select.class);
    assertNotNull(s, "Expected @Select on method: " + methodName);
    return String.join("\n", s.value());
  }

  @Test
  void sumIncomeExpense_sqlMustContainLedgerTimeTypeAndPrivacyFilters() throws Exception {
    String sql =
        sqlOf("sumIncomeExpense", Long.class, LocalDateTime.class, LocalDateTime.class, Long.class);

    assertTrue(sql.contains("WHERE ledger_id = #{ledgerId}"));
    assertTrue(sql.contains("txn_at >= #{start}"));
    assertTrue(sql.contains("txn_at <  #{end}"));
    assertTrue(sql.contains("type IN ('INCOME','EXPENSE')"));
    assertTrue(sql.contains("(is_private = 0 OR created_by = #{currentUserId})"));
  }

  @Test
  void monthlyStats_sqlMustGroupByYearMonthAndApplyPrivacy() throws Exception {
    String sql =
        sqlOf("monthlyStats", Long.class, LocalDateTime.class, LocalDateTime.class, Long.class);

    assertTrue(sql.contains("DATE_FORMAT(txn_at, '%Y-%m') AS period"));
    assertTrue(sql.contains("GROUP BY DATE_FORMAT(txn_at, '%Y-%m')"));
    assertTrue(sql.contains("ORDER BY period ASC"));
    assertTrue(sql.contains("(is_private = 0 OR created_by = #{currentUserId})"));
  }

  @Test
  void categoryStats_sqlMustJoinCategoriesAndApplyPrivacy() throws Exception {
    String sql =
        sqlOf("categoryStats", Long.class, LocalDateTime.class, LocalDateTime.class, Long.class);

    assertTrue(sql.contains("LEFT JOIN categories c ON c.id = t.category_id"));
    assertTrue(sql.contains("t.type = 'EXPENSE'"));
    assertTrue(sql.contains("(t.is_private = 0 OR t.created_by = #{currentUserId})"));
    assertTrue(sql.contains("ORDER BY amount DESC"));
  }

  @Test
  void topMerchants_sqlMustLimitAndGroupByNoteAndApplyPrivacy() throws Exception {
    String sql =
        sqlOf(
            "topMerchants",
            Long.class,
            LocalDateTime.class,
            LocalDateTime.class,
            int.class,
            Long.class);

    assertTrue(sql.contains("GROUP BY note"));
    assertTrue(sql.contains("LIMIT #{limit}"));
    assertTrue(sql.contains("type = 'EXPENSE'"));
    assertTrue(sql.contains("(is_private = 0 OR created_by = #{currentUserId})"));
  }

  @Test
  void arAp_sqlMustHandleNullTransactionIdAndPrivacyViaJoin() throws Exception {
    String arSql = sqlOf("arByLedger", Long.class, Long.class);
    assertTrue(arSql.contains("LEFT JOIN transactions t ON t.id = de.transaction_id"));
    assertTrue(arSql.contains("de.transaction_id IS NULL"));
    assertTrue(arSql.contains("t.is_private = 0"));
    assertTrue(arSql.contains("t.created_by = #{currentUserId}"));

    String apSql = sqlOf("apByLedger", Long.class, Long.class);
    assertTrue(apSql.contains("LEFT JOIN transactions t ON t.id = de.transaction_id"));
    assertTrue(apSql.contains("de.transaction_id IS NULL"));
    assertTrue(apSql.contains("t.is_private = 0"));
    assertTrue(apSql.contains("t.created_by = #{currentUserId}"));
  }
}
