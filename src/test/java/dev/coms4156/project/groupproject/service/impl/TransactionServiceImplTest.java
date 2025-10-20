package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Currency;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.CurrencyMapper;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.TransactionMapper;
import dev.coms4156.project.groupproject.mapper.TransactionSplitMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TransactionServiceImpl.
 *
 * <p>Tests cover core transaction functionality including creation, retrieval, and basic business
 * logic validation.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

  @Mock private TransactionMapper transactionMapper;
  @Mock private TransactionSplitMapper transactionSplitMapper;
  @Mock private DebtEdgeMapper debtEdgeMapper;
  @Mock private LedgerMapper ledgerMapper;
  @Mock private LedgerMemberMapper ledgerMemberMapper;
  @Mock private UserMapper userMapper;
  @Mock private CurrencyMapper currencyMapper;

  @InjectMocks private TransactionServiceImpl transactionService;

  private User testUser;
  private Ledger testLedger;
  private Currency testCurrency;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(1L);
    testUser.setName("Test User");
    testUser.setEmail("test@example.com");
    CurrentUserContext.set(new UserView(1L, "Test User"));

    testLedger = new Ledger();
    testLedger.setId(1L);
    testLedger.setName("Test Ledger");
    testLedger.setOwnerId(1L);
    testLedger.setBaseCurrency("USD");

    testCurrency = new Currency();
    testCurrency.setCode("USD");
    testCurrency.setExponent(2);
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  @DisplayName("createTransaction should throw exception when user not authenticated")
  void createTransaction_ShouldThrowException_WhenUserNotAuthenticated() {
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
  @DisplayName("createTransaction should throw exception when ledger not found")
  void createTransaction_ShouldThrowException_WhenLedgerNotFound() {
    // Arrange
    CreateTransactionRequest request = new CreateTransactionRequest();
    when(ledgerMapper.selectById(1L)).thenReturn(null);

    // Act & Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class, () -> transactionService.createTransaction(1L, request));
    assertEquals("Ledger not found", exception.getMessage());
  }

  @Test
  @DisplayName("getTransaction should throw exception when user not authenticated")
  void getTransaction_ShouldThrowException_WhenUserNotAuthenticated() {
    // Arrange
    CurrentUserContext.clear();

    // Act & Assert
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> transactionService.getTransaction(1L, 1L));
    assertEquals("Not logged in", exception.getMessage());
  }

  @Test
  @DisplayName("deleteTransaction should throw exception when user not authenticated")
  void deleteTransaction_ShouldThrowException_WhenUserNotAuthenticated() {
    // Arrange
    CurrentUserContext.clear();

    // Act & Assert
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> transactionService.deleteTransaction(1L, 1L));
    assertEquals("Not logged in", exception.getMessage());
  }
}
