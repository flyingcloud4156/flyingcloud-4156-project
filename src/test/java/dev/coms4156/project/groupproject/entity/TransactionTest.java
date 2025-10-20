package dev.coms4156.project.groupproject.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.coms4156.project.groupproject.testbase.TestDataBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Transaction entity.
 *
 * <p>Tests cover entity construction, field validation, and business logic methods.
 */
@Tag("entity")
@Tag("transaction")
@DisplayName("Transaction Entity Tests")
class TransactionTest {

  private Transaction testTransaction;

  @BeforeEach
  void setUp() {
    testTransaction = TestDataBuilder.createTestTransaction();
  }

  @Nested
  @DisplayName("Entity Construction Tests")
  class EntityConstructionTests {

    @Test
    @DisplayName("Should create valid transaction with all required fields")
    void shouldCreateValidTransaction_WhenAllFieldsProvided() {
      // Assert
      assertNotNull(testTransaction);
      assertEquals(1L, testTransaction.getId());
      assertEquals(1L, testTransaction.getLedgerId());
      assertEquals(1L, testTransaction.getCreatedBy());
      assertEquals("EXPENSE", testTransaction.getType());
      assertEquals(new BigDecimal("100.00"), testTransaction.getAmountTotal());
      assertEquals("USD", testTransaction.getCurrency());
      assertEquals("Test transaction", testTransaction.getNote());
      assertEquals(1L, testTransaction.getPayerId());
      assertEquals(false, testTransaction.getIsPrivate());
      assertEquals("ROUND_HALF_UP", testTransaction.getRoundingStrategy());
      assertEquals("PAYER", testTransaction.getTailAllocation());
    }

    @Test
    @DisplayName("Should create transaction with null ID for creation scenarios")
    void shouldCreateTransactionWithNullId_WhenForCreation() {
      // Arrange
      Transaction transactionForCreation = TestDataBuilder.createTestTransactionForCreation();

      // Assert
      assertNotNull(transactionForCreation);
      assertNull(transactionForCreation.getId());
      assertEquals("EXPENSE", transactionForCreation.getType());
      assertEquals(new BigDecimal("100.00"), transactionForCreation.getAmountTotal());
    }

    @Test
    @DisplayName("Should handle transaction creation with minimum required fields")
    void shouldCreateTransaction_WhenOnlyRequiredFieldsProvided() {
      // Arrange
      Transaction minimalTransaction = new Transaction();
      minimalTransaction.setType("EXPENSE");
      minimalTransaction.setAmountTotal(new BigDecimal("50.00"));
      minimalTransaction.setCurrency("USD");

      // Assert
      assertNotNull(minimalTransaction);
      assertEquals("EXPENSE", minimalTransaction.getType());
      assertEquals(new BigDecimal("50.00"), minimalTransaction.getAmountTotal());
      assertEquals("USD", minimalTransaction.getCurrency());
      assertNull(minimalTransaction.getId());
      assertNull(minimalTransaction.getNote());
      assertNull(minimalTransaction.getPayerId());
    }
  }

  @Nested
  @DisplayName("Transaction Type Tests")
  class TransactionTypeTests {

    @Test
    @DisplayName("Should handle EXPENSE transaction type")
    void shouldHandleExpenseTransactionType_WhenExpenseTypeProvided() {
      // Act
      testTransaction.setType("EXPENSE");

      // Assert
      assertEquals("EXPENSE", testTransaction.getType());
    }

    @Test
    @DisplayName("Should handle INCOME transaction type")
    void shouldHandleIncomeTransactionType_WhenIncomeTypeProvided() {
      // Act
      testTransaction.setType("INCOME");

      // Assert
      assertEquals("INCOME", testTransaction.getType());
    }

    @Test
    @DisplayName("Should handle LOAN transaction type")
    void shouldHandleLoanTransactionType_WhenLoanTypeProvided() {
      // Act
      testTransaction.setType("LOAN");

      // Assert
      assertEquals("LOAN", testTransaction.getType());
    }
  }

  @Nested
  @DisplayName("Amount and Currency Tests")
  class AmountAndCurrencyTests {

    @Test
    @DisplayName("Should handle various amount values")
    void shouldHandleVariousAmountValues_WhenDifferentAmountsProvided() {
      // Arrange
      BigDecimal[] amounts = {
        new BigDecimal("0.01"),
        new BigDecimal("100.00"),
        new BigDecimal("999.99"),
        new BigDecimal("10000.50")
      };

      // Act & Assert
      for (BigDecimal amount : amounts) {
        testTransaction.setAmountTotal(amount);
        assertEquals(amount, testTransaction.getAmountTotal());
      }
    }

    @Test
    @DisplayName("Should handle various currency codes")
    void shouldHandleVariousCurrencyCodes_WhenDifferentCurrenciesProvided() {
      // Arrange
      String[] currencies = {"USD", "EUR", "GBP", "JPY", "CNY"};

      // Act & Assert
      for (String currency : currencies) {
        testTransaction.setCurrency(currency);
        assertEquals(currency, testTransaction.getCurrency());
      }
    }

    @Test
    @DisplayName("Should handle null amount gracefully")
    void shouldHandleNullAmount_WhenNullProvided() {
      // Act & Assert
      testTransaction.setAmountTotal(null);
      assertNull(testTransaction.getAmountTotal());
    }

