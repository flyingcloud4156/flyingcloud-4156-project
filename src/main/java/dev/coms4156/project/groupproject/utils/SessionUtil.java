package dev.coms4156.project.groupproject.utils;

import dev.coms4156.project.groupproject.dto.UserView;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Session helper for keeping the current user's snapshot fresh. */
public final class SessionUtil {
    private SessionUtil() {}
    public static void refreshCurrentUserSnapshot(StringRedisTemplate redis, UserView user) {
        // no-op here since we don't track active access token; a real impl could scan keys.
        // Kept for extension points.
    }
}
