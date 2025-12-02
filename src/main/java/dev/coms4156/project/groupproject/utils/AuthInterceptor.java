package dev.coms4156.project.groupproject.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/** Reads access token from header and populates thread-local user if present. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper = new ObjectMapper();

  public AuthInterceptor(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String token = request.getHeader(RedisKeys.HEADER_TOKEN);
    if (StringUtils.hasText(token)) {
      String json = redis.opsForValue().get(RedisKeys.accessTokenKey(token));
      if (json != null) {
        UserView uv = mapper.readValue(json, UserView.class);
        CurrentUserContext.set(uv);
        return true;
      }
    }

    // No valid token found - reject request
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response
        .getWriter()
        .write("{\"success\":false,\"message\":\"AUTH_REQUIRED\",\"data\":null,\"total\":null}");
    return false;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    CurrentUserContext.clear();
  }
}
