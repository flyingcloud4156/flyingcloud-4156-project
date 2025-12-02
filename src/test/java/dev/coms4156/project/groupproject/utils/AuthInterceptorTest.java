package dev.coms4156.project.groupproject.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for {@link AuthInterceptor}.
 *
 * <p>Tests branch coverage for authentication paths: OPTIONS request, valid token, invalid token,
 * no token, and afterCompletion cleanup.
 */
class AuthInterceptorTest {

  private AuthInterceptor interceptor;
  private StringRedisTemplate redis;
  private ValueOperations<String, String> valueOps;

  @BeforeEach
  void setup() {
    redis = mock(StringRedisTemplate.class);
    valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);

    interceptor = new AuthInterceptor(redis);
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  @DisplayName("preHandle: OPTIONS request -> returns true without auth check")
  void preHandle_optionsRequest() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getMethod()).thenReturn("OPTIONS");

    boolean result = interceptor.preHandle(request, response, handler);

    assertTrue(result);
  }

  @Test
  @DisplayName("preHandle: valid token -> sets CurrentUserContext and returns true")
  void preHandle_validToken() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    String token = "valid-token-123";
    UserView userView = new UserView(1L, "TestUser");
    ObjectMapper mapper = new ObjectMapper();
    String userJson = mapper.writeValueAsString(userView);

    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(RedisKeys.HEADER_TOKEN)).thenReturn(token);
    when(valueOps.get(RedisKeys.accessTokenKey(token))).thenReturn(userJson);

    boolean result = interceptor.preHandle(request, response, handler);

    assertTrue(result);
    assertTrue(CurrentUserContext.get() != null);
    assertTrue(CurrentUserContext.get().getName().equals("TestUser"));
  }

  @Test
  @DisplayName("preHandle: invalid token (not in Redis) -> returns false with 401")
  void preHandle_invalidToken() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);

    String token = "invalid-token-456";

    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(RedisKeys.HEADER_TOKEN)).thenReturn(token);
    when(valueOps.get(RedisKeys.accessTokenKey(token))).thenReturn(null);
    when(response.getWriter()).thenReturn(writer);

    boolean result = interceptor.preHandle(request, response, handler);

    assertFalse(result);
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setContentType("application/json");
  }

  @Test
  @DisplayName("preHandle: no token header -> returns false with 401")
  void preHandle_noToken() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);

    when(request.getMethod()).thenReturn("GET");
    when(request.getHeader(RedisKeys.HEADER_TOKEN)).thenReturn(null);
    when(response.getWriter()).thenReturn(writer);

    boolean result = interceptor.preHandle(request, response, handler);

    assertFalse(result);
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  @DisplayName("preHandle: empty token header -> returns false with 401")
  void preHandle_emptyToken() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);

    when(request.getMethod()).thenReturn("POST");
    when(request.getHeader(RedisKeys.HEADER_TOKEN)).thenReturn("");
    when(response.getWriter()).thenReturn(writer);

    boolean result = interceptor.preHandle(request, response, handler);

    assertFalse(result);
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  @DisplayName("afterCompletion: clears CurrentUserContext")
  void afterCompletion_clearsContext() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    UserView testUser = new UserView(1L, "TestUser");
    CurrentUserContext.set(testUser);

    interceptor.afterCompletion(request, response, handler, null);

    assertTrue(CurrentUserContext.get() == null);
  }
}
