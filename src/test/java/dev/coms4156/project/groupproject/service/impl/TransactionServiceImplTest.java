package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.LoanRequest;
import dev.coms4156.project.groupproject.dto.SplitItem;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TransactionServiceImpl.
 *
 * <p>This suite tests the business logic of the TransactionService in isolation, using mocks for
 * all data-layer dependencies (mappers). It verifies transaction creation, validation, split
 * calculations, and debt edge generation.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

  @Mock private TransactionMapper transactionMapper;
  @Mock private TransactionSplitMapper transactionSplitMapper;
  @Mock private DebtEdgeMapper debtEdgeMapper;
  @Mock private LedgerMapper ledgerMapper;
  @Mock private LedgerMemberMapper ledgerMemberMapper;
  @Mock private CurrencyMapper currencyMapper;

  @InjectMocks private TransactionServiceImpl transactionService;

  private UserView testUser;
  private Ledger testLedger;
  private Currency testCurrency;

  @BeforeEach
  void setUp() {
    // Arrange: Set up a consistent context for each test
    testUser = new UserView(1L, "Test User");
    CurrentUserContext.set(testUser);

    testLedger = new Ledger();
    testLedger.setId(1L);
    testLedger.setName("Test Ledger");
    testLedger.setOwnerId(1L);
    testLedger.setBaseCurrency("USD");
    testLedger.setLedgerType("GROUP_BALANCE");

    testCurrency = new Currency();
    testCurrency.setCode("USD");
    testCurrency.setExponent(2);
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Nested
  @DisplayName("Create Transaction Tests")
  class CreateTransactionTests {

    @Test
    @DisplayName("should throw exception when user is not authenticated")
    void createTransaction_shouldThrowException_whenUserNotAuthenticated() {
      // Arrange
      CurrentUserContext.clear();
      CreateTransactionRequest request = new CreateTransactionRequest();

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> transactionService.createTransaction(1L, request));
      assertEquals("Not logged in", exception.getMessage());
    }

    @Test
    @DisplayName("should throw exception when ledger is not found")
    void createTransaction_shouldThrowException_whenLedgerNotFound() {
      // Arrange
      when(ledgerMapper.selectById(anyLong())).thenReturn(null);
      CreateTransactionRequest request = new CreateTransactionRequest();

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> transactionService.createTransaction(1L, request));
      assertEquals("Ledger not found", exception.getMessage());
    }

    @Test
    @DisplayName("should throw exception when currency does not match ledger base currency")
    void createTransaction_shouldThrowException_whenCurrencyMismatches() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());
      CreateTransactionRequest request = new CreateTransactionRequest();
      request.setCurrency("EUR");

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> transactionService.createTransaction(1L, request));
      assertEquals("Currency mismatch", exception.getMessage());
    }

    @Test
    @DisplayName("should create a LOAN transaction successfully")
    void createTransaction_shouldCreateLoanSuccessfully() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());

      CreateTransactionRequest request = new CreateTransactionRequest();
      request.setType("LOAN");
      request.setCurrency("USD");
      request.setAmountTotal(new BigDecimal("100.00"));
      request.setTxnAt(LocalDateTime.now());

      LoanRequest loanDetails = new LoanRequest();
      loanDetails.setCreditorUserId(1L);
      loanDetails.setDebtorUserId(2L);
      request.setLoan(loanDetails);

      ArgumentCaptor<DebtEdge> debtEdgeCaptor = ArgumentCaptor.forClass(DebtEdge.class);

      // Act
      transactionService.createTransaction(1L, request);

      // Assert
      verify(debtEdgeMapper).insert(debtEdgeCaptor.capture());
      DebtEdge capturedEdge = debtEdgeCaptor.getValue();

      assertEquals(1L, capturedEdge.getLedgerId());
      assertEquals(1L, capturedEdge.getFromUserId()); // Creditor
      assertEquals(2L, capturedEdge.getToUserId()); // Debtor
      assertEquals(0, new BigDecimal("100.00").compareTo(capturedEdge.getAmount()));
      assertEquals("USD", capturedEdge.getEdgeCurrency());
    }

    @Test
    @DisplayName("should throw exception for LOAN when creditor and debtor are the same")
    void createTransaction_shouldThrowException_forLoanWithSameCreditorDebtor() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());

      CreateTransactionRequest request = new CreateTransactionRequest();
      request.setType("LOAN");
      request.setCurrency("USD");
      request.setAmountTotal(new BigDecimal("100.00"));
      request.setTxnAt(LocalDateTime.now());

      LoanRequest loanDetails = new LoanRequest();
      loanDetails.setCreditorUserId(1L);
      loanDetails.setDebtorUserId(1L); // Same user
      request.setLoan(loanDetails);

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> transactionService.createTransaction(1L, request));
      assertEquals("Creditor and debtor cannot be the same", exception.getMessage());
    }

    @Test
    @DisplayName("should create an EXPENSE transaction with EQUAL split successfully")
    void createTransaction_shouldCreateExpenseWithEqualSplit() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());
      when(currencyMapper.selectById("USD")).thenReturn(testCurrency);

      CreateTransactionRequest request = new CreateTransactionRequest();
      request.setType("EXPENSE");
      request.setCurrency("USD");
      request.setAmountTotal(new BigDecimal("99.99"));
      request.setPayerId(1L);
      request.setTxnAt(LocalDateTime.now());

      SplitItem split1 = new SplitItem();
      split1.setUserId(1L);
      split1.setSplitMethod("EQUAL");
      split1.setShareValue(BigDecimal.ZERO);

      SplitItem split2 = new SplitItem();
      split2.setUserId(2L);
      split2.setSplitMethod("EQUAL");
      split2.setShareValue(BigDecimal.ZERO);

      SplitItem split3 = new SplitItem();
      split3.setUserId(3L);
      split3.setSplitMethod("EQUAL");
      split3.setShareValue(BigDecimal.ZERO);

      request.setSplits(Arrays.asList(split1, split2, split3));

      ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
      ArgumentCaptor<List<TransactionSplit>> splitsCaptor = ArgumentCaptor.forClass(List.class);
      ArgumentCaptor<List<DebtEdge>> edgesCaptor = ArgumentCaptor.forClass(List.class);

      // Act
      transactionService.createTransaction(1L, request);

      // Assert
      verify(transactionMapper).insert(transactionCaptor.capture());
      verify(transactionSplitMapper).insertBatch(splitsCaptor.capture());
      verify(debtEdgeMapper).insertBatch(edgesCaptor.capture());

      // Assert Transaction
      assertEquals(
          0, new BigDecimal("99.99").compareTo(transactionCaptor.getValue().getAmountTotal()));

      // Assert Splits (33.33 * 3 = 99.99)
      List<TransactionSplit> capturedSplits = splitsCaptor.getValue();
      assertEquals(3, capturedSplits.size());
      assertEquals(0, new BigDecimal("33.33").compareTo(capturedSplits.get(0).getComputedAmount()));
      assertEquals(0, new BigDecimal("33.33").compareTo(capturedSplits.get(1).getComputedAmount()));
      assertEquals(0, new BigDecimal("33.33").compareTo(capturedSplits.get(2).getComputedAmount()));

      // Assert Debt Edges (Payer was user 1, so users 2 and 3 owe user 1)
      List<DebtEdge> capturedEdges = edgesCaptor.getValue();
      assertEquals(2, capturedEdges.size());
      // Edge 1: User 2 owes User 1
      assertEquals(1L, capturedEdges.get(0).getFromUserId());
      assertEquals(2L, capturedEdges.get(0).getToUserId());
      assertEquals(0, new BigDecimal("33.33").compareTo(capturedEdges.get(0).getAmount()));
      // Edge 2: User 3 owes User 1
      assertEquals(1L, capturedEdges.get(1).getFromUserId());
      assertEquals(3L, capturedEdges.get(1).getToUserId());
      assertEquals(0, new BigDecimal("33.33").compareTo(capturedEdges.get(1).getAmount()));
    }

    @Test
    @DisplayName("should throw exception for EXPENSE if splits are empty")
    void createTransaction_shouldThrowException_forExpenseWithEmptySplits() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());

      CreateTransactionRequest request = new CreateTransactionRequest();
      request.setType("EXPENSE");
      request.setCurrency("USD");
      request.setAmountTotal(new BigDecimal("100.00"));
      request.setTxnAt(LocalDateTime.now());
      request.setSplits(Collections.emptyList());

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> transactionService.createTransaction(1L, request));
      assertEquals("Splits required for EXPENSE/INCOME type", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Get and List Transaction Tests")
  class GetAndListTransactionTests {
    @Test
    @DisplayName("getTransaction should throw exception when user not authenticated")
    void getTransaction_shouldThrowException_whenUserNotAuthenticated() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> transactionService.getTransaction(1L, 1L));
      assertEquals("Not logged in", exception.getMessage());
    }

    @Test
    @DisplayName("getTransaction should throw exception for transaction not in ledger")
    void getTransaction_shouldThrowException_whenTransactionNotInLedger() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());

      Transaction transaction = new Transaction();
      transaction.setId(1L);
      transaction.setLedgerId(2L); // Different ledger
      when(transactionMapper.findTransactionByIdWithVisibility(1L, 1L)).thenReturn(transaction);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> transactionService.getTransaction(1L, 1L));
      assertEquals("Transaction not found in this ledger", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Delete Transaction Tests")
  class DeleteTransactionTests {
    @Test
    @DisplayName("deleteTransaction should throw exception when user not authenticated")
    void deleteTransaction_shouldThrowException_whenUserNotAuthenticated() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> transactionService.deleteTransaction(1L, 1L));
      assertEquals("Not logged in", exception.getMessage());
    }

    @Test
    @DisplayName("deleteTransaction should throw exception when transaction not found")
    void deleteTransaction_shouldThrowException_whenTransactionNotFound() {
      // Arrange
      when(ledgerMapper.selectById(1L)).thenReturn(testLedger);
      when(ledgerMemberMapper.selectOne(any())).thenReturn(new LedgerMember());
      when(transactionMapper.findTransactionByIdWithVisibility(anyLong(), anyLong()))
          .thenReturn(null);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> transactionService.deleteTransaction(1L, 1L));
      assertEquals("Transaction not found", exception.getMessage());
    }
  }
}
