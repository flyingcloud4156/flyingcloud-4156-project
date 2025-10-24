package dev.coms4156.project.groupproject.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Records access logs for all API entry points. */
@Component
@Slf4j(topic = "dev.coms4156.project.groupproject.access")
public class AccessLogInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    request.setAttribute("startTime", System.currentTimeMillis());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {

    long startTime = (Long) request.getAttribute("startTime");
    long duration = System.currentTimeMillis() - startTime;

    String user =
        CurrentUserContext.get() != null ? CurrentUserContext.get().getName() : "anonymous";

    log.info(
        "event=ACCESS method={} uri={} handler={} status={} durationMs={} user={} exception={}",
        request.getMethod(),
        request.getRequestURI(),
        handler.toString(),
        response.getStatus(),
        duration,
        user,
        ex != null ? ex.getClass().getSimpleName() : "none");
  }
}
