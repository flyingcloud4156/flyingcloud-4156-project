package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import dev.coms4156.project.groupproject.utils.Jsons;
import dev.coms4156.project.groupproject.utils.TokenUtil;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link UserServiceImpl}.
 *
 * <p>Approach: Spy the service to stub MyBatis-Plus inherited methods (getOne, count, save,
 * getById). Mock Redis template and its value operations. Validate output, behavior, and state
 * transitions where applicable.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> valueOps;

  @Spy @InjectMocks private UserServiceImpl service;

  private static User newUser(long id, String email, String name, String passwordHash) {
    User u = new User();
    u.setId(id);
    u.setEmail(email);
    u.setName(name);
    u.setPasswordHash(passwordHash);
    return u;
  }

  @Test
  @DisplayName("lookupUser: typical -> returns id and name by email")
  void lookupUser_typical() {
    User found = newUser(10L, "a@test.com", "Alice", "hash");
    doReturn(found)
        .when(service)
        .getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean());

    dev.coms4156.project.groupproject.dto.UserLookupResponse resp =
        service.lookupUser("a@test.com");

    assertEquals(10L, resp.getUserId());
    assertEquals("Alice", resp.getName());
    verify(service, times(1))
        .getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean());
  }

  @Test
  @DisplayName("lookupUser: invalid email -> throws VALIDATION_FAILED")
  void lookupUser_invalidEmail() {
    assertThrows(RuntimeException.class, () -> service.lookupUser(" "));
  }

  @Test
  @DisplayName("lookupUser: user not found -> throws USER_NOT_FOUND")
  void lookupUser_notFound() {
    doReturn(null)
        .when(service)
        .getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean());
    assertThrows(RuntimeException.class, () -> service.lookupUser("nobody@test.com"));
  }

  @Test
  @DisplayName("register: typical -> saves new user when email unique")
  void register_typical() {
    dev.coms4156.project.groupproject.dto.RegisterRequest req =
        new dev.coms4156.project.groupproject.dto.RegisterRequest();
    req.setEmail("a@test.com");
    req.setPassword("P@ssw0rd!");
    req.setName("Alice");

    doReturn(0L).when(service).count(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    doReturn(true).when(service).save(any(User.class));

    assertDoesNotThrow(() -> service.register(req));
    verify(service, times(1)).count(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    verify(service, times(1)).save(any(User.class));
  }

  @Test
  @DisplayName("register: duplicate email -> throws")
  void register_duplicateEmail() {
    dev.coms4156.project.groupproject.dto.RegisterRequest req =
        new dev.coms4156.project.groupproject.dto.RegisterRequest();
    req.setEmail("a@test.com");
    req.setPassword("P@ssw0rd!");

    doReturn(1L).when(service).count(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    assertThrows(RuntimeException.class, () -> service.register(req));
  }

  @Test
  @DisplayName("login: typical -> verifies password and issues tokens (redis writes)")
  void login_typical() {
    // Stub DB lookup
    User user = newUser(7L, "a@test.com", "Alice", TokenUtil.randomToken());
    // PasswordUtil.verifyPassword requires matching hash; stub to true by stubbing behavior via
    // returning the same password string as hash and using verifyPassword on it is not trivial.
    // Instead, stub getOne to return a user and stub verify by returning true using a partial
    // approach: we cannot stub static method PasswordUtil easily — but implementation uses
    // PasswordUtil.verifyPassword(req.getPassword(), user.getPasswordHash()). We can set
    // passwordHash equal to req password hash using PasswordUtil.hashPassword here.
    dev.coms4156.project.groupproject.dto.LoginRequest req =
        new dev.coms4156.project.groupproject.dto.LoginRequest();
    req.setEmail("a@test.com");
    req.setPassword("secret");

    user.setPasswordHash(
        dev.coms4156.project.groupproject.utils.PasswordUtil.hashPassword("secret"));
    doReturn(user)
        .when(service)
        .getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean());

    // Stub Redis writes
    doReturn(valueOps).when(redis).opsForValue();
    doAnswer(inv -> null).when(valueOps).set(anyString(), anyString(), any(Duration.class));

    dev.coms4156.project.groupproject.dto.TokenPair pair = service.login(req);

    assertNotNull(pair.getAccessToken());
    assertNotNull(pair.getRefreshToken());
    verify(redis, times(2)).opsForValue();
  }

  @Test
  @DisplayName("login: user not found -> throws")
  void login_userNotFound() {
    dev.coms4156.project.groupproject.dto.LoginRequest req =
        new dev.coms4156.project.groupproject.dto.LoginRequest();
    req.setEmail("none@test.com");
    req.setPassword("x");
    doReturn(null)
        .when(service)
        .getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean());
    assertThrows(RuntimeException.class, () -> service.login(req));
  }

  @Test
  @DisplayName("login: wrong password -> throws")
  void login_wrongPassword() {
    dev.coms4156.project.groupproject.dto.LoginRequest req =
        new dev.coms4156.project.groupproject.dto.LoginRequest();
    req.setEmail("a@test.com");
    req.setPassword("bad");
    User user =
        newUser(
            1L,
            "a@test.com",
            "A",
            dev.coms4156.project.groupproject.utils.PasswordUtil.hashPassword("good"));
    doReturn(user)
        .when(service)
        .getOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class), anyBoolean());
    assertThrows(RuntimeException.class, () -> service.login(req));
  }

  @Test
  @DisplayName("refresh: typical -> reads redis, rotates token, reissues tokens")
  void refresh_typical() {
    UserView uv = new UserView(3L, "Bob");
    String refreshToken = "r1";

    doReturn(valueOps).when(redis).opsForValue();
    doReturn(Jsons.toJson(uv)).when(valueOps).get(anyString());

    doReturn(true).when(redis).delete(anyString());
    doReturn(newUser(3L, "b@test.com", "Bob", "h")).when(service).getById(3L);

    doAnswer(inv -> null).when(valueOps).set(anyString(), anyString(), any(Duration.class));

    dev.coms4156.project.groupproject.dto.TokenPair pair = service.refresh(refreshToken);

    assertNotNull(pair.getAccessToken());
    assertNotNull(pair.getRefreshToken());
    verify(redis, times(1)).delete(anyString());
  }

  @Test
  @DisplayName("refresh: missing in redis -> throws")
  void refresh_missing() {
    doReturn(valueOps).when(redis).opsForValue();
    doReturn(null).when(valueOps).get(anyString());
    assertThrows(RuntimeException.class, () -> service.refresh("r1"));
  }

  //   @Test
  //   @DisplayName("logout: blank token -> no-op and clears context")
  //   void logout_blank() {
  //     CurrentUserContext.set(new UserView(1L, "A"));
  //     service.logout(" ");
  //     // no redis interaction expected; just ensure no throw and context cleared via method
  //     assertNull(CurrentUserContext.get());
  //   }

  @Test
  @DisplayName("logout: delete refresh key and clear context")
  void logout_typical() {
    doReturn(true).when(redis).delete(anyString());
    CurrentUserContext.set(new UserView(2L, "B"));
    service.logout("refresh-1");
    verify(redis, times(1)).delete(anyString());
    assertNull(CurrentUserContext.get());
  }

  @Test
  @DisplayName("currentUser: present -> returns view")
  void currentUser_present() {
    CurrentUserContext.set(new UserView(9L, "Z"));
    UserView uv = service.currentUser();
    assertEquals(9L, uv.getId());
    assertEquals("Z", uv.getName());
    CurrentUserContext.clear();
  }

  @Test
  @DisplayName("currentUser: absent -> throws Not logged in")
  void currentUser_absent() {
    CurrentUserContext.clear();
    assertThrows(RuntimeException.class, () -> service.currentUser());
  }

  @Test
  @DisplayName("getProfile: found -> returns view")
  void getProfile_found() {
    doReturn(newUser(4L, "d@test.com", "Dan", "h")).when(service).getById(4L);
    UserView uv = service.getProfile(4L);
    assertEquals(4L, uv.getId());
    assertEquals("Dan", uv.getName());
  }

  @Test
  @DisplayName("getProfile: not found -> returns null")
  void getProfile_notFound() {
    doReturn(null).when(service).getById(5L);
    assertNull(service.getProfile(5L));
  }
}
