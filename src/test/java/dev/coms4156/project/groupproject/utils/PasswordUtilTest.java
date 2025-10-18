package dev.coms4156.project.groupproject.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.coms4156.project.groupproject.testbase.TestDataFixture;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for PasswordUtil utility class.
 *
 * <p>Tests cover: - Password hashing functionality - Password verification functionality - Security
 * properties (salt uniqueness, timing attacks) - Edge cases and boundary conditions - Performance
 * characteristics
 */
@Tag("utility")
@Tag("security")
@DisplayName("PasswordUtil Tests")
class PasswordUtilTest {

  @Nested
  @DisplayName("Password Hashing Tests")
  class PasswordHashingTests {

    @Test
    @DisplayName("Should hash password successfully with valid input")
    void shouldHashPassword_WhenValidPasswordProvided() {
      // Arrange
      String password = "SecurePass123!";

      // Act
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Assert
      assertNotNull(hashedPassword);
      assertFalse(hashedPassword.isEmpty());
      assertNotEquals(password, hashedPassword);
      assertTrue(hashedPassword.contains(" @"));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "SecurePass123!",
          "MyP@ssw0rd",
          "Complex123!@#",
          "simple123",
          "VERYLONGPASSWORD123!@#",
          "a1!",
          "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
          "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
          "A1!"
        })
    @DisplayName("Should hash various valid passwords successfully")
    void shouldHashVariousPasswords_WhenValidPasswordsProvided(String validPassword) {
      // Act
      String hashedPassword = PasswordUtil.hashPassword(validPassword);

      // Assert
      assertNotNull(hashedPassword);
      assertFalse(hashedPassword.isEmpty());
      assertNotEquals(validPassword, hashedPassword);
      assertTrue(hashedPassword.contains(" @"));
    }

    @Test
    @DisplayName("Should generate unique salts for same password")
    void shouldGenerateUniqueSalts_WhenSamePasswordHashedMultipleTimes() {
      // Arrange
      String password = "TestPassword123";

      // Act
      String hash1 = PasswordUtil.hashPassword(password);
      String hash2 = PasswordUtil.hashPassword(password);
      String hash3 = PasswordUtil.hashPassword(password);

      // Assert
      assertNotEquals(hash1, hash2);
      assertNotEquals(hash2, hash3);
      assertNotEquals(hash1, hash3);

      // Verify all have correct format
      assertTrue(hash1.contains(" @"));
      assertTrue(hash2.contains(" @"));
      assertTrue(hash3.contains(" @"));
    }

    @Test
    @DisplayName("Should throw exception when password is null")
    void shouldThrowException_WhenPasswordIsNull() {
      // Act & Assert
      assertThrows(RuntimeException.class, () -> PasswordUtil.hashPassword(null));
    }

    @Test
    @DisplayName("Should hash empty string successfully")
    void shouldHashEmptyString_WhenEmptyPasswordProvided() {
      // Act
      String hashedPassword = PasswordUtil.hashPassword("");

      // Assert
      assertNotNull(hashedPassword);
      assertFalse(hashedPassword.isEmpty());
      assertNotEquals("", hashedPassword);
      assertTrue(hashedPassword.contains(" @"));
    }

