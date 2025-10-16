package dev.coms4156.project.groupproject.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.UserView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Reads access token from header and populates thread-local user if present. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuthInterceptor(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(RedisKeys.HEADER_TOKEN);
        if (StringUtils.hasText(token)) {
            String json = redis.opsForValue().get(RedisKeys.accessTokenKey(token));
            if (json != null) {
                UserView uv = mapper.readValue(json, UserView.class);
                CurrentUserContext.set(uv);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}
