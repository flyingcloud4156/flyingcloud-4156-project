package dev.coms4156.project.groupproject.testbase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture class providing reusable test data sets.
 *
 * <p>Contains pre-configured test data collections that can be used across multiple test classes.
 * Follows the Fixture pattern for consistent test data setup.
 */
public final class TestDataFixture {

  private TestDataFixture() {
    // Utility class - prevent instantiation
  }

  /** List of valid passwords for testing password hashing/verification. */
  public static final List<String> VALID_PASSWORDS =
      List.of(
          "SecurePass123!",
          "MyP@ssw0rd",
          "Complex123!@#",
          "simple123",
          "VERYLONGPASSWORD123!@#",
          "a1!", // Minimum length edge case
          "A".repeat(100) + "1!" // Maximum length edge case
          );

  /** List of invalid passwords for testing validation. */
  public static final List<String> INVALID_PASSWORDS =
      List.of("", "123", "password", "PASSWORD", "12345678", "short");

  /** List of valid emails for testing. */
  public static final List<String> VALID_EMAILS =
      List.of(
          "user@example.com",
          "test.email+tag@domain.co.uk",
          "user123@test-domain.com",
          "first.last@company.org",
          "a@b.co");

  /** List of invalid emails for testing validation. */
  public static final List<String> INVALID_EMAILS =
      List.of(
          "",
          "invalid-email",
          "@domain.com",
          "user@",
          "user..name@domain.com",
          "user@domain",
          "user name@domain.com");

  /** List of valid currencies for testing. */
  public static final List<String> VALID_CURRENCIES =
      List.of("USD", "EUR", "GBP", "JPY", "CNY", "CAD", "AUD", "CHF", "INR", "SGD");

  /** List of invalid currencies for testing validation. */
  public static final List<String> INVALID_CURRENCIES =
      List.of("", "US", "USDD", "XYZ", "123", "usd");

  /** List of valid ledger types for testing. */
  public static final List<String> VALID_LEDGER_TYPES =
      List.of("GROUP_BALANCE", "PERSONAL", "BUSINESS");

  /** List of invalid ledger types for testing validation. */
  public static final List<String> INVALID_LEDGER_TYPES = List.of("", "INVALID", "personal", "123");

  /**
   * Creates a list of test users with different characteristics.
   *
   * @return list of User entities for comprehensive testing
   */
  public static List<dev.coms4156.project.groupproject.entity.User> createTestUsers() {
    List<dev.coms4156.project.groupproject.entity.User> users = new ArrayList<>();

    // Standard user
    dev.coms4156.project.groupproject.entity.User user1 = TestDataBuilder.createTestUser();
    user1.setId(1L);
    user1.setEmail("user1@example.com");
    user1.setName("User One");
    users.add(user1);

    // User with different timezone
    dev.coms4156.project.groupproject.entity.User user2 = TestDataBuilder.createTestUser();
    user2.setId(2L);
    user2.setEmail("user2@example.com");
    user2.setName("User Two");
    user2.setTimezone("America/New_York");
    user2.setMainCurrency("EUR");
    users.add(user2);

    // User with phone number
    dev.coms4156.project.groupproject.entity.User user3 = TestDataBuilder.createTestUser();
    user3.setId(3L);
    user3.setEmail("user3@example.com");
    user3.setName("User Three");
    user3.setPhone("+8613812345678");
    user3.setTimezone("Asia/Shanghai");
    user3.setMainCurrency("CNY");
    users.add(user3);

    return users;
  }

  /**
   * Creates a list of test ledgers with different characteristics.
   *
   * @return list of Ledger entities for comprehensive testing
   */
  public static List<dev.coms4156.project.groupproject.entity.Ledger> createTestLedgers() {
    List<dev.coms4156.project.groupproject.entity.Ledger> ledgers = new ArrayList<>();

    // Standard group balance ledger
    dev.coms4156.project.groupproject.entity.Ledger ledger1 = TestDataBuilder.createTestLedger();
    ledger1.setId(1L);
    ledger1.setName("Family Expenses");
    ledger1.setOwnerId(1L);
    ledger1.setLedgerType("GROUP_BALANCE");
    ledger1.setBaseCurrency("USD");
    ledgers.add(ledger1);

    // Personal ledger
    dev.coms4156.project.groupproject.entity.Ledger ledger2 = TestDataBuilder.createTestLedger();
    ledger2.setId(2L);
    ledger2.setName("Personal Budget");
    ledger2.setOwnerId(2L);
    ledger2.setLedgerType("PERSONAL");
    ledger2.setBaseCurrency("EUR");
    ledger2.setShareStartDate(LocalDateTime.of(2024, 1, 1, 0, 0).toLocalDate());
    ledgers.add(ledger2);

    // Business ledger
    dev.coms4156.project.groupproject.entity.Ledger ledger3 = TestDataBuilder.createTestLedger();
    ledger3.setId(3L);
    ledger3.setName("Business Expenses");
    ledger3.setOwnerId(3L);
    ledger3.setLedgerType("BUSINESS");
    ledger3.setBaseCurrency("CNY");
    ledger3.setShareStartDate(LocalDateTime.of(2023, 6, 1, 0, 0).toLocalDate());
    ledgers.add(ledger3);

    return ledgers;
  }

  /**
   * Gets a specific test user by ID.
   *
   * @param userId the ID of the user to retrieve
   * @return the User entity, or null if not found
   */
  public static dev.coms4156.project.groupproject.entity.User getTestUserById(Long userId) {
    return createTestUsers().stream()
        .filter(user -> user.getId().equals(userId))
        .findFirst()
        .orElse(null);
  }

  /**
   * Gets a specific test ledger by ID.
   *
   * @param ledgerId the ID of the ledger to retrieve
   * @return the Ledger entity, or null if not found
   */
  public static dev.coms4156.project.groupproject.entity.Ledger getTestLedgerById(Long ledgerId) {
    return createTestLedgers().stream()
        .filter(ledger -> ledger.getId().equals(ledgerId))
        .findFirst()
        .orElse(null);
  }
}
