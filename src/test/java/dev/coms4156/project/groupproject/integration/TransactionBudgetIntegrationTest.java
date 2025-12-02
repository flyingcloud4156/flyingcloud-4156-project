package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.SetBudgetRequest;
import dev.coms4156.project.groupproject.dto.SplitItem;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Budget;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.Transaction;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.BudgetMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.BudgetService;
import dev.coms4156.project.groupproject.service.TransactionService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for TransactionService and BudgetService working together.
 *
 * <p>Tests the critical integration between TransactionService and BudgetService: When a
 * transaction is created, BudgetService.checkBudgetAfterTransaction() is automatically called, and
 * budget alerts are correctly generated and returned in the transaction response.
 *
 * <p>This test uses real Spring beans and real database with @Transactional for rollback.
 */
@SpringBootTest
@Transactional
class TransactionBudgetIntegrationTest {

  @Autowired private BudgetService budgetService;
  @Autowired private TransactionService transactionService;
  @Autowired private BudgetMapper budgetMapper;
  @Autowired private TransactionMapper transactionMapper;
  @Autowired private LedgerMapper ledgerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private LedgerMemberMapper ledgerMemberMapper;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private Long testUserId;
  private Long testLedgerId;
  private Long testCategoryId;

  @BeforeEach
  void setUp() {
    User user = new User();
    user.setEmail("integration_budget_txn@example.com");
    user.setName("Budget Txn Test User");
    user.setPasswordHash("hash");
    userMapper.insert(user);
    testUserId = user.getId();

    UserView userView = new UserView(testUserId, "Budget Txn Test User");
    CurrentUserContext.set(userView);

    Ledger ledger = new Ledger();
    ledger.setName("Budget Txn Test Ledger");
    ledger.setOwnerId(testUserId);
    ledger.setLedgerType("GROUP_BALANCE");
    ledger.setBaseCurrency("USD");
    ledger.setShareStartDate(LocalDate.now());
    ledgerMapper.insert(ledger);
    testLedgerId = ledger.getId();

    LedgerMember member = new LedgerMember();
    member.setLedgerId(testLedgerId);
    member.setUserId(testUserId);
    member.setRole("OWNER");
    ledgerMemberMapper.insert(member);

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
  void whenCreateExpenseWithoutBudget_thenNoAlertReturned() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUserId);
    request.setAmountTotal(new BigDecimal("100.00"));
    request.setCurrency("USD");
    request.setNote("Test expense");
    request.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUserId);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    request.setSplits(Collections.singletonList(split));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, request);

    assertNotNull(response);
    assertNotNull(response.getTransactionId());
    assertNull(response.getBudgetAlert());

    Transaction savedTxn = transactionMapper.selectById(response.getTransactionId());
    assertNotNull(savedTxn);
    assertEquals(0, new BigDecimal("100.00").compareTo(savedTxn.getAmountTotal()));
  }

  @Test
  void whenCreateExpenseExceedsLedgerBudget_thenAlertIsGenerated() {
    SetBudgetRequest budgetRequest = new SetBudgetRequest();
    budgetRequest.setCategoryId(null);
    budgetRequest.setYear(LocalDateTime.now().getYear());
    budgetRequest.setMonth(LocalDateTime.now().getMonthValue());
    budgetRequest.setLimitAmount(new BigDecimal("1000.00"));

    budgetService.setBudget(testLedgerId, budgetRequest);

    Budget savedBudget =
        budgetMapper
            .findByLedgerAndYearMonth(
                testLedgerId, LocalDateTime.now().getYear(), LocalDateTime.now().getMonthValue())
            .get(0);
    assertNotNull(savedBudget);
    assertEquals(0, new BigDecimal("1000.00").compareTo(savedBudget.getLimitAmount()));

    CreateTransactionRequest txnRequest = new CreateTransactionRequest();
    txnRequest.setTxnAt(LocalDateTime.now());
    txnRequest.setType("EXPENSE");
    txnRequest.setCategoryId(testCategoryId);
    txnRequest.setPayerId(testUserId);
    txnRequest.setAmountTotal(new BigDecimal("900.00"));
    txnRequest.setCurrency("USD");
    txnRequest.setNote("Large expense");
    txnRequest.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUserId);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    txnRequest.setSplits(Collections.singletonList(split));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, txnRequest);

    assertNotNull(response);
    assertNotNull(response.getTransactionId());
    assertNotNull(response.getBudgetAlert());
    assertTrue(response.getBudgetAlert().length() > 0);
  }

  @Test
  void whenCreateExpenseExceedsCategoryBudget_thenCategoryAlertIsReturned() {
    SetBudgetRequest budgetRequest = new SetBudgetRequest();
    budgetRequest.setCategoryId(testCategoryId);
    budgetRequest.setYear(LocalDateTime.now().getYear());
    budgetRequest.setMonth(LocalDateTime.now().getMonthValue());
    budgetRequest.setLimitAmount(new BigDecimal("50.00"));

    budgetService.setBudget(testLedgerId, budgetRequest);

    CreateTransactionRequest txnRequest = new CreateTransactionRequest();
    txnRequest.setTxnAt(LocalDateTime.now());
    txnRequest.setType("EXPENSE");
    txnRequest.setCategoryId(testCategoryId);
    txnRequest.setPayerId(testUserId);
    txnRequest.setAmountTotal(new BigDecimal("100.00"));
    txnRequest.setCurrency("USD");
    txnRequest.setNote("Category expense");
    txnRequest.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUserId);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    txnRequest.setSplits(Collections.singletonList(split));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, txnRequest);

    assertNotNull(response);
    assertNotNull(response.getBudgetAlert());
  }

  @Test
  void whenCreateIncomeTransaction_thenNoBudgetCheckPerformed() {
    SetBudgetRequest budgetRequest = new SetBudgetRequest();
    budgetRequest.setCategoryId(null);
    budgetRequest.setYear(LocalDateTime.now().getYear());
    budgetRequest.setMonth(LocalDateTime.now().getMonthValue());
    budgetRequest.setLimitAmount(new BigDecimal("1000.00"));

    budgetService.setBudget(testLedgerId, budgetRequest);

    CreateTransactionRequest txnRequest = new CreateTransactionRequest();
    txnRequest.setTxnAt(LocalDateTime.now());
    txnRequest.setType("INCOME");
    txnRequest.setCategoryId(testCategoryId);
    txnRequest.setPayerId(testUserId);
    txnRequest.setAmountTotal(new BigDecimal("5000.00"));
    txnRequest.setCurrency("USD");
    txnRequest.setNote("Salary");
    txnRequest.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUserId);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    txnRequest.setSplits(Collections.singletonList(split));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, txnRequest);

    assertNotNull(response);
    assertNull(response.getBudgetAlert());
  }

  @Test
  void verifyTransactionAndBudgetDataSharing_viaLedgerIdAndCategoryId() {
    SetBudgetRequest budgetRequest = new SetBudgetRequest();
    budgetRequest.setCategoryId(testCategoryId);
    budgetRequest.setYear(2025);
    budgetRequest.setMonth(12);
    budgetRequest.setLimitAmount(new BigDecimal("2000.00"));

    budgetService.setBudget(testLedgerId, budgetRequest);

    Budget savedBudget = budgetMapper.findByLedgerAndYearMonth(testLedgerId, 2025, 12).get(0);
    assertNotNull(savedBudget);

    CreateTransactionRequest txnRequest = new CreateTransactionRequest();
    txnRequest.setTxnAt(LocalDateTime.of(2025, 12, 15, 10, 0));
    txnRequest.setType("EXPENSE");
    txnRequest.setCategoryId(testCategoryId);
    txnRequest.setPayerId(testUserId);
    txnRequest.setAmountTotal(new BigDecimal("750.00"));
    txnRequest.setCurrency("USD");
    txnRequest.setNote("Test persistence");
    txnRequest.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUserId);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    txnRequest.setSplits(Collections.singletonList(split));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, txnRequest);

    Transaction savedTxn = transactionMapper.selectById(response.getTransactionId());
    assertNotNull(savedTxn);
    assertEquals(testLedgerId, savedTxn.getLedgerId());
    assertEquals(testCategoryId, savedTxn.getCategoryId());
    assertEquals(testLedgerId, savedBudget.getLedgerId());
    assertEquals(testCategoryId, savedBudget.getCategoryId());
  }

  @Test
  void testSetBudget_insertsAndUpdatesBudgetCorrectly() {
    SetBudgetRequest setBudgetRequest = new SetBudgetRequest();
    setBudgetRequest.setCategoryId(null);
    setBudgetRequest.setYear(2025);
    setBudgetRequest.setMonth(1);
    setBudgetRequest.setLimitAmount(new BigDecimal("5000.00"));

    budgetService.setBudget(testLedgerId, setBudgetRequest);

    Budget savedBudget =
        budgetMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Budget>()
                .eq(Budget::getLedgerId, testLedgerId)
                .eq(Budget::getYear, 2025)
                .eq(Budget::getMonth, 1)
                .isNull(Budget::getCategoryId));

    assertNotNull(savedBudget);
    assertEquals(0, new BigDecimal("5000.00").compareTo(savedBudget.getLimitAmount()));
  }

  @Test
  void testGetBudgetStatus_queriesSumExpenseByTimeRange() {
    SetBudgetRequest setBudgetRequest = new SetBudgetRequest();
    setBudgetRequest.setCategoryId(null);
    setBudgetRequest.setYear(LocalDateTime.now().getYear());
    setBudgetRequest.setMonth(LocalDateTime.now().getMonthValue());
    setBudgetRequest.setLimitAmount(new BigDecimal("10000.00"));

    budgetService.setBudget(testLedgerId, setBudgetRequest);

    CreateTransactionRequest txnRequest = new CreateTransactionRequest();
    txnRequest.setTxnAt(LocalDateTime.now());
    txnRequest.setType("EXPENSE");
    txnRequest.setCategoryId(testCategoryId);
    txnRequest.setPayerId(testUserId);
    txnRequest.setAmountTotal(new BigDecimal("2000.00"));
    txnRequest.setCurrency("USD");
    txnRequest.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUserId);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    txnRequest.setSplits(Collections.singletonList(split));

    transactionService.createTransaction(testLedgerId, txnRequest);

    dev.coms4156.project.groupproject.dto.BudgetStatusResponse response =
        budgetService.getBudgetStatus(
            testLedgerId, LocalDateTime.now().getYear(), LocalDateTime.now().getMonthValue());

    assertNotNull(response);
    assertNotNull(response.getItems());
    assertTrue(
        response.getItems().stream()
            .anyMatch(b -> b.getSpentAmount().compareTo(BigDecimal.ZERO) > 0));
  }
}
