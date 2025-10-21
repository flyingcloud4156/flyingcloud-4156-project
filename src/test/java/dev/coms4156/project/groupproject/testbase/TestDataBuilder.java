package dev.coms4156.project.groupproject.testbase;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Factory class for creating test data instances.
 *
 * <p>Provides centralized test data creation with realistic values for unit testing. Follows the
 * Factory pattern for consistent test data across all test classes.
 */
public final class TestDataBuilder {

  private TestDataBuilder() {
    // Utility class - prevent instantiation
  }

  /**
   * Creates a test user with valid data.
   *
   * @return a User entity with test data
   */
  public static dev.coms4156.project.groupproject.entity.User createTestUser() {
    dev.coms4156.project.groupproject.entity.User user =
        new dev.coms4156.project.groupproject.entity.User();
    user.setId(1L);
    user.setName("Test User");
    user.setEmail("test@example.com");
    user.setPhone("+1234567890");
    user.setPasswordHash("hashedPassword123");
    user.setTimezone("UTC");
    user.setMainCurrency("USD");
    user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    return user;
  }

  /**
   * Creates a test user with null ID (for creation tests).
   *
   * @return a User entity without ID for creation scenarios
   */
  public static dev.coms4156.project.groupproject.entity.User createTestUserForCreation() {
    dev.coms4156.project.groupproject.entity.User user = createTestUser();
    user.setId(null);
    return user;
  }

  /**
   * Creates a test ledger with valid data.
   *
   * @return a Ledger entity with test data
   */
  public static dev.coms4156.project.groupproject.entity.Ledger createTestLedger() {
    dev.coms4156.project.groupproject.entity.Ledger ledger =
        new dev.coms4156.project.groupproject.entity.Ledger();
    ledger.setId(1L);
    ledger.setName("Test Ledger");
    ledger.setOwnerId(1L);
    ledger.setLedgerType("GROUP_BALANCE");
    ledger.setBaseCurrency("USD");
    ledger.setShareStartDate(LocalDateTime.now(ZoneOffset.UTC).toLocalDate());
    ledger.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    ledger.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    return ledger;
  }

  /**
   * Creates a test ledger with null ID (for creation tests).
   *
   * @return a Ledger entity without ID for creation scenarios
   */
  public static dev.coms4156.project.groupproject.entity.Ledger createTestLedgerForCreation() {
    dev.coms4156.project.groupproject.entity.Ledger ledger = createTestLedger();
    ledger.setId(null);
    return ledger;
  }

  /**
   * Creates test user with invalid email (for validation tests).
   *
   * @return a User entity with invalid email
   */
  public static dev.coms4156.project.groupproject.entity.User createTestUserWithInvalidEmail() {
    dev.coms4156.project.groupproject.entity.User user = createTestUser();
    user.setEmail("invalid-email");
    return user;
  }

  /**
   * Creates test user with empty name (for validation tests).
   *
   * @return a User entity with empty name
   */
  public static dev.coms4156.project.groupproject.entity.User createTestUserWithEmptyName() {
    dev.coms4156.project.groupproject.entity.User user = createTestUser();
    user.setName("");
    return user;
  }

  /**
   * Creates test ledger with invalid type (for validation tests).
   *
   * @return a Ledger entity with invalid type
   */
  public static dev.coms4156.project.groupproject.entity.Ledger createTestLedgerWithInvalidType() {
    dev.coms4156.project.groupproject.entity.Ledger ledger = createTestLedger();
    ledger.setLedgerType("INVALID_TYPE");
    return ledger;
  }

  /**
   * Creates a test transaction with valid data.
   *
   * @return a Transaction entity with test data
   */
  public static dev.coms4156.project.groupproject.entity.Transaction createTestTransaction() {
    dev.coms4156.project.groupproject.entity.Transaction transaction =
        new dev.coms4156.project.groupproject.entity.Transaction();
    transaction.setId(1L);
    transaction.setLedgerId(1L);
    transaction.setCreatedBy(1L);
    transaction.setTxnAt(LocalDateTime.now(ZoneOffset.UTC));
    transaction.setType("EXPENSE");
    transaction.setAmountTotal(new java.math.BigDecimal("100.00"));
    transaction.setCurrency("USD");
    transaction.setNote("Test transaction");
    transaction.setPayerId(1L);
    transaction.setIsPrivate(false);
    transaction.setRoundingStrategy("ROUND_HALF_UP");
    transaction.setTailAllocation("PAYER");
    transaction.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    transaction.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    return transaction;
  }

