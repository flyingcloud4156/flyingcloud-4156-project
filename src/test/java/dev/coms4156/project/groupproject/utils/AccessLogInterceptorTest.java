package dev.coms4156.project.groupproject.utils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.coms4156.project.groupproject.dto.UserView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** Tests for AccessLogInterceptor to ensure all API entry points are correctly logged. */
class AccessLogInterceptorTest {

  private AccessLogInterceptor interceptor;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private HandlerMethod handler;
  private ListAppender<ILoggingEvent> listAppender;
  private Logger accessLogger;

  @BeforeEach
  void setUp() {
    interceptor = new AccessLogInterceptor();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();

    // Create a mock handler
    handler = mock(HandlerMethod.class);
    when(handler.toString()).thenReturn("testHandler");

    // Set up log capture for the access logger
    accessLogger = (Logger) LoggerFactory.getLogger("dev.coms4156.project.groupproject.access");
    listAppender = new ListAppender<>();
    listAppender.start();
    accessLogger.addAppender(listAppender);
  }

  @Test
  void testPreHandle_shouldSetStartTime() throws Exception {
    // Given
    request.setMethod("GET");
    request.setRequestURI("/api/v1/users/me");

    // When
    boolean result = interceptor.preHandle(request, response, handler);

    // Then
    assert result;
    assert request.getAttribute("startTime") != null;
  }

  @Test
  void testAfterCompletion_withAuthenticatedUser_shouldLogAccessWithUser() {
    // Given
    request.setMethod("GET");
    request.setRequestURI("/api/v1/users/me");
    request.setAttribute("startTime", System.currentTimeMillis() - 100);
    response.setStatus(200);

    UserView user = new UserView();
    user.setName("Test User");
    CurrentUserContext.set(user);

    // When
    interceptor.afterCompletion(request, response, handler, null);

    // Then
    assert listAppender.list.size() == 1;
    ILoggingEvent logEvent = listAppender.list.get(0);
    assert logEvent.getLevel() == Level.INFO;
    assert logEvent.getFormattedMessage().contains("event=ACCESS");
    assert logEvent.getFormattedMessage().contains("method=GET");
    assert logEvent.getFormattedMessage().contains("uri=/api/v1/users/me");
    assert logEvent.getFormattedMessage().contains("status=200");
    assert logEvent.getFormattedMessage().contains("user=Test User");
    assert logEvent.getFormattedMessage().contains("exception=none");

    CurrentUserContext.clear();
  }

  @Test
  void testAfterCompletion_withAnonymousUser_shouldLogAccessWithAnonymous() {
    // Given
    request.setMethod("POST");
    request.setRequestURI("/api/v1/auth/login");
    request.setAttribute("startTime", System.currentTimeMillis() - 50);
    response.setStatus(200);

    // When
    interceptor.afterCompletion(request, response, handler, null);

    // Then
    assert listAppender.list.size() == 1;
    ILoggingEvent logEvent = listAppender.list.get(0);
    assert logEvent.getLevel() == Level.INFO;
    assert logEvent.getFormattedMessage().contains("event=ACCESS");
    assert logEvent.getFormattedMessage().contains("method=POST");
    assert logEvent.getFormattedMessage().contains("uri=/api/v1/auth/login");
    assert logEvent.getFormattedMessage().contains("status=200");
    assert logEvent.getFormattedMessage().contains("user=anonymous");
    assert logEvent.getFormattedMessage().contains("exception=none");
  }

  @Test
  void testAfterCompletion_withException_shouldLogException() {
    // Given
    request.setMethod("PUT");
    request.setRequestURI("/api/v1/ledgers/123");
    request.setAttribute("startTime", System.currentTimeMillis() - 200);
    response.setStatus(500);
    RuntimeException exception = new RuntimeException("Test error");

    // When
    interceptor.afterCompletion(request, response, handler, exception);

    // Then
    assert listAppender.list.size() == 1;
    ILoggingEvent logEvent = listAppender.list.get(0);
    assert logEvent.getLevel() == Level.INFO;
    assert logEvent.getFormattedMessage().contains("event=ACCESS");
    assert logEvent.getFormattedMessage().contains("method=PUT");
    assert logEvent.getFormattedMessage().contains("uri=/api/v1/ledgers/123");
    assert logEvent.getFormattedMessage().contains("status=500");
    assert logEvent.getFormattedMessage().contains("exception=RuntimeException");
  }

  @Test
  void testAfterCompletion_shouldRecordDuration() {
    // Given
    request.setMethod("GET");
    request.setRequestURI("/api/v1/currencies");
    long startTime = System.currentTimeMillis() - 150;
    request.setAttribute("startTime", startTime);
    response.setStatus(200);

    // When
    interceptor.afterCompletion(request, response, handler, null);

    // Then
    assert listAppender.list.size() == 1;
    ILoggingEvent logEvent = listAppender.list.get(0);
    String message = logEvent.getFormattedMessage();
    assert message.contains("durationMs=");
    assert message.contains("event=ACCESS");
    assert message.contains("uri=/api/v1/currencies");
  }
}
