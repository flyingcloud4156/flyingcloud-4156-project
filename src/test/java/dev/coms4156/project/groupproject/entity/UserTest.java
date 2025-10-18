package dev.coms4156.project.groupproject.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.testbase.TestDataBuilder;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for User entity.
 *
 * <p>Tests cover: - Entity construction and validation - Business logic methods - Equals/hashCode
 * contracts - Edge cases and boundary conditions - Data integrity constraints
 */
@Tag("entity")
@Tag("user")
@DisplayName("User Entity Tests")
class UserTest {

  private User testUser;

  @BeforeEach
  void setUp() {
    testUser = TestDataBuilder.createTestUser();
  }

  @Nested
  @DisplayName("Entity Construction Tests")
  class EntityConstructionTests {

    @Test
    @DisplayName("Should create valid user with all required fields")
    void shouldCreateValidUser_WhenAllFieldsProvided() {
      // Arrange & Act - Test data created in setUp
      // Assert
      assertNotNull(testUser);
      assertEquals(1L, testUser.getId());
      assertEquals("Test User", testUser.getName());
      assertEquals("test@example.com", testUser.getEmail());
      assertEquals("+1234567890", testUser.getPhone());
      assertEquals("hashedPassword123", testUser.getPasswordHash());
      assertEquals("UTC", testUser.getTimezone());
      assertEquals("USD", testUser.getMainCurrency());
      assertNotNull(testUser.getCreatedAt());
      assertNotNull(testUser.getUpdatedAt());
    }

    @Test
    @DisplayName("Should create user with null ID for creation scenarios")
    void shouldCreateUserWithNullId_WhenForCreation() {
      // Arrange & Act
      User userForCreation = TestDataBuilder.createTestUserForCreation();

      // Assert
      assertNotNull(userForCreation);
      assertNull(userForCreation.getId());
      assertEquals("Test User", userForCreation.getName());
      assertEquals("test@example.com", userForCreation.getEmail());
    }

    @Test
    @DisplayName("Should handle user creation with minimum required fields")
    void shouldCreateUser_WhenOnlyRequiredFieldsProvided() {
      // Arrange & Act
      User minimalUser = new User();
      minimalUser.setName("Minimal User");
      minimalUser.setEmail("minimal@example.com");
      minimalUser.setPasswordHash("hash");

      // Assert
      assertNotNull(minimalUser);
      assertEquals("Minimal User", minimalUser.getName());
      assertEquals("minimal@example.com", minimalUser.getEmail());
      assertEquals("hash", minimalUser.getPasswordHash());
      assertNull(minimalUser.getId());
      assertNull(minimalUser.getPhone());
      assertNull(minimalUser.getTimezone());
      assertNull(minimalUser.getMainCurrency());
    }
  }

  @Nested
  @DisplayName("Field Validation Tests")
  class FieldValidationTests {

    @ParameterizedTest
    @ValueSource(
        strings = {"valid@example.com", "test.email+tag@domain.co.uk", "user123@test-domain.com"})
    @DisplayName("Should accept valid email formats")
    void shouldAcceptValidEmails_WhenValidFormatProvided(String validEmail) {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act
      user.setEmail(validEmail);

      // Assert
      assertEquals(validEmail, user.getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid-email", "@domain.com", "user@", "user@domain"})
    @DisplayName("Should handle invalid email formats gracefully")
    void shouldHandleInvalidEmails_WhenInvalidFormatProvided(String invalidEmail) {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act & Assert - Entity should accept any string (validation at service layer)
      assertDoesNotThrow(() -> user.setEmail(invalidEmail));
      assertEquals(invalidEmail, user.getEmail());
    }

    @Test
    @DisplayName("Should handle null email gracefully")
    void shouldHandleNullEmail_WhenNullProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act & Assert
      assertDoesNotThrow(() -> user.setEmail(null));
      assertNull(user.getEmail());
    }

    @Test
    @DisplayName("Should handle empty name gracefully")
    void shouldHandleEmptyName_WhenEmptyStringProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act
      user.setName("");

      // Assert
      assertEquals("", user.getName());
    }

    @Test
    @DisplayName("Should handle null name gracefully")
    void shouldHandleNullName_WhenNullProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act & Assert
      assertDoesNotThrow(() -> user.setName(null));
      assertNull(user.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "JPY", "CNY"})
    @DisplayName("Should accept valid currency codes")
    void shouldAcceptValidCurrencies_WhenValidCodeProvided(String validCurrency) {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act
      user.setMainCurrency(validCurrency);

      // Assert
      assertEquals(validCurrency, user.getMainCurrency());
    }

    @Test
    @DisplayName("Should handle phone number formats")
    void shouldHandlePhoneNumbers_WhenVariousFormatsProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();
      List<String> phoneNumbers =
          List.of(
              "+1234567890", "123-456-7890", "(123) 456-7890", "1234567890", "+86 138 1234 5678");

