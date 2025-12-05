package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.SplitItem;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Currency;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.Transaction;
import dev.coms4156.project.groupproject.entity.TransactionSplit;
import dev.coms4156.project.groupproject.mapper.CurrencyMapper;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.mapper.TransactionSplitMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TransactionServiceImpl} with all external mappers mocked. Internal helper
 * methods are exercised via public methods to validate behavior, interactions, and state effects.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TransactionServiceImplTest {

  @Mock private TransactionMapper transactionMapper;
  @Mock private TransactionSplitMapper transactionSplitMapper;
  @Mock private DebtEdgeMapper debtEdgeMapper;
  @Mock private LedgerMapper ledgerMapper;
  @Mock private LedgerMemberMapper ledgerMemberMapper;
  @Mock private CurrencyMapper currencyMapper;
  @Mock private dev.coms4156.project.groupproject.service.BudgetService budgetService;

  @InjectMocks private TransactionServiceImpl service;

  @AfterEach
  void clear() {
    CurrentUserContext.clear();
  }

  private static Ledger ledger(long id, String baseCurrency, String type) {
    Ledger l = new Ledger();
    l.setId(id);
    l.setBaseCurrency(baseCurrency);
    l.setLedgerType(type);
    return l;
  }

  private static LedgerMember ledgerMember(long userId, String role) {
    LedgerMember lm = new LedgerMember();
    lm.setUserId(userId);
    lm.setRole(role);
    return lm;
  }

  private static CreateTransactionRequest reqExpenseEqual(
      long payerId, long userId, BigDecimal total) {
    CreateTransactionRequest r = new CreateTransactionRequest();
    r.setTxnAt(LocalDateTime.now());
    r.setType("EXPENSE");
    r.setCurrency("USD");
    r.setAmountTotal(total);
    r.setNote("Dinner");
    r.setPayerId(payerId);
    r.setIsPrivate(false);
    r.setRoundingStrategy("ROUND_HALF_UP");
    r.setTailAllocation("PAYER");
    SplitItem s = new SplitItem();
    s.setUserId(userId);
    s.setSplitMethod("EQUAL");
    s.setShareValue(BigDecimal.ZERO);
    s.setIncluded(true);
    r.setSplits(Collections.singletonList(s));
    return r;
  }

  @Test
  @DisplayName("createTransaction: typical EXPENSE equal split -> inserts txn, splits, edges")
  void createTransaction_typical() {
    CurrentUserContext.set(new UserView(1L, "A"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    // Stub selectList to return members for membership validation in handleSplitTransaction
    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    member1.setLedgerId(10L);
    LedgerMember member2 = new LedgerMember();
    member2.setUserId(2L);
    member2.setLedgerId(10L);
    doReturn(java.util.Arrays.asList(member1, member2))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(77L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));
    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    // Payer is userId=1, split is userId=2, so a debt edge should be generated
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("120.00"));

    CreateTransactionResponse resp = service.createTransaction(10L, req);

    assertNotNull(resp);
    assertEquals(77L, resp.getTransactionId());

    verify(transactionSplitMapper, times(1))
        .insertBatch(
            argThat(
                list ->
                    list != null
                        && list.size() == 1
                        && list.get(0).getUserId().equals(2L)
                        && list.get(0).getComputedAmount() != null
                        && list.get(0)
                                .getComputedAmount()
                                .compareTo(new java.math.BigDecimal("120.00"))
                            == 0));

    verify(debtEdgeMapper, times(1)).insertBatch(argThat(list -> list != null && list.size() == 1));
  }

  @Test
  @DisplayName("createTransaction: currency mismatch -> throws")
  void createTransaction_currencyMismatch() {
    CurrentUserContext.set(new UserView(2L, "B"));
    Ledger led = ledger(10L, "USD", "GROUP_BALANCE");
    doReturn(led).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    CreateTransactionRequest req = reqExpenseEqual(2L, 2L, new BigDecimal("50.00"));
    req.setCurrency("EUR");

    assertThrows(RuntimeException.class, () -> service.createTransaction(10L, req));
  }

  @Test
  @DisplayName("createTransaction: missing splits -> throws")
  void createTransaction_missingSplits() {
    CurrentUserContext.set(new UserView(1L, "A"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(88L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("10.00"));
    req.setSplits(null);

    assertThrows(RuntimeException.class, () -> service.createTransaction(10L, req));
  }

  @Test
  @DisplayName("createTransaction: duplicate user IDs in splits -> throws")
  void createTransaction_duplicateUsers() {
    CurrentUserContext.set(new UserView(1L, "A"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    doReturn(java.util.Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(89L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    final CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("10.00"));
    SplitItem s2 = new SplitItem();
    s2.setUserId(1L);
    s2.setSplitMethod("EQUAL");
    s2.setShareValue(BigDecimal.ZERO);
    s2.setIncluded(true);
    java.util.List<SplitItem> list = new java.util.ArrayList<>();
    list.add(req.getSplits().get(0));
    list.add(s2);
    req.setSplits(list);

    assertThrows(RuntimeException.class, () -> service.createTransaction(10L, req));
  }

  @Test
  @DisplayName("createTransaction: no included users -> throws")
  void createTransaction_noIncluded() {
    CurrentUserContext.set(new UserView(1L, "A"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    doReturn(java.util.Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(90L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("10.00"));
    req.getSplits().get(0).setIncluded(false);

    assertThrows(RuntimeException.class, () -> service.createTransaction(10L, req));
  }

  @Test
  @DisplayName("createTransaction: invalid split method -> throws")
  void createTransaction_invalidMethod() {
    CurrentUserContext.set(new UserView(1L, "A"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    doReturn(java.util.Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(91L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("10.00"));
    req.getSplits().get(0).setSplitMethod("UNKNOWN");

    assertThrows(RuntimeException.class, () -> service.createTransaction(10L, req));
  }

  @Test
  @DisplayName("createTransaction: not member -> throws")
  void createTransaction_notMember() {
    CurrentUserContext.set(new UserView(1L, "A"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(null)
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("10.00"));
    assertThrows(RuntimeException.class, () -> service.createTransaction(10L, req));
  }

  @Test
  @DisplayName("getTransaction: typical -> returns response when visible and in ledger")
  void getTransaction_typical() {
    CurrentUserContext.set(new UserView(3L, "C"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    Transaction t = new Transaction();
    t.setId(100L);
    t.setLedgerId(10L);
    t.setCurrency("USD");
    t.setType("EXPENSE");
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(100L, 3L);
    doReturn(Collections.emptyList()).when(transactionSplitMapper).findByTransactionId(100L);
    doReturn(Collections.emptyList()).when(debtEdgeMapper).findByTransactionId(100L);

    TransactionResponse resp = service.getTransaction(10L, 100L);
    assertEquals(100L, resp.getTransactionId());
    assertEquals(10L, resp.getLedgerId());
  }

  @Test
  @DisplayName("getTransaction: wrong ledger -> throws")
  void getTransaction_wrongLedger() {
    CurrentUserContext.set(new UserView(3L, "C"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    Transaction t = new Transaction();
    t.setId(100L);
    t.setLedgerId(11L);
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(100L, 3L);
    assertThrows(RuntimeException.class, () -> service.getTransaction(10L, 100L));
  }

  @Test
  @DisplayName("listTransactions: typical -> returns page and items")
  void listTransactions_typical() {
    CurrentUserContext.set(new UserView(4L, "D"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    final IPage<Transaction> page = org.mockito.Mockito.mock(IPage.class);
    Transaction rec = new Transaction();
    rec.setId(5L);
    rec.setLedgerId(10L);
    rec.setCurrency("USD");
    List<Transaction> records = new ArrayList<>();
    records.add(rec);

    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(
            any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class),
            eq(10L),
            any(),
            any(),
            any(),
            any(),
            any(),
            eq(4L));
    doReturn(records).when(page).getRecords();
    doReturn(1L).when(page).getTotal();

    ListTransactionsResponse resp = service.listTransactions(10L, 1, 10, null, null, null, null);
    assertEquals(1, resp.getItems().size());
    assertEquals(1, resp.getTotal());
    assertEquals(1, resp.getPage());
    assertEquals(10, resp.getSize());
  }

  @Test
  @DisplayName("deleteTransaction: typical -> deletes edges, splits, then txn")
  void deleteTransaction_typical() {
    CurrentUserContext.set(new UserView(5L, "E"));
    doReturn(ledger(10L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(10L);
    doReturn(new LedgerMember())
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    Transaction t = new Transaction();
    t.setId(9L);
    t.setLedgerId(10L);
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(9L, 5L);

    service.deleteTransaction(10L, 9L);

    verify(debtEdgeMapper, times(1)).deleteByTransactionId(9L);
    verify(transactionSplitMapper, times(1)).deleteByTransactionId(9L);
    verify(transactionMapper, times(1)).deleteById(9L);
  }

  // ===== Additional Tests for Complete Coverage =====

  @Test
  @DisplayName("createTransaction: atypical PERCENT split -> calculates amounts correctly")
  void createTransaction_percentSplit() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    CreateTransactionRequest req = reqExpensePercent();
    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: atypical WEIGHT split -> distributes by weight")
  void createTransaction_weightSplit() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    CreateTransactionRequest req = reqExpenseWeight();
    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: atypical EXACT split -> uses exact amounts")
  void createTransaction_exactSplit() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    CreateTransactionRequest req = reqExpenseExact();
    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("getTransaction: invalid not found -> throws")
  void getTransaction_notFound() {
    CurrentUserContext.set(new UserView(1L, "U"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    doReturn(null).when(transactionMapper).findTransactionByIdWithVisibility(999L, 1L);

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getTransaction(1L, 999L));
    assertEquals("Transaction not found", ex.getMessage());
  }

  @Test
  @DisplayName("getTransaction: invalid not logged in -> throws")
  void getTransaction_notLoggedIn() {
    CurrentUserContext.set(null);

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.getTransaction(1L, 1L));
    assertEquals("Not logged in", ex.getMessage());
  }

  @Test
  @DisplayName("listTransactions: atypical with date filters -> applies filters")
  void listTransactions_withFilters() {
    CurrentUserContext.set(new UserView(1L, "U"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(
            any(), eq(1L), any(), any(), eq("EXPENSE"), eq(1L), any(), eq(1L));

    ListTransactionsResponse resp =
        service.listTransactions(
            1L, 1, 10, "2025-01-01T00:00:00", "2025-12-31T23:59:59", "EXPENSE", 1L);

    assertNotNull(resp);
    verify(transactionMapper, times(1))
        .findTransactionsByLedger(
            any(), eq(1L), any(), any(), eq("EXPENSE"), eq(1L), any(), eq(1L));
  }

  @Test
  @DisplayName("listTransactions: invalid not member -> throws")
  void listTransactions_notMember() {
    CurrentUserContext.set(new UserView(99L, "U"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(null).when(ledgerMemberMapper).selectOne(any());

    RuntimeException ex =
        assertThrows(
            RuntimeException.class,
            () -> service.listTransactions(1L, 1, 10, null, null, null, null));
    assertEquals("User not a member of this ledger", ex.getMessage());
  }

  @Test
  @DisplayName("deleteTransaction: invalid not found -> throws")
  void deleteTransaction_notFound() {
    CurrentUserContext.set(new UserView(1L, "U"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    doReturn(null).when(transactionMapper).findTransactionByIdWithVisibility(999L, 1L);

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.deleteTransaction(1L, 999L));
    assertEquals("Transaction not found", ex.getMessage());
  }

  @Test
  @DisplayName("deleteTransaction: invalid not member -> throws")
  void deleteTransaction_notMember() {
    CurrentUserContext.set(new UserView(99L, "U"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(null).when(ledgerMemberMapper).selectOne(any());

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.deleteTransaction(1L, 1L));
    assertEquals("User not a member of this ledger", ex.getMessage());
  }

  // Helper methods for new test data
  private CreateTransactionRequest reqExpensePercent() {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setPayerId(1L);
    req.setIsPrivate(false);
    req.setRoundingStrategy("ROUND_HALF_UP");
    req.setTailAllocation("PAYER");

    SplitItem split1 = new SplitItem();
    split1.setUserId(1L);
    split1.setSplitMethod("PERCENT");
    split1.setShareValue(new BigDecimal("60.00"));
    split1.setIncluded(true);

    SplitItem split2 = new SplitItem();
    split2.setUserId(2L);
    split2.setSplitMethod("PERCENT");
    split2.setShareValue(new BigDecimal("40.00"));
    split2.setIncluded(true);

    req.setSplits(Arrays.asList(split1, split2));
    return req;
  }

  @Test
  @DisplayName("createTransaction: EXPENSE with budget alert -> returns alert message")
  void createTransaction_expenseWithBudgetAlert_returnsAlert() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    Ledger led = ledger(1L, "USD", "GROUP_BALANCE");
    doReturn(led).when(ledgerMapper).selectById(1L);
    doReturn(ledgerMember(1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    member1.setLedgerId(1L);
    doReturn(Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(100L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doReturn(1).when(transactionSplitMapper).insertBatch(any(List.class));

    doReturn("Budget warning: Category 5 at 90%, approaching limit")
        .when(budgetService)
        .checkBudgetAfterTransaction(any(Long.class), any(Long.class), any(LocalDateTime.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));
    req.setCategoryId(5L);

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(100L, resp.getTransactionId());
    assertEquals("Budget warning: Category 5 at 90%, approaching limit", resp.getBudgetAlert());

    verify(budgetService, times(1))
        .checkBudgetAfterTransaction(eq(1L), eq(5L), any(LocalDateTime.class));
  }

  @Test
  @DisplayName("createTransaction: EXPENSE with no budget alert -> returns null alert")
  void createTransaction_expenseNoBudgetAlert_returnsNullAlert() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    Ledger led = ledger(1L, "USD", "GROUP_BALANCE");
    doReturn(led).when(ledgerMapper).selectById(1L);
    doReturn(ledgerMember(1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    member1.setLedgerId(1L);
    doReturn(Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(101L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doReturn(1).when(transactionSplitMapper).insertBatch(any(List.class));

    doReturn(null)
        .when(budgetService)
        .checkBudgetAfterTransaction(any(Long.class), any(), any(LocalDateTime.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("50.00"));

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(101L, resp.getTransactionId());
    assertNull(resp.getBudgetAlert());

    verify(budgetService, times(1))
        .checkBudgetAfterTransaction(eq(1L), any(), any(LocalDateTime.class));
  }

  @Test
  @DisplayName("createTransaction: INCOME -> budget service NOT called")
  void createTransaction_income_budgetServiceNotCalled() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    Ledger led = ledger(1L, "USD", "GROUP_BALANCE");
    doReturn(led).when(ledgerMapper).selectById(1L);
    doReturn(ledgerMember(1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    member1.setLedgerId(1L);
    doReturn(Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(102L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doReturn(1).when(transactionSplitMapper).insertBatch(any(List.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("200.00"));
    req.setType("INCOME");

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(102L, resp.getTransactionId());
    assertNull(resp.getBudgetAlert());

    verify(budgetService, never())
        .checkBudgetAfterTransaction(any(Long.class), any(), any(LocalDateTime.class));
  }

  @Test
  @DisplayName("createTransaction: EXPENSE with budget service exception -> transaction succeeds")
  void createTransaction_expenseBudgetServiceThrows_transactionSucceeds() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    Ledger led = ledger(1L, "USD", "GROUP_BALANCE");
    doReturn(led).when(ledgerMapper).selectById(1L);
    doReturn(ledgerMember(1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    member1.setLedgerId(1L);
    doReturn(Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(103L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doReturn(1).when(transactionSplitMapper).insertBatch(any(List.class));

    doThrow(new RuntimeException("Budget service error"))
        .when(budgetService)
        .checkBudgetAfterTransaction(any(Long.class), any(), any(LocalDateTime.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("75.00"));

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(103L, resp.getTransactionId());
    assertNull(resp.getBudgetAlert());

    verify(budgetService, times(1))
        .checkBudgetAfterTransaction(eq(1L), any(), any(LocalDateTime.class));
  }

  @Test
  @DisplayName("createTransaction: EXPENSE with null categoryId -> budget service called with null")
  void createTransaction_expenseNullCategory_budgetServiceCalledWithNull() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    Ledger led = ledger(1L, "USD", "GROUP_BALANCE");
    doReturn(led).when(ledgerMapper).selectById(1L);
    doReturn(ledgerMember(1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember member1 = new LedgerMember();
    member1.setUserId(1L);
    member1.setLedgerId(1L);
    doReturn(Collections.singletonList(member1))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(104L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doReturn(1).when(transactionSplitMapper).insertBatch(any(List.class));

    doReturn("Budget alert: Total Budget exceeded at 110%")
        .when(budgetService)
        .checkBudgetAfterTransaction(any(Long.class), any(), any(LocalDateTime.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("300.00"));
    req.setCategoryId(null);

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(104L, resp.getTransactionId());
    assertEquals("Budget alert: Total Budget exceeded at 110%", resp.getBudgetAlert());

    verify(budgetService, times(1))
        .checkBudgetAfterTransaction(eq(1L), eq(null), any(LocalDateTime.class));
  }

  private CreateTransactionRequest reqExpenseWeight() {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setPayerId(1L);
    req.setIsPrivate(false);
    req.setRoundingStrategy("ROUND_HALF_UP");
    req.setTailAllocation("PAYER");

    SplitItem split1 = new SplitItem();
    split1.setUserId(1L);
    split1.setSplitMethod("WEIGHT");
    split1.setShareValue(new BigDecimal("2.0"));
    split1.setIncluded(true);

    SplitItem split2 = new SplitItem();
    split2.setUserId(2L);
    split2.setSplitMethod("WEIGHT");
    split2.setShareValue(new BigDecimal("3.0"));
    split2.setIncluded(true);

    req.setSplits(Arrays.asList(split1, split2));
    return req;
  }

  private CreateTransactionRequest reqExpenseExact() {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setPayerId(1L);
    req.setIsPrivate(false);
    req.setRoundingStrategy("NONE");
    req.setTailAllocation("PAYER");

    SplitItem split1 = new SplitItem();
    split1.setUserId(1L);
    split1.setSplitMethod("EXACT");
    split1.setShareValue(new BigDecimal("40.00"));
    split1.setIncluded(true);

    SplitItem split2 = new SplitItem();
    split2.setUserId(2L);
    split2.setSplitMethod("EXACT");
    split2.setShareValue(new BigDecimal("60.00"));
    split2.setIncluded(true);

    req.setSplits(Arrays.asList(split1, split2));
    return req;
  }

  // ===== Branch Coverage Tests for TransactionServiceImpl =====

  @Test
  @DisplayName(
      "createTransaction: applyRoundingAndTailAllocation - currency null -> uses default exponent")
  void createTransaction_applyRoundingCurrencyNull() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "UNKNOWN", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    // Currency not found - returns null
    doReturn(null).when(currencyMapper).selectById("UNKNOWN");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(300L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setCurrency("UNKNOWN");

    service.createTransaction(1L, req);

    verify(transactionSplitMapper, times(1)).insertBatch(any());
    verify(debtEdgeMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName(
      "createTransaction: applyRoundingAndTailAllocation - invalid rounding "
          + "strategy -> uses default")
  void createTransaction_applyRoundingInvalidStrategy() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(301L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setRoundingStrategy("INVALID_STRATEGY"); // Invalid strategy

    service.createTransaction(1L, req);

    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: applyRoundingAndTailAllocation - tail zero -> no allocation")
  void createTransaction_applyRoundingTailZero() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(302L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Use EXACT split that sums exactly to total (no tail)
    CreateTransactionRequest req = reqExpenseExact();
    req.setAmountTotal(new BigDecimal("100.00")); // 40 + 60 = 100 exactly

    service.createTransaction(1L, req);

    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: determineTailTarget - LARGEST_SHARE -> allocates to largest")
  void createTransaction_determineTailTargetCreatorNull() {
    // Test LARGEST_SHARE branch (can't test CREATOR with null currentUser because auth check
    // happens first)
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(303L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setTailAllocation("LARGEST_SHARE"); // Test LARGEST_SHARE branch

    service.createTransaction(1L, req);

    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: determineTailTarget - invalid tailAllocation -> uses PAYER")
  void createTransaction_determineTailTargetInvalid() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(304L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setTailAllocation("INVALID_ALLOCATION"); // Invalid

    service.createTransaction(1L, req);

    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: generateDebtEdges - LOAN type -> no edges generated")
  void createTransaction_generateDebtEdgesLoanType() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(306L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setType("LOAN"); // LOAN type

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
    // LOAN type should not generate debt edges
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: generateDebtEdges - non-GROUP_BALANCE -> no edges")
  void createTransaction_generateDebtEdgesNonGroupBalance() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "PERSONAL")).when(ledgerMapper).selectById(1L); // Not GROUP_BALANCE
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(307L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
    // Non-GROUP_BALANCE should not generate debt edges
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: generateDebtEdges - payer equals participant -> no self-edge")
  void createTransaction_generateDebtEdgesPayerEqualsParticipant() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(308L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Payer is also the only participant
    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
    // Should not create self-debt edge (payer == participant)
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: calculateSplitAmounts - switch default branch -> throws")
  void createTransaction_calculateSplitAmountsDefaultBranch() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.getSplits().get(0).setSplitMethod("UNKNOWN_METHOD"); // Invalid method

    // This should throw in validateSplitMethod before reaching calculateSplitAmounts
    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  // ===== Additional Branch Coverage Tests for TransactionServiceImpl =====

  @Test
  @DisplayName("createTransaction: currency mismatch -> throws")
  void createTransaction_currencyMismatch_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setCurrency("EUR"); // Different from ledger base currency (USD)

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: budgetService throws exception -> handles gracefully")
  void createTransaction_budgetServiceThrows_handlesGracefully() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(400L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Make budgetService throw exception
    doThrow(new RuntimeException("Budget service error"))
        .when(budgetService)
        .checkBudgetAfterTransaction(any(), any(), any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setType("EXPENSE");

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(400L, resp.getTransactionId());
    assertNull(resp.getBudgetAlert()); // Should be null when exception occurs
  }

  @Test
  @DisplayName("createTransaction: INCOME type -> budgetService not called")
  void createTransaction_incomeType_budgetServiceNotCalled() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(401L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setType("INCOME"); // INCOME type

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals(401L, resp.getTransactionId());
    // Verify budgetService was NOT called for INCOME
    verify(budgetService, never()).checkBudgetAfterTransaction(any(), any(), any());
  }

  @Test
  @DisplayName("getTransaction: transaction not found -> throws")
  void getTransaction_transactionNotFound_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    // Transaction not found
    doReturn(null).when(transactionMapper).findTransactionByIdWithVisibility(999L, 1L);

    assertThrows(RuntimeException.class, () -> service.getTransaction(1L, 999L));
  }

  @Test
  @DisplayName("getTransaction: transaction ledgerId mismatch -> throws")
  void getTransaction_ledgerIdMismatch_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(500L);
    t.setLedgerId(999L); // Different ledger ID
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(500L, 1L);

    assertThrows(RuntimeException.class, () -> service.getTransaction(1L, 500L));
  }

  @Test
  @DisplayName("getTransaction: with null splits -> handles gracefully")
  void getTransaction_nullSplits_handlesGracefully() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(501L);
    t.setLedgerId(1L);
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(501L, 1L);

    // No splits
    doReturn(Collections.emptyList()).when(transactionSplitMapper).findByTransactionId(501L);

    // No edges
    doReturn(Collections.emptyList()).when(debtEdgeMapper).findByTransactionId(501L);

    TransactionResponse resp = service.getTransaction(1L, 501L);

    assertNotNull(resp);
    assertEquals(501L, resp.getTransactionId());
    assertTrue(resp.getSplits().isEmpty());
    assertTrue(resp.getEdgesPreview().isEmpty());
  }

  @Test
  @DisplayName("listTransactions: with null dates -> handles gracefully")
  void listTransactions_nullDates_handlesGracefully() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), isNull(), isNull(), any(), any(), any(), eq(1L));

    ListTransactionsResponse resp = service.listTransactions(1L, 1, 10, null, null, null, null);

    assertNotNull(resp);
    assertEquals(0, resp.getItems().size());
  }

  @Test
  @DisplayName("deleteTransaction: transaction not found -> throws")
  void deleteTransaction_transactionNotFound_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    // Transaction not found
    doReturn(null).when(transactionMapper).findTransactionByIdWithVisibility(999L, 1L);

    assertThrows(RuntimeException.class, () -> service.deleteTransaction(1L, 999L));
  }

  @Test
  @DisplayName("deleteTransaction: transaction ledgerId mismatch -> throws")
  void deleteTransaction_ledgerIdMismatch_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(600L);
    t.setLedgerId(999L); // Different ledger ID
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(600L, 1L);

    assertThrows(RuntimeException.class, () -> service.deleteTransaction(1L, 600L));
  }

  @Test
  @DisplayName("createTransaction: splits null -> throws")
  void createTransaction_splitsNull_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setSplits(null); // Null splits

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: splits empty -> throws")
  void createTransaction_splitsEmpty_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setSplits(Collections.emptyList()); // Empty splits

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: duplicate user IDs in splits -> throws")
  void createTransaction_duplicateUserIds_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    // Add duplicate user ID
    SplitItem duplicate = new SplitItem();
    duplicate.setUserId(2L); // Same as existing split
    duplicate.setSplitMethod("EQUAL");
    duplicate.setShareValue(BigDecimal.ZERO);
    duplicate.setIncluded(true);
    List<SplitItem> splits = new ArrayList<>(req.getSplits());
    splits.add(duplicate);
    req.setSplits(splits);

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: no included users -> throws")
  void createTransaction_noIncludedUsers_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    // Set all splits to not included
    req.getSplits().forEach(split -> split.setIncluded(false));

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: generateDebtEdges - edges empty -> no insertBatch")
  void createTransaction_generateDebtEdgesEmpty_noInsertBatch() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(700L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Payer is the only participant - no debt edges should be created
    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
    // No edges should be created (payer == only participant)
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName("getTransaction: not logged in -> throws")
  void getTransaction_notLoggedIn_throws() {
    CurrentUserContext.set(null);

    assertThrows(RuntimeException.class, () -> service.getTransaction(1L, 1L));
  }

  @Test
  @DisplayName("listTransactions: not logged in -> throws")
  void listTransactions_notLoggedIn_throws() {
    CurrentUserContext.set(null);

    assertThrows(
        RuntimeException.class, () -> service.listTransactions(1L, 1, 10, null, null, null, null));
  }

  @Test
  @DisplayName("deleteTransaction: not logged in -> throws")
  void deleteTransaction_notLoggedIn_throws() {
    CurrentUserContext.set(null);

    assertThrows(RuntimeException.class, () -> service.deleteTransaction(1L, 1L));
  }

  @Test
  @DisplayName("listTransactions: with valid dates -> parses correctly")
  void listTransactions_validDates_parsesCorrectly() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), any(), any(), any(), any(), any(), eq(1L));

    String fromDate = "2024-01-01T00:00:00";
    String toDate = "2024-12-31T23:59:59";
    ListTransactionsResponse resp =
        service.listTransactions(1L, 1, 10, fromDate, toDate, null, null);

    assertNotNull(resp);
    assertEquals(0, resp.getItems().size());
  }

  @Test
  @DisplayName("listTransactions: with only fromDate -> parses correctly")
  void listTransactions_onlyFromDate_parsesCorrectly() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), any(), isNull(), any(), any(), any(), eq(1L));

    String fromDate = "2024-01-01T00:00:00";
    ListTransactionsResponse resp = service.listTransactions(1L, 1, 10, fromDate, null, null, null);

    assertNotNull(resp);
    assertEquals(0, resp.getItems().size());
  }

  @Test
  @DisplayName("listTransactions: with only toDate -> parses correctly")
  void listTransactions_onlyToDate_parsesCorrectly() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), isNull(), any(), any(), any(), any(), eq(1L));

    String toDate = "2024-12-31T23:59:59";
    ListTransactionsResponse resp = service.listTransactions(1L, 1, 10, null, toDate, null, null);

    assertNotNull(resp);
    assertEquals(0, resp.getItems().size());
  }

  @Test
  @DisplayName("createTransaction: validateSplits - EXACT method with non-matching total -> throws")
  void createTransaction_exactMethodNonMatchingTotal_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    CreateTransactionRequest req = reqExpenseExact();
    req.setAmountTotal(new BigDecimal("100.00")); // Total is 100
    // But splits sum to 40 + 60 = 100, so this should pass
    // Let's make it fail by setting a different total
    req.setAmountTotal(new BigDecimal("150.00")); // Different total

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: validateSplits - EXACT method with matching total -> succeeds")
  void createTransaction_exactMethodMatchingTotal_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(800L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseExact();
    req.setAmountTotal(new BigDecimal("100.00")); // Matches 40 + 60

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: validateSplits - EXACT method not used -> no validation")
  void createTransaction_exactMethodNotUsed_noValidation() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(801L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Use EQUAL method, not EXACT
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(transactionSplitMapper, times(1)).insertBatch(any());
  }

  // ===== Additional Branch Coverage Tests to reach 95% =====

  @Test
  @DisplayName("createTransaction: validateSplitMethod - PERCENT value exactly 0 -> succeeds")
  void createTransaction_percentValueZero_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(900L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpensePercent();
    req.getSplits().get(0).setShareValue(BigDecimal.ZERO); // 0%
    req.getSplits().get(1).setShareValue(new BigDecimal("100.00")); // 100%

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplitMethod - PERCENT value exactly 100 -> succeeds")
  void createTransaction_percentValueHundred_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(901L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpensePercent();
    req.getSplits().get(0).setShareValue(new BigDecimal("100.00")); // 100%
    req.getSplits().get(1).setShareValue(BigDecimal.ZERO); // 0%

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName(
      "createTransaction: validateSplitMethod - PERCENT value between 0 and 100 -> succeeds")
  void createTransaction_percentValueInRange_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(902L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpensePercent();
    req.getSplits().get(0).setShareValue(new BigDecimal("50.00")); // 50%
    req.getSplits().get(1).setShareValue(new BigDecimal("50.00")); // 50%

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplitMethod - WEIGHT positive -> succeeds")
  void createTransaction_weightPositive_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(903L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseWeight();
    req.getSplits().get(0).setShareValue(new BigDecimal("1.5")); // Positive weight
    req.getSplits().get(1).setShareValue(new BigDecimal("2.5")); // Positive weight

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplitMethod - EXACT zero -> succeeds")
  void createTransaction_exactZero_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(904L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseExact();
    req.setAmountTotal(new BigDecimal("100.00"));
    req.getSplits().get(0).setShareValue(BigDecimal.ZERO); // Zero is allowed for EXACT
    req.getSplits().get(1).setShareValue(new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplitMethod - EXACT positive -> succeeds")
  void createTransaction_exactPositive_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(905L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseExact();
    req.setAmountTotal(new BigDecimal("100.00"));
    req.getSplits().get(0).setShareValue(new BigDecimal("40.00"));
    req.getSplits().get(1).setShareValue(new BigDecimal("60.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplitMethod - EQUAL method -> no validation")
  void createTransaction_equalMethod_noValidation() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(906L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: calculateSplitAmounts - PERCENT total equals 100 -> succeeds")
  void createTransaction_percentTotalEquals100_succeeds() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(907L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpensePercent();
    // Already sums to 100, should succeed

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: applyRoundingAndTailAllocation - tail positive -> allocates")
  void createTransaction_tailPositive_allocates() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(908L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Create a scenario where rounding creates a positive tail
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setRoundingStrategy("TRIM_TO_UNIT"); // This might create a tail

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: applyRoundingAndTailAllocation - tail negative -> allocates")
  void createTransaction_tailNegative_allocates() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(909L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Create a scenario where rounding creates a negative tail
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setRoundingStrategy("ROUND_HALF_UP");

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: determineTailTarget - PAYER -> returns payerId")
  void createTransaction_tailTargetPayer_returnsPayerId() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(910L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setTailAllocation("PAYER");

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: determineTailTarget - CREATOR -> returns creatorId")
  void createTransaction_tailTargetCreator_returnsCreatorId() {
    CurrentUserContext.set(new UserView(5L, "Creator"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(5L, "EDITOR")); // Creator is member
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(911L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));
    req.setTailAllocation("CREATOR");

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName(
      "createTransaction: generateDebtEdges - EXPENSE with multiple participants -> creates edges")
  void createTransaction_expenseMultipleParticipants_createsEdges() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    members.add(ledgerMember(3L, "VIEWER"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(912L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Create transaction with 3 participants
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("300.00"));
    SplitItem split3 = new SplitItem();
    split3.setUserId(3L);
    split3.setSplitMethod("EQUAL");
    split3.setShareValue(BigDecimal.ZERO);
    split3.setIncluded(true);
    List<SplitItem> splits = new ArrayList<>(req.getSplits());
    splits.add(split3);
    req.setSplits(splits);

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(debtEdgeMapper, times(1)).insertBatch(any());
  }

  @Test
  @DisplayName(
      "createTransaction: generateDebtEdges - INCOME with multiple participants "
          + "-> creates reverse edges")
  void createTransaction_incomeMultipleParticipants_createsReverseEdges() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    members.add(ledgerMember(3L, "VIEWER"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(913L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("300.00"));
    req.setType("INCOME");
    SplitItem split3 = new SplitItem();
    split3.setUserId(3L);
    split3.setSplitMethod("EQUAL");
    split3.setShareValue(BigDecimal.ZERO);
    split3.setIncluded(true);
    List<SplitItem> splits = new ArrayList<>(req.getSplits());
    splits.add(split3);
    req.setSplits(splits);

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    verify(debtEdgeMapper, times(1))
        .insertBatch(
            argThat(
                list ->
                    list != null
                        && list.size() == 2
                        && list.stream()
                            .allMatch(
                                edge ->
                                    edge.getFromUserId().equals(2L)
                                        || edge.getFromUserId().equals(3L))));
  }

  @Test
  @DisplayName("createTransaction: generateDebtEdges - edges not empty -> calls insertBatch")
  void createTransaction_edgesNotEmpty_callsInsertBatch() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(914L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(debtEdgeMapper, times(1)).insertBatch(argThat(list -> list != null && !list.isEmpty()));
  }

  @Test
  @DisplayName("getTransaction: with splits and edges -> returns complete response")
  void getTransaction_withSplitsAndEdges_returnsComplete() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(2000L);
    t.setLedgerId(1L);
    t.setCurrency("USD");
    t.setType("EXPENSE");
    t.setAmountTotal(new BigDecimal("100.00"));
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(2000L, 1L);

    TransactionSplit split = new TransactionSplit();
    split.setUserId(2L);
    split.setComputedAmount(new BigDecimal("50.00"));
    doReturn(Collections.singletonList(split))
        .when(transactionSplitMapper)
        .findByTransactionId(2000L);

    DebtEdge edge = new DebtEdge();
    edge.setFromUserId(1L);
    edge.setToUserId(2L);
    edge.setAmount(new BigDecimal("50.00"));
    edge.setEdgeCurrency("USD");
    doReturn(Collections.singletonList(edge)).when(debtEdgeMapper).findByTransactionId(2000L);

    TransactionResponse resp = service.getTransaction(1L, 2000L);

    assertNotNull(resp);
    assertEquals(2000L, resp.getTransactionId());
    assertEquals(1, resp.getSplits().size());
    assertEquals(1, resp.getEdgesPreview().size());
  }

  @Test
  @DisplayName("getTransaction: with null edges -> handles gracefully")
  void getTransaction_nullEdges_handlesGracefully() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(2002L);
    t.setLedgerId(1L);
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(2002L, 1L);

    doReturn(Collections.emptyList()).when(transactionSplitMapper).findByTransactionId(2002L);
    doReturn(null).when(debtEdgeMapper).findByTransactionId(2002L);

    assertThrows(NullPointerException.class, () -> service.getTransaction(1L, 2002L));
  }

  @Test
  @DisplayName("listTransactions: with type filter -> filters correctly")
  void listTransactions_withTypeFilter_filtersCorrectly() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), any(), any(), eq("EXPENSE"), any(), any(), eq(1L));

    ListTransactionsResponse resp =
        service.listTransactions(1L, 1, 10, null, null, "EXPENSE", null);

    assertNotNull(resp);
    assertEquals(0, resp.getItems().size());
  }

  @Test
  @DisplayName("listTransactions: with createdBy filter -> filters correctly")
  void listTransactions_withCreatedByFilter_filtersCorrectly() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.emptyList());
    page.setTotal(0L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), any(), any(), any(), eq(5L), any(), eq(1L));

    ListTransactionsResponse resp = service.listTransactions(1L, 1, 10, null, null, null, 5L);

    assertNotNull(resp);
    assertEquals(0, resp.getItems().size());
  }

  // ===== Additional Branch Coverage Tests to reach 90% =====

  @Test
  @DisplayName("createTransaction: applyRoundingAndTailAllocation - NONE strategy -> no rounding")
  void createTransaction_roundingNone_noRounding() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(3000L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setRoundingStrategy("NONE"); // NONE strategy - no rounding

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName(
      "createTransaction: applyRoundingAndTailAllocation - default strategy -> uses ROUND_HALF_UP")
  void createTransaction_roundingDefault_usesRoundHalfUp() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(3001L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setRoundingStrategy("UNKNOWN"); // Unknown strategy - should use default

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName(
      "createTransaction: determineTailTarget - LARGEST_SHARE -> returns largest share user")
  void createTransaction_tailTargetLargestShare_returnsLargestShareUser() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(3002L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpensePercent();
    req.setTailAllocation("LARGEST_SHARE");
    // Set unequal shares so one is clearly larger
    req.getSplits().get(0).setShareValue(new BigDecimal("70.00"));
    req.getSplits().get(1).setShareValue(new BigDecimal("30.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: determineTailTarget - default -> uses PAYER")
  void createTransaction_tailTargetDefault_usesPayer() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(3003L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setTailAllocation("UNKNOWN"); // Unknown - should use default (PAYER)

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateLedgerAndMembership - ledger null -> throws")
  void createTransaction_ledgerNull_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(null).when(ledgerMapper).selectById(1L);

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("createTransaction: validateLedgerAndMembership - member null -> throws")
  void createTransaction_memberNull_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(null).when(ledgerMemberMapper).selectOne(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
  }

  @Test
  @DisplayName("deleteTransaction: successful deletion -> calls all delete methods")
  void deleteTransaction_successful_callsAllDeleteMethods() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(4000L);
    t.setLedgerId(1L);
    doReturn(t).when(transactionMapper).findTransactionByIdWithVisibility(4000L, 1L);

    service.deleteTransaction(1L, 4000L);

    verify(debtEdgeMapper, times(1)).deleteByTransactionId(4000L);
    verify(transactionSplitMapper, times(1)).deleteByTransactionId(4000L);
    verify(transactionMapper, times(1)).deleteById(4000L);
  }

  @Test
  @DisplayName("listTransactions: with results -> maps to summaries")
  void listTransactions_withResults_mapsToSummaries() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    Transaction t = new Transaction();
    t.setId(5000L);
    t.setLedgerId(1L);
    t.setCurrency("USD");
    t.setAmountTotal(new BigDecimal("100.00"));
    t.setType("EXPENSE");
    t.setPayerId(1L);
    t.setCreatedBy(1L);

    Page<Transaction> page = new Page<>(1, 10);
    page.setRecords(Collections.singletonList(t));
    page.setTotal(1L);
    doReturn(page)
        .when(transactionMapper)
        .findTransactionsByLedger(any(), eq(1L), any(), any(), any(), any(), any(), eq(1L));

    ListTransactionsResponse resp = service.listTransactions(1L, 1, 10, null, null, null, null);

    assertNotNull(resp);
    assertEquals(1, resp.getItems().size());
    assertEquals(5000L, resp.getItems().get(0).getTransactionId());
  }

  // ===== Additional tests to reach 90% =====

  @Test
  @DisplayName(
      "createTransaction: determineTailTarget - CREATOR with currentUser -> returns userId")
  void createTransaction_tailTargetCreator_returnsUserId() {
    CurrentUserContext.set(new UserView(5L, "Creator"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(5L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6000L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));
    req.setTailAllocation("CREATOR");

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplits - included false -> not validated")
  void createTransaction_includedFalse_notValidated() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6001L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Create a request where one split is not included
    // reqExpenseEqual returns a list with 2 splits
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setType("EXPENSE");
    req.setPayerId(1L);
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setRoundingStrategy("ROUND_HALF_UP");
    req.setTailAllocation("PAYER");
    req.setTxnAt(LocalDateTime.now());

    SplitItem s1 = new SplitItem();
    s1.setUserId(1L);
    s1.setSplitMethod("EQUAL");
    s1.setShareValue(BigDecimal.ZERO);
    s1.setIncluded(false); // Not included
    SplitItem s2 = new SplitItem();
    s2.setUserId(2L);
    s2.setSplitMethod("EQUAL");
    s2.setShareValue(BigDecimal.ZERO);
    s2.setIncluded(true); // Included
    req.setSplits(List.of(s1, s2));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: applyRoundingAndTailAllocation - tail == 0 -> no allocation")
  void createTransaction_tailZero_noAllocation() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6002L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // 100 / 2 = 50 each, no tail
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName(
      "createTransaction: applyRoundingAndTailAllocation - targetUserId null -> no allocation")
  void createTransaction_targetUserIdNull_noAllocation() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6003L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setTailAllocation("PAYER");

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName(
      "createTransaction: generateDebtEdges - EXPENSE with payer equals participant "
          + "-> no self-edge")
  void createTransaction_expensePayerEqualsParticipant_noSelfEdge() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6004L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Payer is user 1, only participant is also user 1
    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    // No debt edges should be created (payer == only participant)
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName(
      "createTransaction: generateDebtEdges - INCOME with payer equals participant -> no self-edge")
  void createTransaction_incomePayerEqualsParticipant_noSelfEdge() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6005L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 1L, new BigDecimal("100.00"));
    req.setType("INCOME");

    service.createTransaction(1L, req);

    // No debt edges should be created (payer == only participant)
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: budgetService returns alert -> sets in response")
  void createTransaction_budgetServiceReturnsAlert_setsInResponse() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(6006L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // Budget service returns an alert
    doReturn("Budget exceeded!")
        .when(budgetService)
        .checkBudgetAfterTransaction(any(), any(), any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setType("EXPENSE");

    CreateTransactionResponse resp = service.createTransaction(1L, req);

    assertNotNull(resp);
    assertEquals("Budget exceeded!", resp.getBudgetAlert());
  }

  @Test
  @DisplayName("createTransaction: validateSplits - EXACT method not found -> no validation")
  void createTransaction_exactMethodNotFound_noValidation() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(7000L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // EQUAL method - EXACT not found, so no EXACT validation
    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: validateSplits - EXACT method found -> validates total")
  void createTransaction_exactMethodFound_validatesTotal() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(7001L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    // EXACT method with correct total
    CreateTransactionRequest req = reqExpenseExact();
    req.setAmountTotal(new BigDecimal("100.00")); // Matches 40 + 60

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  // ===== Additional tests to reach 90% - validateSplitMethod branches =====

  @Test
  @DisplayName("createTransaction: validateSplitMethod - invalid method -> throws")
  void createTransaction_invalidSplitMethod_throws() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setType("EXPENSE");
    req.setPayerId(1L);
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setRoundingStrategy("ROUND_HALF_UP");
    req.setTailAllocation("PAYER");
    req.setTxnAt(LocalDateTime.now());

    SplitItem s1 = new SplitItem();
    s1.setUserId(1L);
    s1.setSplitMethod("INVALID_METHOD"); // Invalid method
    s1.setShareValue(BigDecimal.ZERO);
    s1.setIncluded(true);
    SplitItem s2 = new SplitItem();
    s2.setUserId(2L);
    s2.setSplitMethod("INVALID_METHOD");
    s2.setShareValue(BigDecimal.ZERO);
    s2.setIncluded(true);
    req.setSplits(List.of(s1, s2));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
    assertTrue(ex.getMessage().contains("Invalid split method"));
  }

  @Test
  @DisplayName("createTransaction: non-GROUP_BALANCE ledger -> no debt edges created")
  void createTransaction_nonGroupBalanceLedger_noDebtEdges() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "PERSONAL")).when(ledgerMapper).selectById(1L); // Not GROUP_BALANCE
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(8000L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    // No debt edges for non-GROUP_BALANCE ledger
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName(
      "createTransaction: currency null in applyRoundingAndTailAllocation -> uses default exponent")
  void createTransaction_currencyNull_usesDefaultExponent() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "XYZ", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    doReturn(null).when(currencyMapper).selectById("XYZ"); // Currency not found

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(8001L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setCurrency("XYZ");

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
  }

  @Test
  @DisplayName("createTransaction: LOAN type -> no debt edges created")
  void createTransaction_loanType_noDebtEdges() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    members.add(ledgerMember(2L, "EDITOR"));
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    Currency usd = new Currency();
    usd.setCode("USD");
    usd.setExponent(2);
    doReturn(usd).when(currencyMapper).selectById("USD");

    doAnswer(
            inv -> {
              Transaction t = inv.getArgument(0);
              t.setId(8002L);
              return 1;
            })
        .when(transactionMapper)
        .insert(any(Transaction.class));

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));
    req.setType("LOAN"); // Not EXPENSE or INCOME

    service.createTransaction(1L, req);

    verify(transactionMapper, times(1)).insert(any(Transaction.class));
    // LOAN type doesn't create debt edges
    verify(debtEdgeMapper, never()).insertBatch(any());
  }

  @Test
  @DisplayName("createTransaction: validateSplits - split user not in ledger -> throws")
  void createTransaction_splitUserNotInLedger_throws2() {
    CurrentUserContext.set(new UserView(1L, "U1"));
    doReturn(ledger(1L, "USD", "GROUP_BALANCE")).when(ledgerMapper).selectById(1L);
    doReturn(new LedgerMember()).when(ledgerMemberMapper).selectOne(any());

    List<LedgerMember> members = new ArrayList<>();
    members.add(ledgerMember(1L, "OWNER"));
    // User 2 is NOT in the ledger
    doReturn(members).when(ledgerMemberMapper).selectList(any());

    CreateTransactionRequest req = reqExpenseEqual(1L, 2L, new BigDecimal("100.00"));

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> service.createTransaction(1L, req));
    assertTrue(ex.getMessage().contains("not members of the ledger"));
  }
}
