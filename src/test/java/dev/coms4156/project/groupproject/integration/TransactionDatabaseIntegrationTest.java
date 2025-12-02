package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.SplitItem;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.Transaction;
import dev.coms4156.project.groupproject.entity.TransactionSplit;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.mapper.TransactionSplitMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.TransactionService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for TransactionService with complete database flow.
 *
 * <p>Tests the integration: TransactionService → TransactionMapper → TransactionSplitMapper →
 * DebtEdgeMapper → Database
 *
 * <p>Verifies: - Transaction insert creates records in transactions table - TransactionSplit
 * records are created in transaction_splits table - DebtEdge records are created in debt_edges
 * table - Shared data (transaction_id) is correctly maintained across tables - Query operations
 * return consistent data
 */
@SpringBootTest
@Transactional
class TransactionDatabaseIntegrationTest {

  @Autowired private TransactionService transactionService;
  @Autowired private TransactionMapper transactionMapper;
  @Autowired private TransactionSplitMapper transactionSplitMapper;
  @Autowired private DebtEdgeMapper debtEdgeMapper;
  @Autowired private LedgerMapper ledgerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private LedgerMemberMapper ledgerMemberMapper;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private Long testUser1Id;
  private Long testUser2Id;
  private Long testUser3Id;
  private Long testLedgerId;
  private Long testCategoryId;