    @Test
    @DisplayName("Should handle null currency gracefully")
    void shouldHandleNullCurrency_WhenNullProvided() {
      // Act & Assert
      testTransaction.setCurrency(null);
      assertNull(testTransaction.getCurrency());
    }
  }

  @Nested
  @DisplayName("Rounding and Allocation Strategy Tests")
  class RoundingAndAllocationTests {

    @Test
    @DisplayName("Should handle different rounding strategies")
    void shouldHandleDifferentRoundingStrategies_WhenVariousStrategiesProvided() {
      // Arrange
      String[] strategies = {"NONE", "ROUND_HALF_UP", "TRIM_TO_UNIT"};

      // Act & Assert
      for (String strategy : strategies) {
        testTransaction.setRoundingStrategy(strategy);
        assertEquals(strategy, testTransaction.getRoundingStrategy());
      }
    }

    @Test
    @DisplayName("Should handle different tail allocation strategies")
    void shouldHandleDifferentTailAllocationStrategies_WhenVariousStrategiesProvided() {
      // Arrange
      String[] allocations = {"PAYER", "LARGEST_SHARE", "CREATOR"};

      // Act & Assert
      for (String allocation : allocations) {
        testTransaction.setTailAllocation(allocation);
        assertEquals(allocation, testTransaction.getTailAllocation());
      }
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("Should set and get transaction timestamp correctly")
    void shouldSetAndGetTxnAt_WhenTimestampProvided() {
      // Arrange
      LocalDateTime timestamp = LocalDateTime.now();
      Transaction transaction = TestDataBuilder.createTestTransaction();

      // Act
      transaction.setTxnAt(timestamp);

      // Assert
      assertEquals(timestamp, transaction.getTxnAt());
    }

    @Test
    @DisplayName("Should set and get creation timestamp correctly")
    void shouldSetAndGetCreatedAt_WhenTimestampProvided() {
      // Arrange
      LocalDateTime timestamp = LocalDateTime.now();
      Transaction transaction = TestDataBuilder.createTestTransaction();

      // Act
      transaction.setCreatedAt(timestamp);

      // Assert
      assertEquals(timestamp, transaction.getCreatedAt());
    }

    @Test
    @DisplayName("Should set and get update timestamp correctly")
    void shouldSetAndGetUpdatedAt_WhenTimestampProvided() {
      // Arrange
      LocalDateTime timestamp = LocalDateTime.now();
      Transaction transaction = TestDataBuilder.createTestTransaction();

      // Act
      transaction.setUpdatedAt(timestamp);

      // Assert
      assertEquals(timestamp, transaction.getUpdatedAt());
    }
  }

  @Nested
  @DisplayName("Privacy and Category Tests")
  class PrivacyAndCategoryTests {

    @Test
    @DisplayName("Should handle private transaction flag")
    void shouldHandlePrivateTransactionFlag_WhenBooleanProvided() {
      // Act & Assert
      testTransaction.setIsPrivate(true);
      assertEquals(true, testTransaction.getIsPrivate());

      testTransaction.setIsPrivate(false);
      assertEquals(false, testTransaction.getIsPrivate());
    }

    @Test
    @DisplayName("Should handle null private flag gracefully")
    void shouldHandleNullPrivateFlag_WhenNullProvided() {
      // Act & Assert
      testTransaction.setIsPrivate(null);
      assertNull(testTransaction.getIsPrivate());
    }

    @Test
    @DisplayName("Should handle category ID")
    void shouldHandleCategoryId_WhenCategoryIdProvided() {
      // Act
      testTransaction.setCategoryId(5L);

      // Assert
      assertEquals(5L, testTransaction.getCategoryId());
    }

    @Test
    @DisplayName("Should handle null category ID gracefully")
    void shouldHandleNullCategoryId_WhenNullProvided() {
      // Act & Assert
      testTransaction.setCategoryId(null);
      assertNull(testTransaction.getCategoryId());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("Should be equal when IDs are the same")
    void shouldBeEqual_WhenIdsAreSame() {
      // Arrange
      Transaction transaction1 = TestDataBuilder.createTestTransaction();
      Transaction transaction2 = TestDataBuilder.createTestTransaction();
      transaction2.setId(transaction1.getId());
      // Set same timestamps to ensure equality
      transaction2.setCreatedAt(transaction1.getCreatedAt());
      transaction2.setUpdatedAt(transaction1.getUpdatedAt());
      transaction2.setTxnAt(transaction1.getTxnAt());

      // Act & Assert
      assertEquals(transaction1, transaction2);
      assertEquals(transaction1.hashCode(), transaction2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when IDs are different")
    void shouldNotBeEqual_WhenIdsAreDifferent() {
      // Arrange
      Transaction transaction1 = TestDataBuilder.createTestTransaction();
      Transaction transaction2 = TestDataBuilder.createTestTransaction();
      transaction2.setId(2L);

      // Act & Assert
      assertNotEquals(transaction1, transaction2);
    }

    @Test
    @DisplayName("Should not be equal when comparing to null")
    void shouldNotBeEqual_WhenComparingToNull() {
      // Act & Assert
      assertNotEquals(testTransaction, null);
    }

    @Test
    @DisplayName("Should not be equal when comparing to different class")
    void shouldNotBeEqual_WhenComparingToDifferentClass() {
      // Act & Assert
      assertNotEquals(testTransaction, "some string");
      assertNotEquals(testTransaction, 123);
    }
  }
}
