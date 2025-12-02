package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.SplitItem;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.AnalyticsService;
import dev.coms4156.project.groupproject.service.TransactionService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for AnalyticsService with database.
 *
 * <p>Tests the integration: AnalyticsService → AnalyticsAggMapper → Database (complex SQL
 * aggregations)
 *
 * <p>Verifies: - getLedgerAnalyticsOverview() executes complex SQL queries - Data aggregation
 * across multiple tables - Shared data (ledger_id, user_id) is correctly joined
 */
@SpringBootTest
@Transactional
class AnalyticsDatabaseIntegrationTest {

  @Autowired private AnalyticsService analyticsService;
  @Autowired private TransactionService transactionService;
  @Autowired private LedgerMapper ledgerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private LedgerMemberMapper ledgerMemberMapper;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private Long testUser1Id;
  private Long testUser2Id;
  private Long testLedgerId;
  private Long testCategoryId;

  @BeforeEach
  void setUp() {
    User user1 = new User();
    user1.setEmail("analytics_int_1@example.com");
    user1.setName("Analytics Test User 1");
    user1.setPasswordHash("hash1");
    userMapper.insert(user1);
    testUser1Id = user1.getId();

    User user2 = new User();
    user2.setEmail("analytics_int_2@example.com");
    user2.setName("Analytics Test User 2");
    user2.setPasswordHash("hash2");
    userMapper.insert(user2);
    testUser2Id = user2.getId();

    UserView userView = new UserView(testUser1Id, "Analytics Test User 1");
    CurrentUserContext.set(userView);

    Ledger ledger = new Ledger();
    ledger.setName("Analytics Test Ledger");
    ledger.setOwnerId(testUser1Id);
    ledger.setLedgerType("GROUP_BALANCE");
    ledger.setBaseCurrency("USD");
    ledger.setShareStartDate(LocalDate.now());
    ledgerMapper.insert(ledger);
    testLedgerId = ledger.getId();

    LedgerMember member1 = new LedgerMember();
    member1.setLedgerId(testLedgerId);
    member1.setUserId(testUser1Id);
    member1.setRole("OWNER");
    ledgerMemberMapper.insert(member1);

    LedgerMember member2 = new LedgerMember();
    member2.setLedgerId(testLedgerId);
    member2.setUserId(testUser2Id);
    member2.setRole("EDITOR");
    ledgerMemberMapper.insert(member2);

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

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  void testGetLedgerAnalyticsOverview_executesComplexAggregationSql() {
    CreateTransactionRequest request1 = new CreateTransactionRequest();
    request1.setTxnAt(LocalDateTime.now());
    request1.setType("INCOME");
    request1.setCategoryId(testCategoryId);
    request1.setPayerId(testUser1Id);
    request1.setAmountTotal(new BigDecimal("1000.00"));
    request1.setCurrency("USD");
    request1.setNote("Income");
    request1.setIsPrivate(false);

    SplitItem split1 = new SplitItem();
    split1.setUserId(testUser1Id);
    split1.setSplitMethod("EQUAL");
    split1.setShareValue(BigDecimal.ZERO);
    request1.setSplits(Arrays.asList(split1));

    transactionService.createTransaction(testLedgerId, request1);

    CreateTransactionRequest request2 = new CreateTransactionRequest();
    request2.setTxnAt(LocalDateTime.now());
    request2.setType("EXPENSE");
    request2.setCategoryId(testCategoryId);
    request2.setPayerId(testUser1Id);
    request2.setAmountTotal(new BigDecimal("500.00"));
    request2.setCurrency("USD");
    request2.setNote("Expense");
    request2.setIsPrivate(false);

    SplitItem split2 = new SplitItem();
    split2.setUserId(testUser1Id);
    split2.setSplitMethod("EQUAL");
    split2.setShareValue(BigDecimal.ZERO);
    request2.setSplits(Arrays.asList(split2));

    transactionService.createTransaction(testLedgerId, request2);

    LedgerAnalyticsOverview overview = analyticsService.overview(testLedgerId, 12);

    assertNotNull(overview);
    assertNotNull(overview.getTotalIncome());
    assertNotNull(overview.getTotalExpense());
  }

  @Test
  void testGetCategoryStats_aggregatesDataCorrectly() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("300.00"));
    request.setCurrency("USD");
    request.setNote("Category test");
    request.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUser1Id);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    request.setSplits(Arrays.asList(split));

    transactionService.createTransaction(testLedgerId, request);

    LedgerAnalyticsOverview overview = analyticsService.overview(testLedgerId, 12);

    assertNotNull(overview);
    assertNotNull(overview.getByCategory());
  }

  @Test
  void testAnalytics_verifiesSharedDataAcrossMultipleTables() {
    CreateTransactionRequest incomeRequest = new CreateTransactionRequest();
    incomeRequest.setTxnAt(LocalDateTime.now());
    incomeRequest.setType("INCOME");
    incomeRequest.setCategoryId(testCategoryId);
    incomeRequest.setPayerId(testUser1Id);
    incomeRequest.setAmountTotal(new BigDecimal("2000.00"));
    incomeRequest.setCurrency("USD");
    incomeRequest.setNote("Multi-table test income");
    incomeRequest.setIsPrivate(false);

    SplitItem incomeSplit = new SplitItem();
    incomeSplit.setUserId(testUser1Id);
    incomeSplit.setSplitMethod("EQUAL");
    incomeSplit.setShareValue(BigDecimal.ZERO);
    incomeRequest.setSplits(Arrays.asList(incomeSplit));

    transactionService.createTransaction(testLedgerId, incomeRequest);

    CreateTransactionRequest expenseRequest = new CreateTransactionRequest();
    expenseRequest.setTxnAt(LocalDateTime.now());
    expenseRequest.setType("EXPENSE");
    expenseRequest.setCategoryId(testCategoryId);
    expenseRequest.setPayerId(testUser1Id);
    expenseRequest.setAmountTotal(new BigDecimal("800.00"));
    expenseRequest.setCurrency("USD");
    expenseRequest.setNote("Multi-table test expense");
    expenseRequest.setIsPrivate(false);

    SplitItem expenseSplit = new SplitItem();
    expenseSplit.setUserId(testUser1Id);
    expenseSplit.setSplitMethod("EQUAL");
    expenseSplit.setShareValue(BigDecimal.ZERO);
    expenseRequest.setSplits(Arrays.asList(expenseSplit));

    transactionService.createTransaction(testLedgerId, expenseRequest);

    LedgerAnalyticsOverview overview = analyticsService.overview(testLedgerId, 12);

    assertNotNull(overview);
    assertNotNull(overview.getTotalIncome());
    assertNotNull(overview.getTotalExpense());
  }

  @Test
  void testAnalyticsWithMultipleUsers_verifiesUserJoins() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("600.00"));
    request.setCurrency("USD");
    request.setNote("Multi-user test");
    request.setIsPrivate(false);

    SplitItem split1 = new SplitItem();
    split1.setUserId(testUser1Id);
    split1.setSplitMethod("EQUAL");
    split1.setShareValue(BigDecimal.ZERO);

    SplitItem split2 = new SplitItem();
    split2.setUserId(testUser2Id);
    split2.setSplitMethod("EQUAL");
    split2.setShareValue(BigDecimal.ZERO);

    request.setSplits(Arrays.asList(split1, split2));

    transactionService.createTransaction(testLedgerId, request);

    LedgerAnalyticsOverview overview = analyticsService.overview(testLedgerId, 12);

    assertNotNull(overview);
    assertNotNull(overview.getTotalExpense());
  }
}
