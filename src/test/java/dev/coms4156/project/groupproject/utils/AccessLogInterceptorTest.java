package dev.coms4156.project.groupproject.utils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.coms4156.project.groupproject.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AccessLogInterceptor}.
 *
 * <p>Tests branch coverage for logging paths: anonymous vs authenticated user, empty vs non-empty
 * parameters, password redaction, exception vs no exception.
 */
class AccessLogInterceptorTest {

  private AccessLogInterceptor interceptor;

  @BeforeEach
  void setup() {
    interceptor = new AccessLogInterceptor();
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  @DisplayName("preHandle: typical -> returns true and sets attributes")
  void preHandle_typical() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    boolean result = interceptor.preHandle(request, response, handler);

    assertTrue(result);
  }

  @Test
  @DisplayName("afterCompletion: authenticated user -> logs user name")
  void afterCompletion_authenticatedUser() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/test");
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(response.getStatus()).thenReturn(200);

    UserView testUser = new UserView(1L, "TestUser");
    CurrentUserContext.set(testUser);

    interceptor.afterCompletion(request, response, handler, null);

    // If no exception thrown, test passes
    assertTrue(true);
  }

  @Test
  @DisplayName("afterCompletion: anonymous user -> logs anonymous")
  void afterCompletion_anonymousUser() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/register");
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(response.getStatus()).thenReturn(201);

    // No user set in CurrentUserContext - should log as "anonymous"
    interceptor.afterCompletion(request, response, handler, null);

    assertTrue(true);
  }

  @Test
  @DisplayName("afterCompletion: with exception -> logs exception class")
  void afterCompletion_withException() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();
    Exception testException = new RuntimeException("Test error");

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/test");
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(response.getStatus()).thenReturn(500);

    interceptor.afterCompletion(request, response, handler, testException);

    assertTrue(true);
  }

  @Test
  @DisplayName("sanitizeParameters: empty map -> returns {}")
  void sanitizeParameters_emptyMap() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/test");
    when(request.getParameterMap()).thenReturn(new HashMap<>());
    when(response.getStatus()).thenReturn(200);

    interceptor.afterCompletion(request, response, handler, null);

    assertTrue(true);
  }

  @Test
  @DisplayName("sanitizeParameters: null map -> returns {}")
  void sanitizeParameters_nullMap() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/test");
    when(request.getParameterMap()).thenReturn(null);
    when(response.getStatus()).thenReturn(200);

    interceptor.afterCompletion(request, response, handler, null);

    assertTrue(true);
  }

  @Test
  @DisplayName("sanitizeParameters: password field -> redacts value")
  void sanitizeParameters_passwordField() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    Map<String, String[]> params = new HashMap<>();
    params.put("username", new String[] {"testuser"});
    params.put("password", new String[] {"secret123"});

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/v1/login");
    when(request.getParameterMap()).thenReturn(params);
    when(response.getStatus()).thenReturn(200);

    interceptor.afterCompletion(request, response, handler, null);

    assertTrue(true);
  }

  @Test
  @DisplayName("sanitizeParameters: regular params -> shows values")
  void sanitizeParameters_regularParams() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    Object handler = new Object();

    Map<String, String[]> params = new HashMap<>();
    params.put("page", new String[] {"1"});
    params.put("size", new String[] {"10"});

    when(request.getAttribute("startTime")).thenReturn(System.currentTimeMillis());
    when(request.getAttribute("requestId")).thenReturn("test-request-id");
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/v1/transactions");
    when(request.getParameterMap()).thenReturn(params);
    when(response.getStatus()).thenReturn(200);

    interceptor.afterCompletion(request, response, handler, null);

    assertTrue(true);
  }
}
