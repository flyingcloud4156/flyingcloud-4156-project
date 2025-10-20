package dev.coms4156.project.groupproject.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Transaction DTOs validation.
 *
 * <p>Tests cover validation annotations, required fields, and data constraints.
 */
@Tag("dto")
@Tag("validation")
@DisplayName("Transaction DTO Validation Tests")
class TransactionDtoTest {

  private static Validator validator;
  private CreateTransactionRequest createRequest;
  private SplitItem splitItem;

  @BeforeAll
  static void setUpValidator() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @BeforeEach
  void setUp() {
    createRequest = new CreateTransactionRequest();
    createRequest.setTxnAt(LocalDateTime.now());
    createRequest.setType("EXPENSE");
    createRequest.setCurrency("USD");
    createRequest.setAmountTotal(new BigDecimal("100.00"));
    createRequest.setPayerId(1L);

    splitItem = new SplitItem();
    splitItem.setUserId(1L);
    splitItem.setSplitMethod("EQUAL");
    splitItem.setShareValue(BigDecimal.ZERO);
    splitItem.setIncluded(true);
  }

  @Nested
  @DisplayName("CreateTransactionRequest Validation Tests")
  class CreateTransactionRequestValidationTests {

    @Test
    @DisplayName("Should pass validation with valid request")
    void shouldPassValidation_WhenValidRequestProvided() {
      // Arrange
      createRequest.setSplits(List.of(splitItem));

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Should fail validation when txnAt is null")
    void shouldFailValidation_WhenTxnAtIsNull() {
      // Arrange
      createRequest.setTxnAt(null);

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Transaction timestamp is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when type is null")
    void shouldFailValidation_WhenTypeIsNull() {
      // Arrange
      createRequest.setType(null);

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Transaction type is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when type is invalid")
    void shouldFailValidation_WhenTypeIsInvalid() {
      // Arrange
      createRequest.setType("INVALID_TYPE");

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals(
          "Type must be EXPENSE, INCOME, or LOAN", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when currency is null")
    void shouldFailValidation_WhenCurrencyIsNull() {
      // Arrange
      createRequest.setCurrency(null);

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Currency is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when currency format is invalid")
    void shouldFailValidation_WhenCurrencyFormatIsInvalid() {
      // Arrange
      createRequest.setCurrency("INVALID");

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Currency must be a 3-letter code", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when amountTotal is null")
    void shouldFailValidation_WhenAmountTotalIsNull() {
      // Arrange
      createRequest.setAmountTotal(null);

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Total amount is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when amountTotal is negative")
    void shouldFailValidation_WhenAmountTotalIsNegative() {
      // Arrange
      createRequest.setAmountTotal(new BigDecimal("-10.00"));

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Amount must be positive", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when amountTotal is zero")
    void shouldFailValidation_WhenAmountTotalIsZero() {
      // Arrange
      createRequest.setAmountTotal(BigDecimal.ZERO);

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Amount must be positive", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when note is too long")
    void shouldFailValidation_WhenNoteIsTooLong() {
      // Arrange
      createRequest.setNote("a".repeat(501)); // Exceeds 500 character limit

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Note cannot exceed 500 characters", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should pass validation when note is at maximum length")
    void shouldPassValidation_WhenNoteIsAtMaximumLength() {
      // Arrange
      createRequest.setNote("a".repeat(500)); // Exactly 500 characters
      createRequest.setSplits(List.of(splitItem));

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Should fail validation when rounding strategy is invalid")
    void shouldFailValidation_WhenRoundingStrategyIsInvalid() {
      // Arrange
      createRequest.setRoundingStrategy("INVALID_STRATEGY");

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals(
          "Rounding strategy must be NONE, ROUND_HALF_UP, or TRIM_TO_UNIT",
          violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when tail allocation is invalid")
    void shouldFailValidation_WhenTailAllocationIsInvalid() {
      // Arrange
      createRequest.setTailAllocation("INVALID_ALLOCATION");

      // Act
      Set<ConstraintViolation<CreateTransactionRequest>> violations =
          validator.validate(createRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals(
          "Tail allocation must be PAYER, LARGEST_SHARE, or CREATOR",
          violations.iterator().next().getMessage());
    }
  }

  @Nested
  @DisplayName("SplitItem Validation Tests")
  class SplitItemValidationTests {

    @Test
    @DisplayName("Should pass validation with valid split item")
    void shouldPassValidation_WhenValidSplitItemProvided() {
      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert
      assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Should fail validation when userId is null")
    void shouldFailValidation_WhenUserIdIsNull() {
      // Arrange
      splitItem.setUserId(null);

      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("User ID is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when splitMethod is null")
    void shouldFailValidation_WhenSplitMethodIsNull() {
      // Arrange
      splitItem.setSplitMethod(null);

      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Split method is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when shareValue is negative")
    void shouldFailValidation_WhenShareValueIsNegative() {
      // Arrange
      splitItem.setShareValue(new BigDecimal("-1.00"));

      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Share value must be non-negative", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should pass validation when shareValue is zero")
    void shouldPassValidation_WhenShareValueIsZero() {
      // Arrange
      splitItem.setShareValue(BigDecimal.ZERO);

      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert
      assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Should pass validation when shareValue is positive")
    void shouldPassValidation_WhenShareValueIsPositive() {
      // Arrange
      splitItem.setShareValue(new BigDecimal("50.00"));

      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert
      assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Should handle null included flag gracefully")
    void shouldHandleNullIncludedFlag_WhenNullProvided() {
      // Arrange
      splitItem.setIncluded(null);

      // Act
      Set<ConstraintViolation<SplitItem>> violations = validator.validate(splitItem);

      // Assert - included is optional, so no validation error
      assertEquals(0, violations.size());
      assertNull(splitItem.getIncluded());
    }
  }

  @Nested
  @DisplayName("LoanRequest Validation Tests")
  class LoanRequestValidationTests {

    @Test
    @DisplayName("Should pass validation with valid loan request")
    void shouldPassValidation_WhenValidLoanRequestProvided() {
      // Arrange
      LoanRequest loanRequest = new LoanRequest();
      loanRequest.setCreditorUserId(1L);
      loanRequest.setDebtorUserId(2L);

      // Act
      Set<ConstraintViolation<LoanRequest>> violations = validator.validate(loanRequest);

      // Assert
      assertEquals(0, violations.size());
    }

    @Test
    @DisplayName("Should fail validation when creditorUserId is null")
    void shouldFailValidation_WhenCreditorUserIdIsNull() {
      // Arrange
      LoanRequest loanRequest = new LoanRequest();
      loanRequest.setCreditorUserId(null);
      loanRequest.setDebtorUserId(2L);

      // Act
      Set<ConstraintViolation<LoanRequest>> violations = validator.validate(loanRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Creditor user ID is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("Should fail validation when debtorUserId is null")
    void shouldFailValidation_WhenDebtorUserIdIsNull() {
      // Arrange
      LoanRequest loanRequest = new LoanRequest();
      loanRequest.setCreditorUserId(1L);
      loanRequest.setDebtorUserId(null);

      // Act
      Set<ConstraintViolation<LoanRequest>> violations = validator.validate(loanRequest);

      // Assert
      assertEquals(1, violations.size());
      assertEquals("Debtor user ID is required", violations.iterator().next().getMessage());
    }
  }
}