  /**
   * Creates a test transaction with null ID (for creation tests).
   *
   * @return a Transaction entity without ID for creation scenarios
   */
  public static dev.coms4156.project.groupproject.entity.Transaction
      createTestTransactionForCreation() {
    dev.coms4156.project.groupproject.entity.Transaction transaction = createTestTransaction();
    transaction.setId(null);
    return transaction;
  }

  /**
   * Creates a test category with valid data.
   *
   * @return a Category entity with test data
   */
  public static dev.coms4156.project.groupproject.entity.Category createTestCategory() {
    dev.coms4156.project.groupproject.entity.Category category =
        new dev.coms4156.project.groupproject.entity.Category();
    category.setId(1L);
    category.setLedgerId(1L);
    category.setName("Food");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    category.setSortOrder(1);
    return category;
  }

  /**
   * Creates a test category with null ID (for creation tests).
   *
   * @return a Category entity without ID for creation scenarios
   */
  public static dev.coms4156.project.groupproject.entity.Category createTestCategoryForCreation() {
    dev.coms4156.project.groupproject.entity.Category category = createTestCategory();
    category.setId(null);
    return category;
  }

  /**
   * Creates a test category with inactive status.
   *
   * @return a Category entity with inactive status
   */
  public static dev.coms4156.project.groupproject.entity.Category createTestInactiveCategory() {
    dev.coms4156.project.groupproject.entity.Category category = createTestCategory();
    category.setIsActive(false);
    return category;
  }

  /**
   * Creates a test category with invalid kind (for validation tests).
   *
   * @return a Category entity with invalid kind
   */
  public static dev.coms4156.project.groupproject.entity.Category
      createTestCategoryWithInvalidKind() {
    dev.coms4156.project.groupproject.entity.Category category = createTestCategory();
    category.setKind("INVALID_KIND");
    return category;
  }

  /**
   * Creates a test category with empty name (for validation tests).
   *
   * @return a Category entity with empty name
   */
  public static dev.coms4156.project.groupproject.entity.Category
      createTestCategoryWithEmptyName() {
    dev.coms4156.project.groupproject.entity.Category category = createTestCategory();
    category.setName("");
    return category;
  }

  /**
   * Creates a test ledger member with valid data.
   *
   * @return a LedgerMember entity with test data
   */
  public static dev.coms4156.project.groupproject.entity.LedgerMember createTestLedgerMember() {
    dev.coms4156.project.groupproject.entity.LedgerMember member =
        new dev.coms4156.project.groupproject.entity.LedgerMember();
    member.setLedgerId(1L);
    member.setUserId(1L);
    member.setRole("OWNER");
    return member;
  }

  /**
   * Creates a test ledger member with specific role.
   *
   * @param role the role for the member
   * @return a LedgerMember entity with specified role
   */
  public static dev.coms4156.project.groupproject.entity.LedgerMember createTestLedgerMember(
      String role) {
    dev.coms4156.project.groupproject.entity.LedgerMember member = createTestLedgerMember();
    member.setRole(role);
    return member;
  }

  /**
   * Creates a test user view for authentication context.
   *
   * @return a UserView with test data
   */
  public static dev.coms4156.project.groupproject.dto.UserView createTestUserView() {
    dev.coms4156.project.groupproject.dto.UserView userView =
        new dev.coms4156.project.groupproject.dto.UserView();
    userView.setId(1L);
    userView.setName("Test User");
    return userView;
  }

  /**
   * Creates a test create category request.
   *
   * @return a CreateCategoryRequest with test data
   */
  public static dev.coms4156.project.groupproject.dto.CreateCategoryRequest
      createTestCreateCategoryRequest() {
    dev.coms4156.project.groupproject.dto.CreateCategoryRequest request =
        new dev.coms4156.project.groupproject.dto.CreateCategoryRequest();
    request.setName("Food");
    request.setKind("EXPENSE");
    request.setSortOrder(1);
    return request;
  }

  /**
   * Creates a test create category request with empty name (for validation tests).
   *
   * @return a CreateCategoryRequest with empty name
   */
  public static dev.coms4156.project.groupproject.dto.CreateCategoryRequest
      createTestCreateCategoryRequestWithEmptyName() {
    dev.coms4156.project.groupproject.dto.CreateCategoryRequest request =
        createTestCreateCategoryRequest();
    request.setName("");
    return request;
  }
}
