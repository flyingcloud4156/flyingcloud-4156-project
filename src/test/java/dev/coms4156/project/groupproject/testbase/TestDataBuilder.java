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
}
