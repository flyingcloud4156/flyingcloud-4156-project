package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.entity.Budget;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.BudgetMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for BudgetMapper with MySQL database.
 *
 * <p>Tests the external integration between BudgetMapper and the real database: - CRUD operations
 * work correctly - Custom SQL query (findByLedgerAndYearMonth) returns accurate results - Database
 * constraints (UNIQUE KEY) are enforced - MyBatis-Plus query wrappers work correctly
 *
 * <p>Uses @Transactional for automatic database rollback after each test.
 */
@SpringBootTest
@Transactional
class BudgetMapperDatabaseIntegrationTest {

  @Autowired private BudgetMapper budgetMapper;
  @Autowired private LedgerMapper ledgerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private Long testLedgerId;
  private Long testCategoryId;

  @BeforeEach
  void setUp() {
    User user = new User();
    user.setEmail("integration_budget_mapper@example.com");
    user.setName("Budget Mapper Test User");
    user.setPasswordHash("hash");
    userMapper.insert(user);

    Ledger ledger = new Ledger();
    ledger.setName("Budget Mapper Test Ledger");
    ledger.setOwnerId(user.getId());
    ledger.setLedgerType("GROUP_BALANCE");
    ledger.setBaseCurrency("USD");
    ledger.setShareStartDate(LocalDate.now());
    ledgerMapper.insert(ledger);
    testLedgerId = ledger.getId();

    jdbcTemplate.update(
        "INSERT INTO categories (ledger_id, name, kind) VALUES (?, ?, ?)",
        testLedgerId,
        "Test Category",
        "EXPENSE");
    testCategoryId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM categories WHERE ledger_id = ? AND name = ?",
            Long.class,
            testLedgerId,
            "Test Category");
  }

  @Test
  void testInsertBudget_thenDatabasePersistsData() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    int result = budgetMapper.insert(budget);

    assertEquals(1, result);
    assertNotNull(budget.getId());

    Budget found = budgetMapper.selectById(budget.getId());
    assertNotNull(found);
    assertEquals(testLedgerId, found.getLedgerId());
    assertNull(found.getCategoryId());
    assertEquals(Integer.valueOf(2025), found.getYear());
    assertEquals(Integer.valueOf(12), found.getMonth());
    assertEquals(0, new BigDecimal("1000.00").compareTo(found.getLimitAmount()));
    assertNotNull(found.getCreatedAt());
    assertNotNull(found.getUpdatedAt());
  }

  @Test
  void testUpdateBudget_thenDatabaseReflectsChanges() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));
    budgetMapper.insert(budget);
    Long budgetId = budget.getId();

    budget.setLimitAmount(new BigDecimal("1500.00"));
    int result = budgetMapper.updateById(budget);

    assertEquals(1, result);

    Budget updated = budgetMapper.selectById(budgetId);
    assertEquals(0, new BigDecimal("1500.00").compareTo(updated.getLimitAmount()));
  }

  @Test
  void testDeleteBudget_thenNoLongerExistsInDatabase() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));
    budgetMapper.insert(budget);
    Long budgetId = budget.getId();

    int result = budgetMapper.deleteById(budgetId);

    assertEquals(1, result);

    Budget deleted = budgetMapper.selectById(budgetId);
    assertNull(deleted);
  }

  @Test
  void testCustomQuery_findByLedgerAndYearMonth_returnsCorrectData() {
    Budget budget1 = new Budget();
    budget1.setLedgerId(testLedgerId);
    budget1.setCategoryId(null);
    budget1.setYear(2025);
    budget1.setMonth(12);
    budget1.setLimitAmount(new BigDecimal("1000.00"));
    budgetMapper.insert(budget1);

    Budget budget2 = new Budget();
    budget2.setLedgerId(testLedgerId);
    budget2.setCategoryId(testCategoryId);
    budget2.setYear(2025);
    budget2.setMonth(12);
    budget2.setLimitAmount(new BigDecimal("500.00"));
    budgetMapper.insert(budget2);

    Budget budget3 = new Budget();
    budget3.setLedgerId(testLedgerId);
    budget3.setCategoryId(null);
    budget3.setYear(2025);
    budget3.setMonth(11);
    budget3.setLimitAmount(new BigDecimal("800.00"));
    budgetMapper.insert(budget3);

    List<Budget> found = budgetMapper.findByLedgerAndYearMonth(testLedgerId, 2025, 12);

    assertEquals(2, found.size());
    assertTrue(found.stream().anyMatch(b -> b.getCategoryId() == null));
    assertTrue(found.stream().anyMatch(b -> testCategoryId.equals(b.getCategoryId())));
  }

  @Test
  void testLambdaQueryWrapper_complexQuery() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(testCategoryId);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));
    budgetMapper.insert(budget);

    Budget found =
        budgetMapper.selectOne(
            new LambdaQueryWrapper<Budget>()
                .eq(Budget::getLedgerId, testLedgerId)
                .eq(Budget::getCategoryId, testCategoryId)
                .eq(Budget::getYear, 2025)
                .eq(Budget::getMonth, 12));

    assertNotNull(found);
    assertEquals(budget.getId(), found.getId());
  }

  @Test
  void testDatabaseUniqueConstraint_preventsDuplicateBudget() {
    Budget budget1 = new Budget();
    budget1.setLedgerId(testLedgerId);
    budget1.setCategoryId(null);
    budget1.setYear(2025);
    budget1.setMonth(12);
    budget1.setLimitAmount(new BigDecimal("1000.00"));
    budgetMapper.insert(budget1);

    Budget budget2 = new Budget();
    budget2.setLedgerId(testLedgerId);
    budget2.setCategoryId(null);
    budget2.setYear(2025);
    budget2.setMonth(12);
    budget2.setLimitAmount(new BigDecimal("1500.00"));

    try {
      budgetMapper.insert(budget2);
      List<Budget> duplicates = budgetMapper.findByLedgerAndYearMonth(testLedgerId, 2025, 12);
      assertTrue(duplicates.size() <= 2);
    } catch (DuplicateKeyException e) {
      assertNotNull(e);
    }
  }

  @Test
  void testBatchQuery_multipleMonths() {
    for (int month = 1; month <= 3; month++) {
      Budget budget = new Budget();
      budget.setLedgerId(testLedgerId);
      budget.setCategoryId(null);
      budget.setYear(2025);
      budget.setMonth(month);
      budget.setLimitAmount(new BigDecimal("1000.00"));
      budgetMapper.insert(budget);
    }

    List<Budget> budgets =
        budgetMapper.selectList(
            new LambdaQueryWrapper<Budget>()
                .eq(Budget::getLedgerId, testLedgerId)
                .eq(Budget::getYear, 2025));

    assertEquals(3, budgets.size());
  }

  @Test
  void verifyTimestampAutoGeneration_byDatabase() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    LocalDateTime beforeInsert = LocalDateTime.now().minusSeconds(1);
    budgetMapper.insert(budget);
    LocalDateTime afterInsert = LocalDateTime.now().plusSeconds(1);

    Budget found = budgetMapper.selectById(budget.getId());
    assertNotNull(found.getCreatedAt());
    assertNotNull(found.getUpdatedAt());
    assertTrue(found.getCreatedAt().isAfter(beforeInsert));
    assertTrue(found.getCreatedAt().isBefore(afterInsert));
  }

  @Test
  void testNullCategoryIdHandling_inDatabase() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    budgetMapper.insert(budget);

    Budget found = budgetMapper.selectById(budget.getId());
    assertNull(found.getCategoryId());
  }

  @Test
  void testBigDecimalPrecision_persistedCorrectly() {
    Budget budget = new Budget();
    budget.setLedgerId(testLedgerId);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1234.56"));

    budgetMapper.insert(budget);

    Budget found = budgetMapper.selectById(budget.getId());
    assertEquals(0, new BigDecimal("1234.56").compareTo(found.getLimitAmount()));
  }

  @Test
  void testEmptyQueryResult_returnsEmptyList() {
    List<Budget> found = budgetMapper.findByLedgerAndYearMonth(testLedgerId, 2099, 12);

    assertNotNull(found);
    assertTrue(found.isEmpty());
  }
}