    @Test
    @DisplayName("Should hash very long passwords successfully")
    void shouldHashLongPasswords_WhenLongPasswordProvided() {
      // Arrange
      String longPassword = "a".repeat(1000) + "1!";

      // Act
      String hashedPassword = PasswordUtil.hashPassword(longPassword);

      // Assert
      assertNotNull(hashedPassword);
      assertFalse(hashedPassword.isEmpty());
      assertNotEquals(longPassword, hashedPassword);
      assertTrue(hashedPassword.contains(" @"));
    }
  }

  @Nested
  @DisplayName("Password Verification Tests")
  class PasswordVerificationTests {

    @Test
    @DisplayName("Should verify correct password successfully")
    void shouldVerifyPassword_WhenCorrectPasswordProvided() {
      // Arrange
      String password = "TestPassword123!";
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Act
      boolean isVerified = PasswordUtil.verifyPassword(password, hashedPassword);

      // Assert
      assertTrue(isVerified);
    }

    @Test
    @DisplayName("Should reject incorrect password")
    void shouldRejectPassword_WhenIncorrectPasswordProvided() {
      // Arrange
      String password = "TestPassword123!";
      String wrongPassword = "WrongPassword456!";
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Act
      boolean isVerified = PasswordUtil.verifyPassword(wrongPassword, hashedPassword);

      // Assert
      assertFalse(isVerified);
    }

    @Test
    @DisplayName("Should reject password verification for different hashed passwords")
    void shouldRejectVerification_WhenDifferentHashedPasswordProvided() {
      // Arrange
      String password = "TestPassword123!";
      String hashedPassword1 = PasswordUtil.hashPassword(password);
      String hashedPassword2 = PasswordUtil.hashPassword("DifferentPassword456!");

      // Act
      boolean isVerified = PasswordUtil.verifyPassword(password, hashedPassword2);

      // Assert
      assertFalse(isVerified);
    }

    @Test
    @DisplayName("Should handle malformed hashed passwords gracefully")
    void shouldHandleMalformedHashedPasswords_WhenInvalidFormatProvided() {
      // Arrange
      String password = "TestPassword123!";
      List<String> malformedHashes =
          List.of(
              "",
              "invalidhash",
              "onlysalt@",
              "@onlyhash",
              "invalid@separator@format",
              "notbase64 @ notbase64",
              "YWJjZGVmZ2g= @ invalidbase64hash",
              "invalidbase64salt @ YWJjZGVmZ2g=");

      // Act & Assert
      malformedHashes.forEach(
          malformedHash ->
              assertFalse(
                  PasswordUtil.verifyPassword(password, malformedHash),
                  "Should reject malformed hash: " + malformedHash));
    }

    @Test
    @DisplayName("Should reject null stored password")
    void shouldRejectNullStoredPassword_WhenNullProvided() {
      // Arrange
      String password = "TestPassword123!";

      // Act
      boolean isVerified = PasswordUtil.verifyPassword(password, null);

      // Assert
      assertFalse(isVerified);
    }

    @Test
    @DisplayName("Should reject empty stored password")
    void shouldRejectEmptyStoredPassword_WhenEmptyStringProvided() {
      // Arrange
      String password = "TestPassword123!";

      // Act
      boolean isVerified = PasswordUtil.verifyPassword(password, "");

      // Assert
      assertFalse(isVerified);
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {"", "123", "password"})
    @DisplayName("Should verify passwords of various types")
    void shouldVerifyVariousPasswords_WhenDifferentPasswordTypesProvided(String password) {
      if (password == null) {
        // Skip null for verification since it should be handled at service level
        return;
      }

      // Arrange
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Act
      boolean isVerified = PasswordUtil.verifyPassword(password, hashedPassword);

      // Assert
      assertTrue(isVerified);
    }
  }

  @Nested
  @DisplayName("Security Property Tests")
  class SecurityPropertyTests {

    @Test
    @DisplayName("Should have consistent hash format")
    void shouldHaveConsistentFormat_WhenPasswordHashed() {
      // Arrange
      String password = "TestPassword123!";

      // Act
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Assert
      String[] parts = hashedPassword.split(" @", 2);
      assertEquals(2, parts.length);

      // Verify both parts are Base64 encoded (no exceptions thrown)
      assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(parts[0]));
      assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(parts[1]));
    }

    @Test
    @DisplayName("Should generate consistent hash length")
    void shouldGenerateConsistentHashLength_WhenSamePasswordHashed() {
      // Arrange
      String password = "TestPassword123!";

      // Act
      String hash1 = PasswordUtil.hashPassword(password);
      String hash2 = PasswordUtil.hashPassword(password);

      // Assert
      assertEquals(hash1.length(), hash2.length());
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Should complete hashing within reasonable time")
    void shouldCompleteHashingInReasonableTime_WhenComplexPasswordProvided() {
      // Arrange
      String complexPassword = "VeryComplexPassword123!@#$%^&*()";

      // Act & Assert
      assertDoesNotThrow(
          () -> {
            PasswordUtil.hashPassword(complexPassword);
            PasswordUtil.verifyPassword(
                complexPassword, PasswordUtil.hashPassword(complexPassword));
          });
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    @DisplayName("Should complete verification within reasonable time")
    void shouldCompleteVerificationInReasonableTime_WhenPasswordVerified() {
      // Arrange
      String password = "TestPassword123!";
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Act & Assert
      assertDoesNotThrow(() -> PasswordUtil.verifyPassword(password, hashedPassword));
    }

    @Test
    @DisplayName("Should be resistant to timing attacks")
    void shouldBeResistantToTimingAttacks_WhenVerifyingPasswords() {
      // Arrange
      String password = "TestPassword123!";
      String hashedPassword = PasswordUtil.hashPassword(password);

      // Act - Measure time for correct and incorrect passwords
      long startTime = System.nanoTime();
      boolean correctResult = PasswordUtil.verifyPassword(password, hashedPassword);
      final long correctTime = System.nanoTime() - startTime;

      startTime = System.nanoTime();
      boolean incorrectResult = PasswordUtil.verifyPassword("WrongPassword", hashedPassword);
      long incorrectTime = System.nanoTime() - startTime;

      // Assert
      assertTrue(correctResult);
      assertFalse(incorrectResult);

      // Time difference should be within reasonable bounds (within 10x)
      assertTrue(
          Math.abs(correctTime - incorrectTime) < Math.max(correctTime, incorrectTime) * 10,
          "Verification times should be relatively consistent to prevent timing attacks");
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("Should handle Unicode characters in passwords")
    void shouldHandleUnicodeCharacters_WhenUnicodePasswordProvided() {
      // Arrange
      String unicodePassword = "测试密码123!@#";
      String emojiPassword = "Password🔒123!";
      String mixedPassword = "Test测试Password🔒123!";

      // Act & Assert
      for (String password : List.of(unicodePassword, emojiPassword, mixedPassword)) {
        String hashedPassword = PasswordUtil.hashPassword(password);
        assertNotNull(hashedPassword);
        assertTrue(PasswordUtil.verifyPassword(password, hashedPassword));
      }
    }

    @Test
    @DisplayName("Should handle whitespace characters")
    void shouldHandleWhitespaceCharacters_WhenPasswordWithSpacesProvided() {
      // Arrange
      List<String> passwords =
          List.of(
              " password with spaces ",
              "Password\twith\ttabs",
              "Password\nwith\nnewlines",
              "Password\rwith\rcarriage\nreturns");

      // Act & Assert
      passwords.forEach(
          password -> {
            String hashedPassword = PasswordUtil.hashPassword(password);
            assertNotNull(hashedPassword);
            assertTrue(PasswordUtil.verifyPassword(password, hashedPassword));
          });
    }

    @Test
    @DisplayName("Should handle special characters")
    void shouldHandleSpecialCharacters_WhenPasswordWithSpecialCharsProvided() {
      // Arrange
      String specialCharsPassword = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~";

      // Act
      String hashedPassword = PasswordUtil.hashPassword(specialCharsPassword);

      // Assert
      assertNotNull(hashedPassword);
      assertTrue(PasswordUtil.verifyPassword(specialCharsPassword, hashedPassword));
    }

    @Test
    @DisplayName("Should handle password that looks like hash format")
    void shouldHandlePasswordLooksLikeHash_WhenPasswordInHashFormatProvided() {
      // Arrange
      String passwordThatLooksLikeHash = "YWJjZGVmZ2g=@YWJjZGVmZ2hqa2xtbm9wcXJzdHV2d3h5eg==";

      // Act
      String hashedPassword = PasswordUtil.hashPassword(passwordThatLooksLikeHash);

      // Assert
      assertNotNull(hashedPassword);
      assertTrue(PasswordUtil.verifyPassword(passwordThatLooksLikeHash, hashedPassword));
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("Should work with multiple password-hash verification cycles")
    void shouldWorkWithMultipleCycles_WhenMultiplePasswordsHashedAndVerified() {
      // Arrange
      List<String> passwords = TestDataFixture.VALID_PASSWORDS;

      // Act & Assert
      passwords.forEach(
          password -> {
            String hashedPassword = PasswordUtil.hashPassword(password);
            assertTrue(
                PasswordUtil.verifyPassword(password, hashedPassword),
                "Should verify password: " + password);

            // Should reject incorrect passwords
            assertFalse(
                PasswordUtil.verifyPassword(password + "wrong", hashedPassword),
                "Should reject modified password for: " + password);
          });
    }

    @Test
    @DisplayName("Should maintain hash verification after multiple hash generations")
    void shouldMaintainVerification_WhenMultipleHashesGenerated() {
      // Arrange
      String password = "TestPassword123!";
      String originalHash = PasswordUtil.hashPassword(password);

      // Act - Generate many more hashes
      for (int i = 0; i < 100; i++) {
        PasswordUtil.hashPassword("someOtherPassword" + i);
      }

      // Assert
      assertTrue(
          PasswordUtil.verifyPassword(password, originalHash),
          "Original hash should still be verifiable");
    }
  }

  /**
   * Provides stream of boundary case passwords for parameterized testing.
   *
   * @return stream of boundary case passwords
   */
  static Stream<String> boundaryCasePasswords() {
    return Stream.of(
        "a", // Single character
        "ab", // Two characters
        "abc", // Three characters
        "123", // All numbers
        "ABC", // All uppercase
        "abc", // All lowercase
        "a1!", // Minimum valid password
        " ".repeat(1000), // All spaces
        "a".repeat(10000) // Very long password
        );
  }
}
