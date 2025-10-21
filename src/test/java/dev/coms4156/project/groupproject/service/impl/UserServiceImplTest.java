package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.coms4156.project.groupproject.dto.ChangePasswordRequest;
import dev.coms4156.project.groupproject.dto.LoginRequest;
import dev.coms4156.project.groupproject.dto.RegisterRequest;
import dev.coms4156.project.groupproject.dto.TokenPair;
import dev.coms4156.project.groupproject.dto.UpdateProfileRequest;
import dev.coms4156.project.groupproject.dto.UserLookupResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import dev.coms4156.project.groupproject.utils.Jsons;
import dev.coms4156.project.groupproject.utils.PasswordUtil;
import dev.coms4156.project.groupproject.utils.RedisKeys;
import dev.coms4156.project.groupproject.utils.SystemConstants;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests (isolated)")
class UserServiceImplTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOps;

  // Spy + constructor injection for redis, and stub ServiceImpl methods as needed
  @Spy @InjectMocks private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    CurrentUserContext.clear();
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Nested
  @DisplayName("lookupUser")
  class LookupUserTests {

    @Test
    @DisplayName("invalid email -> VALIDATION_FAILED")
    void invalidEmail() {
      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.lookupUser(""));
      assertEquals("VALIDATION_FAILED", ex.getMessage());
      verify(userService, never()).getOne(any(), anyBoolean());
    }

    @Test
    @DisplayName("not found -> USER_NOT_FOUND")
    void notFound() {
      doReturn(null).when(userService).getOne(any(), anyBoolean());
      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> userService.lookupUser("x@a.com"));
      assertEquals("USER_NOT_FOUND", ex.getMessage());
    }

    @Test
    @DisplayName("found -> returns id + name")
    void found() {
      User u = new User();
      u.setId(9L);
      u.setName("Alice");
      doReturn(u).when(userService).getOne(any(), anyBoolean());

      UserLookupResponse resp = userService.lookupUser("alice@example.com");
      assertNotNull(resp);
      assertEquals(9L, resp.getUserId());
      assertEquals("Alice", resp.getName());
    }
  }

  @Nested
  @DisplayName("register")
  class RegisterTests {

    @Test
    @DisplayName("email exists -> throws")
    void emailExists() {
      RegisterRequest req = new RegisterRequest();
      req.setEmail("e@ex.com");
      req.setName("n");
      req.setPassword("pass123!");

      doReturn(1L).when(userService).count(any());

      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.register(req));
      assertEquals("Email already registered", ex.getMessage());
      verify(userService, never()).save(any(User.class));
    }

    @Test
    @DisplayName("success with provided name -> saved fields set")
    void successProvidedName() {
      RegisterRequest req = new RegisterRequest();
      req.setEmail("e@ex.com");
      req.setName("Nick");
      req.setPassword("P@ssw0rd!");

      doReturn(0L).when(userService).count(any());
      AtomicReference<User> savedRef = new AtomicReference<>();
      doAnswer(
              inv -> {
                User u = inv.getArgument(0);
                savedRef.set(u);
                return true;
              })
          .when(userService)
          .save(any(User.class));

      userService.register(req);

      User saved = savedRef.get();
      assertNotNull(saved);
      assertEquals("e@ex.com", saved.getEmail());
      assertEquals("Nick", saved.getName());
      assertEquals("America/New_York", saved.getTimezone());
      assertEquals("USD", saved.getMainCurrency());
      assertNotNull(saved.getPasswordHash());
      assertNotEquals(req.getPassword(), saved.getPasswordHash());
    }

    @Test
    @DisplayName("blank name -> default prefix user_*")
    void blankNameDefaulted() {
      RegisterRequest req = new RegisterRequest();
      req.setEmail("e@ex.com");
      req.setName("");
      req.setPassword("P@ssw0rd!");

      doReturn(0L).when(userService).count(any());
      AtomicReference<User> savedRef = new AtomicReference<>();
      doAnswer(
              inv -> {
                User u = inv.getArgument(0);
                savedRef.set(u);
                return true;
              })
          .when(userService)
          .save(any(User.class));

      userService.register(req);

      User saved = savedRef.get();
      assertNotNull(saved);
      assertTrue(saved.getName().startsWith(SystemConstants.DEFAULT_NICK_PREFIX));
    }
  }

  @Nested
  @DisplayName("login")
  class LoginTests {

    @Test
    @DisplayName("user not found -> throws")
    void userNotFound() {
      doReturn(null).when(userService).getOne(any(), anyBoolean());

      LoginRequest req = new LoginRequest();
      req.setEmail("x@a.com");
      req.setPassword("abc123!");

      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(req));
      assertEquals("User not found", ex.getMessage());
    }

    @Test
    @DisplayName("wrong password -> throws")
    void wrongPassword() {
      User u = new User();
      u.setId(1L);
      u.setEmail("u@a.com");
      u.setName("U");
      u.setPasswordHash(PasswordUtil.hashPassword("correct!"));
      doReturn(u).when(userService).getOne(any(), anyBoolean());

      LoginRequest req = new LoginRequest();
      req.setEmail("u@a.com");
      req.setPassword("wrong!");

      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.login(req));
      assertEquals("Wrong credentials", ex.getMessage());
    }

    @Test
    @DisplayName("success -> returns tokens and writes Redis with TTLs")
    void success() {
      User u = new User();
      u.setId(7L);
      u.setEmail("u@a.com");
      u.setName("User");
      u.setPasswordHash(PasswordUtil.hashPassword("pw!12345"));
      doReturn(u).when(userService).getOne(any(), anyBoolean());

      LoginRequest req = new LoginRequest();
      req.setEmail("u@a.com");
      req.setPassword("pw!12345");
      // Stub Redis ops
      when(redis.opsForValue()).thenReturn(valueOps);
      TokenPair pair = userService.login(req);
      assertNotNull(pair);
      assertNotNull(pair.getAccessToken());
      assertNotNull(pair.getRefreshToken());

      verify(valueOps)
          .set(
              argThat(k -> k.startsWith(RedisKeys.accessTokenKey(""))),
              anyString(),
              eq(Duration.ofHours(RedisKeys.ACCESS_TOKEN_TTL_HOURS)));
      verify(valueOps)
          .set(
              argThat(k -> k.startsWith(RedisKeys.refreshTokenKey(""))),
              anyString(),
              eq(Duration.ofDays(RedisKeys.REFRESH_TOKEN_TTL_DAYS)));
    }
  }

  @Nested
  @DisplayName("refresh")
  class RefreshTests {

    @Test
    @DisplayName("invalid refresh -> throws")
    void invalid() {
      when(redis.opsForValue()).thenReturn(valueOps);
      when(valueOps.get(anyString())).thenReturn(null);
      String token = "R";
      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.refresh(token));
      assertEquals("Invalid or expired refresh token", ex.getMessage());
    }

    @Test
    @DisplayName("user not found -> throws")
    void userNotFound() {
      when(redis.opsForValue()).thenReturn(valueOps);
      UserView uv = new UserView(99L, "X");
      when(valueOps.get(anyString())).thenReturn(Jsons.toJson(uv));
      doReturn(null).when(userService).getById(99L);

      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.refresh("rt"));
      assertEquals("User not found", ex.getMessage());
      verify(redis).delete(eq(RedisKeys.refreshTokenKey("rt")));
    }

    @Test
    @DisplayName("success -> rotates token, writes new tokens")
    void success() {
      when(redis.opsForValue()).thenReturn(valueOps);
      UserView uv = new UserView(5L, "N");
      when(valueOps.get(anyString())).thenReturn(Jsons.toJson(uv));
      User u = new User();
      u.setId(5L);
      u.setName("N");
      doReturn(u).when(userService).getById(5L);

      TokenPair pair = userService.refresh("rtok");
      assertNotNull(pair);

      verify(redis).delete(eq(RedisKeys.refreshTokenKey("rtok")));
      verify(valueOps)
          .set(
              argThat(k -> k.startsWith(RedisKeys.accessTokenKey(""))),
              anyString(),
              eq(Duration.ofHours(RedisKeys.ACCESS_TOKEN_TTL_HOURS)));
      verify(valueOps)
          .set(
              argThat(k -> k.startsWith(RedisKeys.refreshTokenKey(""))),
              anyString(),
              eq(Duration.ofDays(RedisKeys.REFRESH_TOKEN_TTL_DAYS)));
    }
  }

  @Nested
  @DisplayName("logout")
  class LogoutTests {

    @Test
    @DisplayName("blank token -> no-op (does not clear context)")
    void blankToken() {
      CurrentUserContext.set(new UserView(1L, "U"));
      userService.logout("");
      verify(redis, never()).delete(anyString());
      assertNotNull(CurrentUserContext.get());
    }

    @Test
    @DisplayName("valid token -> deletes refresh + clears context")
    void validToken() {
      CurrentUserContext.set(new UserView(1L, "U"));
      userService.logout("rt");
      verify(redis).delete(eq(RedisKeys.refreshTokenKey("rt")));
      assertNull(CurrentUserContext.get());
    }
  }

  @Nested
  @DisplayName("currentUser & getProfile")
  class CurrentAndProfileTests {

    @Test
    @DisplayName("currentUser -> throws when not logged in")
    void currentUserNotLoggedIn() {
      CurrentUserContext.clear();
      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.currentUser());
      assertEquals("Not logged in", ex.getMessage());
    }

    @Test
    @DisplayName("currentUser -> returns when logged in")
    void currentUserOk() {
      UserView uv = new UserView(1L, "A");
      CurrentUserContext.set(uv);
      UserView got = userService.currentUser();
      assertEquals(uv.getId(), got.getId());
      assertEquals(uv.getName(), got.getName());
    }

    @Test
    @DisplayName("getProfile -> null when not found")
    void getProfileNull() {
      doReturn(null).when(userService).getById(333L);
      assertNull(userService.getProfile(333L));
    }

    @Test
    @DisplayName("getProfile -> returns view")
    void getProfileOk() {
      User u = new User();
      u.setId(10L);
      u.setName("Z");
      doReturn(u).when(userService).getById(10L);
      UserView view = userService.getProfile(10L);
      assertEquals(10L, view.getId());
      assertEquals("Z", view.getName());
    }
  }

  @Nested
  @DisplayName("updateMe")
  class UpdateMeTests {

    @Test
    @DisplayName("not logged in -> throws")
    void notLoggedIn() {
      CurrentUserContext.clear();
      UpdateProfileRequest req = new UpdateProfileRequest();
      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.updateMe(req));
      assertEquals("Not logged in", ex.getMessage());
    }

    @Test
    @DisplayName("user not found -> throws")
    void userNotFound() {
      CurrentUserContext.set(new UserView(1L, "U"));
      doReturn(null).when(userService).getById(1L);
      UpdateProfileRequest req = new UpdateProfileRequest();
      RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.updateMe(req));
      assertEquals("User not found", ex.getMessage());
    }

    @Test
    @DisplayName("updates name/timezone when provided")
    void updatesFields() {
      CurrentUserContext.set(new UserView(1L, "U"));
      User u = new User();
      u.setId(1L);
      u.setName("Old");
      u.setTimezone("UTC");
      doReturn(u).when(userService).getById(1L);

      UpdateProfileRequest req = new UpdateProfileRequest();
      req.setName("NewName");
      req.setTimezone("America/New_York");

      AtomicReference<User> updatedRef = new AtomicReference<>();
      doAnswer(
              inv -> {
                updatedRef.set(inv.getArgument(0));
                return true;
              })
          .when(userService)
          .updateById(any(User.class));

      userService.updateMe(req);

      User updated = updatedRef.get();
      assertNotNull(updated);
      assertEquals("NewName", updated.getName());
      assertEquals("America/New_York", updated.getTimezone());
    }
  }

  @Nested
  @DisplayName("changePassword")
  class ChangePasswordTests {

    @Test
    @DisplayName("same old/new -> throws")
    void samePasswords() {
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setOldPassword("abc");
      req.setNewPassword("abc");
      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> userService.changePassword(req));
      assertEquals("New password must be different", ex.getMessage());
    }

    @Test
    @DisplayName("not logged in -> throws")
    void notLoggedIn() {
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setOldPassword("old");
      req.setNewPassword("new");
      CurrentUserContext.clear();
      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> userService.changePassword(req));
      assertEquals("Not logged in", ex.getMessage());
    }

    @Test
    @DisplayName("user not found -> throws")
    void userNotFound() {
      CurrentUserContext.set(new UserView(1L, "U"));
      doReturn(null).when(userService).getById(1L);
      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setOldPassword("old");
      req.setNewPassword("new");
      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> userService.changePassword(req));
      assertEquals("User not found", ex.getMessage());
    }

    @Test
    @DisplayName("wrong old password -> throws")
    void wrongOld() {
      CurrentUserContext.set(new UserView(1L, "U"));
      User u = new User();
      u.setId(1L);
      u.setPasswordHash(PasswordUtil.hashPassword("CORRECT_OLD"));
      doReturn(u).when(userService).getById(1L);

      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setOldPassword("WRONG");
      req.setNewPassword("NEW");

      RuntimeException ex =
          assertThrows(RuntimeException.class, () -> userService.changePassword(req));
      assertEquals("Wrong old password", ex.getMessage());
    }

    @Test
    @DisplayName("success -> updates stored hash and calls updateById")
    void success() {
      CurrentUserContext.set(new UserView(1L, "U"));
      User u = new User();
      u.setId(1L);
      u.setPasswordHash(PasswordUtil.hashPassword("OLD_PW"));
      doReturn(u).when(userService).getById(1L);

      ChangePasswordRequest req = new ChangePasswordRequest();
      req.setOldPassword("OLD_PW");
      req.setNewPassword("NEW_PW");

      AtomicReference<User> updatedRef = new AtomicReference<>();
      doAnswer(
              inv -> {
                updatedRef.set(inv.getArgument(0));
                return true;
              })
          .when(userService)
          .updateById(any(User.class));

      userService.changePassword(req);

      User updated = updatedRef.get();
      assertNotNull(updated);
      assertTrue(PasswordUtil.verifyPassword("NEW_PW", updated.getPasswordHash()));
      assertFalse(PasswordUtil.verifyPassword("OLD_PW", updated.getPasswordHash()));
    }
  }
}
