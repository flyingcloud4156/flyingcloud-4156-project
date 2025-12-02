package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.LoginRequest;
import dev.coms4156.project.groupproject.dto.RegisterRequest;
import dev.coms4156.project.groupproject.dto.TokenPair;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for UserService with database.
 *
 * <p>Tests the integration: UserService → UserMapper → Database → PasswordEncoder
 *
 * <p>Verifies: - register() inserts user to database - Password is hashed correctly - login()
 * queries database and validates credentials - Tokens are generated
 */
@SpringBootTest
@Transactional
class UserDatabaseIntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserMapper userMapper;

  @Test
  void testRegister_insertsUserToDatabase() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("user_int_test@example.com");
    request.setName("User Int Test");
    request.setPassword("TestPass123");

    userService.register(request);

    User savedUser =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, "user_int_test@example.com"));

    assertNotNull(savedUser);
    assertEquals("User Int Test", savedUser.getName());
    assertEquals("user_int_test@example.com", savedUser.getEmail());
  }

  @Test
  void testRegister_passwordIsHashed() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("hash_test@example.com");
    request.setName("Hash Test User");
    request.setPassword("PlainPassword123");

    userService.register(request);

    User savedUser =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, "hash_test@example.com"));

    assertNotNull(savedUser);
    assertNotNull(savedUser.getPasswordHash());
    assertNotEquals("PlainPassword123", savedUser.getPasswordHash());
    assertTrue(savedUser.getPasswordHash().length() > 20);
  }

  @Test
  void testLogin_queriesDatabaseAndValidatesPassword() {
    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setEmail("login_test@example.com");
    registerRequest.setName("Login Test User");
    registerRequest.setPassword("LoginPass123");
    userService.register(registerRequest);

    LoginRequest loginRequest = new LoginRequest();
    loginRequest.setEmail("login_test@example.com");
    loginRequest.setPassword("LoginPass123");

    TokenPair response = userService.login(loginRequest);

    assertNotNull(response);
    assertNotNull(response.getAccessToken());
    assertNotNull(response.getRefreshToken());
  }

  @Test
  void testGetUserById_queriesDatabase() {
    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setEmail("getuser_test@example.com");
    registerRequest.setName("Get User Test");
    registerRequest.setPassword("Pass123");
    userService.register(registerRequest);

    User savedUser =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, "getuser_test@example.com"));

    User retrievedUser = userMapper.selectById(savedUser.getId());

    assertNotNull(retrievedUser);
    assertEquals(savedUser.getId(), retrievedUser.getId());
    assertEquals("Get User Test", retrievedUser.getName());
  }

  @Test
  void verifyUserData_persistedCorrectly() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("persist_test@example.com");
    request.setName("Persistence Test");
    request.setPassword("Test123");

    userService.register(request);

    User user =
        userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, "persist_test@example.com"));

    assertNotNull(user.getId());
    assertNotNull(user.getCreatedAt());
    assertNotNull(user.getUpdatedAt());
    assertEquals("persist_test@example.com", user.getEmail());
    assertEquals("USD", user.getMainCurrency());
    assertEquals("America/New_York", user.getTimezone());
  }
}
