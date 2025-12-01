package dev.coms4156.project.groupproject.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Records comprehensive access logs for all API entry points, including request ID, latency,
 * parameters, and identity.
 */
@Component
@Slf4j(topic = "dev.coms4156.project.groupproject.access")
public class AccessLogInterceptor implements HandlerInterceptor {

  private static final String REQUEST_ID_KEY = "requestId";
  private static final String START_TIME_KEY = "startTime";

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    long startTime = System.currentTimeMillis();
    String requestId = UUID.randomUUID().toString();

    // Set attributes for this request
    request.setAttribute(START_TIME_KEY, startTime);
    request.setAttribute(REQUEST_ID_KEY, requestId);

    // Set MDC for logging context
    MDC.put(REQUEST_ID_KEY, requestId);

    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    try {
      long startTime = (Long) request.getAttribute(START_TIME_KEY);
      String requestId = (String) request.getAttribute(REQUEST_ID_KEY);
      long duration = System.currentTimeMillis() - startTime;

      String user =
          CurrentUserContext.get() != null ? CurrentUserContext.get().getName() : "anonymous";
      String params = sanitizeParameters(request.getParameterMap());

      log.info(
          "event=ACCESS requestId={} method={} uri={} params={} handler={} status={} "
              + "durationMs={} user={} exception={}",
          requestId,
          request.getMethod(),
          request.getRequestURI(),
          params,
          handler.toString(),
          response.getStatus(),
          duration,
          user,
          ex != null ? ex.getClass().getSimpleName() : "none");
    } finally {
      // Clear MDC to prevent memory leaks in thread-pooled environments
      MDC.clear();
    }
  }

  /**
   * Serializes the request parameter map to a string, redacting values for sensitive keys like
   * "password".
   *
   * @param parameterMap The request's parameter map.
   * @return A sanitized string representation of the parameters.
   */
  private String sanitizeParameters(Map<String, String[]> parameterMap) {
    if (parameterMap == null || parameterMap.isEmpty()) {
      return "{}";
    }
    return parameterMap.entrySet().stream()
        .map(
            entry -> {
              String key = entry.getKey();
              String[] values = entry.getValue();
              String valueStr;
              if (key.toLowerCase().contains("password")) {
                valueStr = "[REDACTED]";
              } else {
                valueStr = String.join(",", values);
              }
              return key + "=" + valueStr;
            })
        .collect(Collectors.joining(", ", "{", "}"));
  }
}