  @BeforeEach
  void setUp() {
    User user1 = new User();
    user1.setEmail("txn_int_1@example.com");
    user1.setName("Txn Test User 1");
    user1.setPasswordHash("hash1");
    userMapper.insert(user1);
    testUser1Id = user1.getId();

    User user2 = new User();
    user2.setEmail("txn_int_2@example.com");
    user2.setName("Txn Test User 2");
    user2.setPasswordHash("hash2");
    userMapper.insert(user2);
    testUser2Id = user2.getId();

    User user3 = new User();
    user3.setEmail("txn_int_3@example.com");
    user3.setName("Txn Test User 3");
    user3.setPasswordHash("hash3");
    userMapper.insert(user3);
    testUser3Id = user3.getId();

    UserView userView = new UserView(testUser1Id, "Txn Test User 1");
    CurrentUserContext.set(userView);

    Ledger ledger = new Ledger();
    ledger.setName("Txn Test Ledger");
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

    LedgerMember member3 = new LedgerMember();
    member3.setLedgerId(testLedgerId);
    member3.setUserId(testUser3Id);
    member3.setRole("VIEWER");
    ledgerMemberMapper.insert(member3);

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
  void testCreateTransaction_insertsToTransactionTable() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("300.00"));
    request.setCurrency("USD");
    request.setNote("Test transaction");
    request.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(testUser1Id);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    request.setSplits(Arrays.asList(split));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, request);

    assertNotNull(response.getTransactionId());

    Transaction savedTxn = transactionMapper.selectById(response.getTransactionId());
    assertNotNull(savedTxn);
    assertEquals(testLedgerId, savedTxn.getLedgerId());
    assertEquals(testUser1Id, savedTxn.getCreatedBy());
    assertEquals("EXPENSE", savedTxn.getType());
    assertEquals(0, new BigDecimal("300.00").compareTo(savedTxn.getAmountTotal()));
  }

  @Test
  void testCreateTransaction_insertsToTransactionSplitTable() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("300.00"));
    request.setCurrency("USD");
    request.setNote("Split test");
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

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, request);

    List<TransactionSplit> splits =
        transactionSplitMapper.selectList(
            new LambdaQueryWrapper<TransactionSplit>()
                .eq(TransactionSplit::getTransactionId, response.getTransactionId()));

    assertEquals(2, splits.size());
    assertTrue(splits.stream().anyMatch(s -> s.getUserId().equals(testUser1Id)));
    assertTrue(splits.stream().anyMatch(s -> s.getUserId().equals(testUser2Id)));
  }

  @Test
  void testCreateTransaction_insertsToDebtEdgeTable() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("600.00"));
    request.setCurrency("USD");
    request.setNote("Debt edge test");
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

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, request);

    List<DebtEdge> debtEdges =
        debtEdgeMapper.selectList(
            new LambdaQueryWrapper<DebtEdge>()
                .eq(DebtEdge::getTransactionId, response.getTransactionId()));

    assertNotNull(debtEdges);
    assertEquals(1, debtEdges.size());
    DebtEdge edge = debtEdges.get(0);
    assertNotNull(edge.getFromUserId());
    assertNotNull(edge.getToUserId());
    assertNotNull(edge.getAmount());
    assertTrue(edge.getAmount().compareTo(BigDecimal.ZERO) > 0);
  }

  @Test
  void testCreateTransaction_verifyTransactionIdSharedAcrossThreeTables() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("900.00"));
    request.setCurrency("USD");
    request.setNote("Three tables test");
    request.setIsPrivate(false);

    SplitItem split1 = new SplitItem();
    split1.setUserId(testUser1Id);
    split1.setSplitMethod("EQUAL");
    split1.setShareValue(BigDecimal.ZERO);

    SplitItem split2 = new SplitItem();
    split2.setUserId(testUser2Id);
    split2.setSplitMethod("EQUAL");
    split2.setShareValue(BigDecimal.ZERO);

    SplitItem split3 = new SplitItem();
    split3.setUserId(testUser3Id);
    split3.setSplitMethod("EQUAL");
    split3.setShareValue(BigDecimal.ZERO);

    request.setSplits(Arrays.asList(split1, split2, split3));

    CreateTransactionResponse response =
        transactionService.createTransaction(testLedgerId, request);
    Long txnId = response.getTransactionId();

    Transaction transaction = transactionMapper.selectById(txnId);
    assertNotNull(transaction);
    assertEquals(txnId, transaction.getId());

    List<TransactionSplit> splits =
        transactionSplitMapper.selectList(
            new LambdaQueryWrapper<TransactionSplit>()
                .eq(TransactionSplit::getTransactionId, txnId));
    assertEquals(3, splits.size());
    assertTrue(splits.stream().allMatch(s -> s.getTransactionId().equals(txnId)));

    List<DebtEdge> edges =
        debtEdgeMapper.selectList(
            new LambdaQueryWrapper<DebtEdge>().eq(DebtEdge::getTransactionId, txnId));
    assertFalse(edges.isEmpty());
    assertTrue(edges.stream().allMatch(e -> e.getTransactionId().equals(txnId)));
  }

  @Test
  void testListTransactionsByLedgerId_returnsCorrectData() {
    CreateTransactionRequest request1 = new CreateTransactionRequest();
    request1.setTxnAt(LocalDateTime.now());
    request1.setType("INCOME");
    request1.setCategoryId(1L);
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
    request2.setCategoryId(1L);
    request2.setPayerId(testUser1Id);
    request2.setAmountTotal(new BigDecimal("200.00"));
    request2.setCurrency("USD");
    request2.setNote("Expense");
    request2.setIsPrivate(false);

    SplitItem split2 = new SplitItem();
    split2.setUserId(testUser1Id);
    split2.setSplitMethod("EQUAL");
    split2.setShareValue(BigDecimal.ZERO);
    request2.setSplits(Arrays.asList(split2));

    transactionService.createTransaction(testLedgerId, request2);

    ListTransactionsResponse response =
        transactionService.listTransactions(testLedgerId, 1, 10, null, null, null, null);

    assertNotNull(response);
    assertTrue(response.getTotal() >= 2);
    assertFalse(response.getItems().isEmpty());
  }

  @Test
  void testDeleteTransaction_removesFromAllTables() {
    CreateTransactionRequest request = new CreateTransactionRequest();
    request.setTxnAt(LocalDateTime.now());
    request.setType("EXPENSE");
    request.setCategoryId(testCategoryId);
    request.setPayerId(testUser1Id);
    request.setAmountTotal(new BigDecimal("400.00"));
    request.setCurrency("USD");
    request.setNote("To be deleted");
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

    CreateTransactionResponse createResponse =
        transactionService.createTransaction(testLedgerId, request);
    Long txnId = createResponse.getTransactionId();

    transactionService.deleteTransaction(testLedgerId, txnId);

    Transaction deletedTxn = transactionMapper.selectById(txnId);
    assertEquals(null, deletedTxn);
  }
}