      // Act & Assert
      phoneNumbers.forEach(
          phoneNumber -> {
            assertDoesNotThrow(() -> user.setPhone(phoneNumber));
            assertEquals(phoneNumber, user.getPhone());
          });
    }
  }

  @Nested
  @DisplayName("Timestamp Tests")
  class TimestampTests {

    @Test
    @DisplayName("Should set and get creation timestamp correctly")
    void shouldSetAndGetCreatedAt_WhenTimestampProvided() {
      // Arrange
      LocalDateTime timestamp = LocalDateTime.now();
      User user = TestDataBuilder.createTestUser();

      // Act
      user.setCreatedAt(timestamp);

      // Assert
      assertEquals(timestamp, user.getCreatedAt());
    }

    @Test
    @DisplayName("Should set and get update timestamp correctly")
    void shouldSetAndGetUpdatedAt_WhenTimestampProvided() {
      // Arrange
      LocalDateTime timestamp = LocalDateTime.now();
      User user = TestDataBuilder.createTestUser();

      // Act
      user.setUpdatedAt(timestamp);

      // Assert
      assertEquals(timestamp, user.getUpdatedAt());
    }

    @Test
    @DisplayName("Should handle null timestamps gracefully")
    void shouldHandleNullTimestamps_WhenNullProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();

      // Act & Assert
      assertDoesNotThrow(
          () -> {
            user.setCreatedAt(null);
            user.setUpdatedAt(null);
          });

      assertNull(user.getCreatedAt());
      assertNull(user.getUpdatedAt());
    }
  }

  @Nested
  @DisplayName("Equals and HashCode Tests")
  class EqualsAndHashCodeTests {

    @Test
    @DisplayName("Should be equal when IDs are the same")
    void shouldBeEqual_WhenIdsAreSame() {
      // Arrange
      User user1 = TestDataBuilder.createTestUser();
      User user2 = TestDataBuilder.createTestUser();
      user2.setId(user1.getId());
      // Set same timestamps to ensure equality
      user2.setCreatedAt(user1.getCreatedAt());
      user2.setUpdatedAt(user1.getUpdatedAt());

      // Act & Assert
      assertEquals(user1, user2);
      assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    @DisplayName("Should not be equal when IDs are different")
    void shouldNotBeEqual_WhenIdsAreDifferent() {
      // Arrange
      User user1 = TestDataBuilder.createTestUser();
      User user2 = TestDataBuilder.createTestUser();
      user2.setId(2L);

      // Act & Assert
      assertNotEquals(user1, user2);
      // Note: hashCode might still be equal due to hash collisions, but that's acceptable
    }

    @Test
    @DisplayName("Should not be equal when comparing to null")
    void shouldNotBeEqual_WhenComparingToNull() {
      // Act & Assert
      assertNotEquals(testUser, null);
    }

    @Test
    @DisplayName("Should not be equal when comparing to different class")
    void shouldNotBeEqual_WhenComparingToDifferentClass() {
      // Act & Assert
      assertNotEquals(testUser, "some string");
      assertNotEquals(testUser, 123);
    }

    @Test
    @DisplayName("Should be equal when same instance")
    void shouldBeEqual_WhenSameInstance() {
      // Act & Assert
      assertEquals(testUser, testUser);
      assertEquals(testUser.hashCode(), testUser.hashCode());
    }
  }

  @Nested
  @DisplayName("ToString Tests")
  class ToStringTests {

    @Test
    @DisplayName("Should contain relevant fields in toString")
    void shouldContainRelevantFields_InToString() {
      // Act
      String userString = testUser.toString();

      // Assert
      assertNotNull(userString);
      assertTrue(userString.contains("User"));
      assertTrue(userString.contains("id=1"));
      assertTrue(userString.contains("name=Test User"));
      assertTrue(userString.contains("email=test@example.com"));
    }

    @Test
    @DisplayName("Should contain user information in toString")
    void shouldContainUserInfo_InToString() {
      // Act
      String userString = testUser.toString();

      // Assert
      assertNotNull(userString);
      assertTrue(userString.contains("User"));
      assertTrue(userString.contains("id=1"));
      assertTrue(userString.contains("name=Test User"));
      assertTrue(userString.contains("email=test@example.com"));
      // Note: Lombok @Data includes all fields including passwordHash in toString
      // This test verifies the basic structure is present
    }
  }

  @Nested
  @DisplayName("Boundary Condition Tests")
  class BoundaryConditionTests {

    @Test
    @DisplayName("Should handle maximum string lengths")
    void shouldHandleMaximumStringLengths_WhenMaxLengthProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();
      String maxString = "a".repeat(255);

      // Act & Assert
      assertDoesNotThrow(
          () -> {
            user.setName(maxString);
            user.setEmail(maxString + "@example.com");
            user.setPhone(maxString);
            user.setTimezone(maxString);
            user.setMainCurrency(maxString);
          });

      assertEquals(maxString, user.getName());
      assertEquals(maxString + "@example.com", user.getEmail());
      assertEquals(maxString, user.getPhone());
      assertEquals(maxString, user.getTimezone());
      assertEquals(maxString, user.getMainCurrency());
    }

    @Test
    @DisplayName("Should handle very large ID values")
    void shouldHandleLargeIdValues_WhenMaxLongProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();
      Long maxId = Long.MAX_VALUE;

      // Act
      user.setId(maxId);

      // Assert
      assertEquals(maxId, user.getId());
    }

    @Test
    @DisplayName("Should handle minimum ID values")
    void shouldHandleMinIdValues_WhenMinLongProvided() {
      // Arrange
      User user = TestDataBuilder.createTestUser();
      Long minId = Long.MIN_VALUE;

      // Act
      user.setId(minId);

      // Assert
      assertEquals(minId, user.getId());
    }
  }
}
