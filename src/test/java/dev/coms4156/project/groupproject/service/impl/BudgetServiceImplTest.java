package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.BudgetStatusItem;
import dev.coms4156.project.groupproject.dto.BudgetStatusResponse;
import dev.coms4156.project.groupproject.dto.SetBudgetRequest;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Budget;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.mapper.BudgetMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive unit tests for {@link BudgetServiceImpl}.
 *
 * <p>Testing Strategy:
 *
 * <ul>
 *   <li>All mapper dependencies are mocked to isolate service logic
 *   <li>Test expectations are derived from independent business rules (threshold 0.8, ratio
 *       calculations)
 *   <li>Covers equivalence partitions: valid/invalid inputs, authenticated/unauthenticated users
 *   <li>Covers boundary values: ratio at 0, 0.79, 0.8, 0.99, 1.0, 1.5
 *   <li>Covers loop variations: 0, 1, 2, many budgets
 *   <li>Tests all branches including permission checks, upsert logic, alert priority
 *   <li>Expected values calculated independently: ratio = spent / limit, status based on thresholds
 *   <li>Target: 100% statement and branch coverage
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceImplTest {

  @Mock private BudgetMapper budgetMapper;
  @Mock private TransactionMapper transactionMapper;
  @Mock private LedgerMapper ledgerMapper;
  @Mock private LedgerMemberMapper ledgerMemberMapper;

  @InjectMocks private BudgetServiceImpl service;

  private UserView testUser;
  private Ledger testLedger;

  @BeforeEach
  void setUp() {
    testUser = new UserView();
    testUser.setId(100L);
    testUser.setName("alice");

    testLedger = new Ledger();
    testLedger.setId(1L);
    testLedger.setName("Travel Ledger");
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  // ========== setBudget() Tests ==========

  @Test
  @DisplayName("setBudget: not logged in -> throws exception")
  void setBudget_notLoggedIn_throwsException() {
    CurrentUserContext.clear();

    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1000.00"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.setBudget(1L, request));
    assertEquals("Not logged in", ex.getMessage());
  }

  @Test
  @DisplayName("setBudget: ledger not found -> throws exception")
  void setBudget_ledgerNotFound_throwsException() {
    CurrentUserContext.set(testUser);
    doReturn(null).when(ledgerMapper).selectById(1L);

    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1000.00"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.setBudget(1L, request));
    assertEquals("Ledger not found", ex.getMessage());
  }

  @Test
  @DisplayName("setBudget: not a member -> throws exception")
  void setBudget_notMember_throwsException() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);
    doReturn(null).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1000.00"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.setBudget(1L, request));
    assertEquals("Not a member of this ledger", ex.getMessage());
  }

  @Test
  @DisplayName("setBudget: member with MEMBER role -> throws exception")
  void setBudget_insufficientPermissions_throwsException() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1000.00"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.setBudget(1L, request));
    assertEquals("Insufficient permissions. Only OWNER or ADMIN can set budgets.", ex.getMessage());
  }

  @Test
  @DisplayName("setBudget: create new ledger-level budget (OWNER role) -> success")
  void setBudget_createLedgerLevelBudget_owner_success() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("OWNER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    doReturn(null).when(budgetMapper).selectOne(any(LambdaQueryWrapper.class));

    doAnswer(
            invocation -> {
              Budget budget = invocation.getArgument(0, Budget.class);
              budget.setId(10L);
              return 1;
            })
        .when(budgetMapper)
        .insert(argThat((Budget b) -> b != null));

    SetBudgetRequest request = new SetBudgetRequest();
    request.setCategoryId(null);
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1000.00"));

    service.setBudget(1L, request);

    verify(budgetMapper, times(1))
        .insert(
            argThat(
                (Budget b) ->
                    b.getLedgerId().equals(1L)
                        && b.getCategoryId() == null
                        && b.getYear().equals(2025)
                        && b.getMonth().equals(12)
                        && b.getLimitAmount().compareTo(new BigDecimal("1000.00")) == 0
                        && b.getCreatedAt() != null
                        && b.getUpdatedAt() != null));
    verify(budgetMapper, never()).updateById(argThat((Budget b) -> true));
  }

  @Test
  @DisplayName("setBudget: create new category-level budget (ADMIN role) -> success")
  void setBudget_createCategoryLevelBudget_admin_success() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("ADMIN");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    doReturn(null).when(budgetMapper).selectOne(any(LambdaQueryWrapper.class));

    doAnswer(
            invocation -> {
              Budget budget = invocation.getArgument(0, Budget.class);
              budget.setId(20L);
              return 1;
            })
        .when(budgetMapper)
        .insert(argThat((Budget b) -> b != null));

    SetBudgetRequest request = new SetBudgetRequest();
    request.setCategoryId(5L);
    request.setYear(2025);
    request.setMonth(11);
    request.setLimitAmount(new BigDecimal("500.00"));

    service.setBudget(1L, request);

    verify(budgetMapper, times(1))
        .insert(
            argThat(
                (Budget b) ->
                    b.getLedgerId().equals(1L)
                        && b.getCategoryId().equals(5L)
                        && b.getYear().equals(2025)
                        && b.getMonth().equals(11)
                        && b.getLimitAmount().compareTo(new BigDecimal("500.00")) == 0));
  }

  @Test
  @DisplayName("setBudget: update existing budget -> success")
  void setBudget_updateExistingBudget_success() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("OWNER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget existingBudget = new Budget();
    existingBudget.setId(30L);
    existingBudget.setLedgerId(1L);
    existingBudget.setCategoryId(null);
    existingBudget.setYear(2025);
    existingBudget.setMonth(12);
    existingBudget.setLimitAmount(new BigDecimal("800.00"));
    doReturn(existingBudget).when(budgetMapper).selectOne(any(LambdaQueryWrapper.class));

    doReturn(1).when(budgetMapper).updateById(argThat((Budget b) -> b != null));

    SetBudgetRequest request = new SetBudgetRequest();
    request.setCategoryId(null);
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1200.00"));

    service.setBudget(1L, request);

    verify(budgetMapper, times(1))
        .updateById(
            argThat(
                (Budget b) ->
                    b.getId().equals(30L)
                        && b.getLimitAmount().compareTo(new BigDecimal("1200.00")) == 0
                        && b.getUpdatedAt() != null));
    verify(budgetMapper, never()).insert(argThat((Budget b) -> true));
  }

  @Test
  @DisplayName("setBudget: boundary year = 2020 -> success")
  void setBudget_boundaryYearMin_success() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("OWNER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    doReturn(null).when(budgetMapper).selectOne(any(LambdaQueryWrapper.class));
    doAnswer(
            invocation -> {
              Budget budget = invocation.getArgument(0, Budget.class);
              budget.setId(40L);
              return 1;
            })
        .when(budgetMapper)
        .insert(argThat((Budget b) -> b != null));

    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2020);
    request.setMonth(1);
    request.setLimitAmount(new BigDecimal("100.00"));

    service.setBudget(1L, request);

    verify(budgetMapper, times(1)).insert(argThat((Budget b) -> b.getYear().equals(2020)));
  }

  @Test
  @DisplayName("setBudget: boundary month = 1 and month = 12 -> success")
  void setBudget_boundaryMonths_success() {
    CurrentUserContext.set(testUser);
    doReturn(testLedger).when(ledgerMapper).selectById(1L);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("OWNER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    doReturn(null).when(budgetMapper).selectOne(any(LambdaQueryWrapper.class));
    doAnswer(
            invocation -> {
              Budget budget = invocation.getArgument(0, Budget.class);
              budget.setId(50L);
              return 1;
            })
        .when(budgetMapper)
        .insert(argThat((Budget b) -> b != null));

    SetBudgetRequest request1 = new SetBudgetRequest();
    request1.setYear(2025);
    request1.setMonth(1);
    request1.setLimitAmount(new BigDecimal("100.00"));
    service.setBudget(1L, request1);

    SetBudgetRequest request2 = new SetBudgetRequest();
    request2.setYear(2025);
    request2.setMonth(12);
    request2.setLimitAmount(new BigDecimal("200.00"));
    service.setBudget(1L, request2);

    verify(budgetMapper, times(2)).insert(argThat((Budget b) -> b != null));
  }

  // ========== getBudgetStatus() Tests ==========

  @Test
  @DisplayName("getBudgetStatus: not logged in -> throws exception")
  void getBudgetStatus_notLoggedIn_throwsException() {
    CurrentUserContext.clear();

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getBudgetStatus(1L, 2025, 12));
    assertEquals("Not logged in", ex.getMessage());
  }

  @Test
  @DisplayName("getBudgetStatus: not a member -> throws exception")
  void getBudgetStatus_notMember_throwsException() {
    CurrentUserContext.set(testUser);
    doReturn(null).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getBudgetStatus(1L, 2025, 12));
    assertEquals("Not a member of this ledger", ex.getMessage());
  }

  @Test
  @DisplayName("getBudgetStatus: no budgets -> returns empty list")
  void getBudgetStatus_noBudgets_returnsEmptyList() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    doReturn(Collections.emptyList()).when(budgetMapper).findByLedgerAndYearMonth(1L, 2025, 12);

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 12);

    assertNotNull(response);
    assertEquals(0, response.getItems().size());
  }

  @Test
  @DisplayName("getBudgetStatus: one budget with no spending -> status OK, ratio 0.0000")
  void getBudgetStatus_oneBudgetNoSpending_statusOk() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget = new Budget();
    budget.setId(10L);
    budget.setLedgerId(1L);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 12);

    doReturn(BigDecimal.ZERO)
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 12);

    assertEquals(1, response.getItems().size());
    BudgetStatusItem item = response.getItems().get(0);
    assertEquals(10L, item.getBudgetId());
    assertNull(item.getCategoryId());
    assertEquals("Total Budget", item.getCategoryName());
    assertEquals(0, new BigDecimal("1000.00").compareTo(item.getLimitAmount()));
    assertEquals(0, BigDecimal.ZERO.compareTo(item.getSpentAmount()));
    assertEquals("0.0000", item.getRatio());
    assertEquals("OK", item.getStatus());
  }

  @Test
  @DisplayName("getBudgetStatus: ratio = 0.79 -> status OK")
  void getBudgetStatus_ratioJustBelowThreshold_statusOk() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget = new Budget();
    budget.setId(11L);
    budget.setLedgerId(1L);
    budget.setCategoryId(5L);
    budget.setYear(2025);
    budget.setMonth(12);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 12);

    doReturn(new BigDecimal("790.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 12);

    BudgetStatusItem item = response.getItems().get(0);
    assertEquals("Category 5", item.getCategoryName());
    assertEquals(0, new BigDecimal("790.00").compareTo(item.getSpentAmount()));
    assertEquals("0.7900", item.getRatio());
    assertEquals("OK", item.getStatus());
  }

  @Test
  @DisplayName("getBudgetStatus: ratio = 0.80 -> status NEAR_LIMIT")
  void getBudgetStatus_ratioAtThreshold_statusNearLimit() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget = new Budget();
    budget.setId(12L);
    budget.setLedgerId(1L);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(11);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 11);

    doReturn(new BigDecimal("800.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 11);

    BudgetStatusItem item = response.getItems().get(0);
    assertEquals(0, new BigDecimal("800.00").compareTo(item.getSpentAmount()));
    assertEquals("0.8000", item.getRatio());
    assertEquals("NEAR_LIMIT", item.getStatus());
  }

  @Test
  @DisplayName("getBudgetStatus: ratio = 0.99 -> status NEAR_LIMIT")
  void getBudgetStatus_ratioJustBelowOne_statusNearLimit() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget = new Budget();
    budget.setId(13L);
    budget.setLedgerId(1L);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(10);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 10);

    doReturn(new BigDecimal("990.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 10);

    BudgetStatusItem item = response.getItems().get(0);
    assertEquals(0, new BigDecimal("990.00").compareTo(item.getSpentAmount()));
    assertEquals("0.9900", item.getRatio());
    assertEquals("NEAR_LIMIT", item.getStatus());
  }

  @Test
  @DisplayName("getBudgetStatus: ratio = 1.00 -> status EXCEEDED")
  void getBudgetStatus_ratioExactlyOne_statusExceeded() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget = new Budget();
    budget.setId(14L);
    budget.setLedgerId(1L);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(9);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 9);

    doReturn(new BigDecimal("1000.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 9);

    BudgetStatusItem item = response.getItems().get(0);
    assertEquals(0, new BigDecimal("1000.00").compareTo(item.getSpentAmount()));
    assertEquals("1.0000", item.getRatio());
    assertEquals("EXCEEDED", item.getStatus());
  }

  @Test
  @DisplayName("getBudgetStatus: ratio = 1.50 -> status EXCEEDED")
  void getBudgetStatus_ratioAboveOne_statusExceeded() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget = new Budget();
    budget.setId(15L);
    budget.setLedgerId(1L);
    budget.setCategoryId(7L);
    budget.setYear(2025);
    budget.setMonth(8);
    budget.setLimitAmount(new BigDecimal("500.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 8);

    doReturn(new BigDecimal("750.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 8);

    BudgetStatusItem item = response.getItems().get(0);
    assertEquals(0, new BigDecimal("750.00").compareTo(item.getSpentAmount()));
    assertEquals("1.5000", item.getRatio());
    assertEquals("EXCEEDED", item.getStatus());
  }

  @Test
  @DisplayName("getBudgetStatus: multiple budgets (0 budgets edge case tested above)")
  void getBudgetStatus_twoBudgets_returnsTwo() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget budget1 = new Budget();
    budget1.setId(21L);
    budget1.setLedgerId(1L);
    budget1.setCategoryId(null);
    budget1.setYear(2025);
    budget1.setMonth(12);
    budget1.setLimitAmount(new BigDecimal("2000.00"));

    Budget budget2 = new Budget();
    budget2.setId(22L);
    budget2.setLedgerId(1L);
    budget2.setCategoryId(3L);
    budget2.setYear(2025);
    budget2.setMonth(12);
    budget2.setLimitAmount(new BigDecimal("800.00"));

    doReturn(Arrays.asList(budget1, budget2))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 12);

    doReturn(new BigDecimal("500.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 12);

    assertEquals(2, response.getItems().size());

    BudgetStatusItem item1 = response.getItems().get(0);
    assertEquals(21L, item1.getBudgetId());
    assertEquals("Total Budget", item1.getCategoryName());

    BudgetStatusItem item2 = response.getItems().get(1);
    assertEquals(22L, item2.getBudgetId());
    assertEquals("Category 3", item2.getCategoryName());
  }

  @Test
  @DisplayName("getBudgetStatus: many budgets (3) -> returns three")
  void getBudgetStatus_manyBudgets_returnsAll() {
    CurrentUserContext.set(testUser);

    LedgerMember member = new LedgerMember();
    member.setUserId(100L);
    member.setRole("MEMBER");
    doReturn(member).when(ledgerMemberMapper).selectOne(any(LambdaQueryWrapper.class));

    Budget b1 = new Budget();
    b1.setId(31L);
    b1.setLedgerId(1L);
    b1.setCategoryId(null);
    b1.setYear(2025);
    b1.setMonth(6);
    b1.setLimitAmount(new BigDecimal("1000.00"));

    Budget b2 = new Budget();
    b2.setId(32L);
    b2.setLedgerId(1L);
    b2.setCategoryId(1L);
    b2.setYear(2025);
    b2.setMonth(6);
    b2.setLimitAmount(new BigDecimal("300.00"));

    Budget b3 = new Budget();
    b3.setId(33L);
    b3.setLedgerId(1L);
    b3.setCategoryId(2L);
    b3.setYear(2025);
    b3.setMonth(6);
    b3.setLimitAmount(new BigDecimal("200.00"));

    doReturn(Arrays.asList(b1, b2, b3)).when(budgetMapper).findByLedgerAndYearMonth(1L, 2025, 6);

    doReturn(new BigDecimal("100.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    BudgetStatusResponse response = service.getBudgetStatus(1L, 2025, 6);

    assertEquals(3, response.getItems().size());
  }

  // ========== checkBudgetAfterTransaction() Tests ==========

  @Test
  @DisplayName("checkBudgetAfterTransaction: no budgets -> returns null")
  void checkBudgetAfterTransaction_noBudgets_returnsNull() {
    doReturn(Collections.emptyList()).when(budgetMapper).findByLedgerAndYearMonth(1L, 2025, 12);

    String alert =
        service.checkBudgetAfterTransaction(1L, null, LocalDateTime.of(2025, 12, 15, 10, 0));

    assertNull(alert);
  }

  @Test
  @DisplayName("checkBudgetAfterTransaction: categoryId is null, ledger budget OK -> null")
  void checkBudgetAfterTransaction_nullCategory_ledgerOk_returnsNull() {
    Budget ledgerBudget = new Budget();
    ledgerBudget.setId(40L);
    ledgerBudget.setLedgerId(1L);
    ledgerBudget.setCategoryId(null);
    ledgerBudget.setYear(2025);
    ledgerBudget.setMonth(12);
    ledgerBudget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(ledgerBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 12);

    doReturn(new BigDecimal("100.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, null, LocalDateTime.of(2025, 12, 20, 14, 30));

    assertNull(alert);
  }

  @Test
  @DisplayName("checkBudgetAfterTransaction: categoryId is null, ledger budget NEAR_LIMIT -> alert")
  void checkBudgetAfterTransaction_nullCategory_ledgerNearLimit_returnsAlert() {
    Budget ledgerBudget = new Budget();
    ledgerBudget.setId(41L);
    ledgerBudget.setLedgerId(1L);
    ledgerBudget.setCategoryId(null);
    ledgerBudget.setYear(2025);
    ledgerBudget.setMonth(11);
    ledgerBudget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(ledgerBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 11);

    doReturn(new BigDecimal("850.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, null, LocalDateTime.of(2025, 11, 25, 18, 0));

    assertNotNull(alert);
    assertEquals("Budget warning: Total Budget at 85%, approaching limit", alert);
  }

  @Test
  @DisplayName("checkBudgetAfterTransaction: categoryId is null, ledger budget EXCEEDED -> alert")
  void checkBudgetAfterTransaction_nullCategory_ledgerExceeded_returnsAlert() {
    Budget ledgerBudget = new Budget();
    ledgerBudget.setId(42L);
    ledgerBudget.setLedgerId(1L);
    ledgerBudget.setCategoryId(null);
    ledgerBudget.setYear(2025);
    ledgerBudget.setMonth(10);
    ledgerBudget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(ledgerBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 10);

    doReturn(new BigDecimal("1200.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, null, LocalDateTime.of(2025, 10, 28, 22, 0));

    assertNotNull(alert);
    assertEquals("Budget alert: Total Budget exceeded at 120%", alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: categoryId provided, "
          + "category budget exists and NEAR_LIMIT -> category alert")
  void checkBudgetAfterTransaction_categoryNearLimit_returnsCategoryAlert() {
    Budget categoryBudget = new Budget();
    categoryBudget.setId(50L);
    categoryBudget.setLedgerId(1L);
    categoryBudget.setCategoryId(5L);
    categoryBudget.setYear(2025);
    categoryBudget.setMonth(9);
    categoryBudget.setLimitAmount(new BigDecimal("500.00"));

    doReturn(Collections.singletonList(categoryBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 9);

    doReturn(new BigDecimal("450.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, 5L, LocalDateTime.of(2025, 9, 10, 12, 0));

    assertNotNull(alert);
    assertEquals("Budget warning: Category 5 at 90%, approaching limit", alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: categoryId provided, "
          + "category budget exists and EXCEEDED -> category alert")
  void checkBudgetAfterTransaction_categoryExceeded_returnsCategoryAlert() {
    Budget categoryBudget = new Budget();
    categoryBudget.setId(51L);
    categoryBudget.setLedgerId(1L);
    categoryBudget.setCategoryId(7L);
    categoryBudget.setYear(2025);
    categoryBudget.setMonth(8);
    categoryBudget.setLimitAmount(new BigDecimal("300.00"));

    doReturn(Collections.singletonList(categoryBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 8);

    doReturn(new BigDecimal("400.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert = service.checkBudgetAfterTransaction(1L, 7L, LocalDateTime.of(2025, 8, 5, 9, 30));

    assertNotNull(alert);
    assertEquals("Budget alert: Category 7 exceeded at 133%", alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: categoryId provided, "
          + "category budget OK -> returns null (does not check ledger)")
  void checkBudgetAfterTransaction_categoryOk_returnsNull() {
    Budget categoryBudget = new Budget();
    categoryBudget.setId(52L);
    categoryBudget.setLedgerId(1L);
    categoryBudget.setCategoryId(3L);
    categoryBudget.setYear(2025);
    categoryBudget.setMonth(7);
    categoryBudget.setLimitAmount(new BigDecimal("500.00"));

    doReturn(Collections.singletonList(categoryBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 7);

    doReturn(new BigDecimal("100.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(eq(1L), eq(3L), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, 3L, LocalDateTime.of(2025, 7, 12, 16, 0));

    assertNull(alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: categoryId provided, "
          + "no category budget, ledger budget EXCEEDED -> ledger alert")
  void checkBudgetAfterTransaction_noCategoryBudget_ledgerExceeded_returnsLedgerAlert() {
    Budget ledgerBudget = new Budget();
    ledgerBudget.setId(60L);
    ledgerBudget.setLedgerId(1L);
    ledgerBudget.setCategoryId(null);
    ledgerBudget.setYear(2025);
    ledgerBudget.setMonth(6);
    ledgerBudget.setLimitAmount(new BigDecimal("800.00"));

    doReturn(Collections.singletonList(ledgerBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 6);

    doReturn(new BigDecimal("1000.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, 99L, LocalDateTime.of(2025, 6, 15, 20, 0));

    assertNotNull(alert);
    assertEquals("Budget alert: Total Budget exceeded at 125%", alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: categoryId provided, "
          + "no matching category budget, no ledger budget -> null")
  void checkBudgetAfterTransaction_noMatchingBudgets_returnsNull() {
    Budget otherCategoryBudget = new Budget();
    otherCategoryBudget.setId(70L);
    otherCategoryBudget.setLedgerId(1L);
    otherCategoryBudget.setCategoryId(10L);
    otherCategoryBudget.setYear(2025);
    otherCategoryBudget.setMonth(5);
    otherCategoryBudget.setLimitAmount(new BigDecimal("200.00"));

    doReturn(Collections.singletonList(otherCategoryBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 5);

    String alert =
        service.checkBudgetAfterTransaction(1L, 20L, LocalDateTime.of(2025, 5, 10, 11, 0));

    assertNull(alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: both category and ledger EXCEEDED "
          + "-> returns category alert (priority)")
  void checkBudgetAfterTransaction_bothExceeded_returnsCategoryAlert() {
    Budget categoryBudget = new Budget();
    categoryBudget.setId(80L);
    categoryBudget.setLedgerId(1L);
    categoryBudget.setCategoryId(4L);
    categoryBudget.setYear(2025);
    categoryBudget.setMonth(4);
    categoryBudget.setLimitAmount(new BigDecimal("300.00"));

    Budget ledgerBudget = new Budget();
    ledgerBudget.setId(81L);
    ledgerBudget.setLedgerId(1L);
    ledgerBudget.setCategoryId(null);
    ledgerBudget.setYear(2025);
    ledgerBudget.setMonth(4);
    ledgerBudget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Arrays.asList(categoryBudget, ledgerBudget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 4);

    doReturn(new BigDecimal("400.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(eq(1L), eq(4L), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, 4L, LocalDateTime.of(2025, 4, 20, 13, 0));

    assertNotNull(alert);
    assertEquals("Budget alert: Category 4 exceeded at 133%", alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: budget limit is zero -> returns null (avoid division by zero)")
  void checkBudgetAfterTransaction_limitZero_returnsNull() {
    Budget budget = new Budget();
    budget.setId(90L);
    budget.setLedgerId(1L);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(3);
    budget.setLimitAmount(BigDecimal.ZERO);

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 3);

    doReturn(new BigDecimal("500.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, null, LocalDateTime.of(2025, 3, 10, 15, 0));

    assertNull(alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: ratio exactly at 0.80 boundary -> NEAR_LIMIT with 80% message")
  void checkBudgetAfterTransaction_ratioExactly80_nearLimit() {
    Budget budget = new Budget();
    budget.setId(91L);
    budget.setLedgerId(1L);
    budget.setCategoryId(null);
    budget.setYear(2025);
    budget.setMonth(2);
    budget.setLimitAmount(new BigDecimal("1000.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 2);

    doReturn(new BigDecimal("800.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, null, LocalDateTime.of(2025, 2, 15, 10, 0));

    assertNotNull(alert);
    assertEquals("Budget warning: Total Budget at 80%, approaching limit", alert);
  }

  @Test
  @DisplayName(
      "checkBudgetAfterTransaction: ratio exactly at 1.00 boundary -> EXCEEDED with 100% message")
  void checkBudgetAfterTransaction_ratioExactly100_exceeded() {
    Budget budget = new Budget();
    budget.setId(92L);
    budget.setLedgerId(1L);
    budget.setCategoryId(8L);
    budget.setYear(2025);
    budget.setMonth(1);
    budget.setLimitAmount(new BigDecimal("500.00"));

    doReturn(Collections.singletonList(budget))
        .when(budgetMapper)
        .findByLedgerAndYearMonth(1L, 2025, 1);

    doReturn(new BigDecimal("500.00"))
        .when(transactionMapper)
        .sumExpenseByTimeRange(
            any(Long.class), any(), any(LocalDateTime.class), any(LocalDateTime.class));

    String alert =
        service.checkBudgetAfterTransaction(1L, 8L, LocalDateTime.of(2025, 1, 31, 23, 59));

    assertNotNull(alert);
    assertEquals("Budget alert: Category 8 exceeded at 100%", alert);
  }
}
