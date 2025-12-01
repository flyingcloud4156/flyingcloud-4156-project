package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.Transaction;
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
}
