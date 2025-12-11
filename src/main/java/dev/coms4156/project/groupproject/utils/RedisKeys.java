package dev.coms4156.project.groupproject.utils;

/** Redis keys and TTLs for session tokens. */
public final class RedisKeys {
  private RedisKeys() {}

  public static final String HEADER_TOKEN = "X-Auth-Token"; // access token header name
  // Tokens are now long-lived for demo purposes (1 year).
  public static final long ACCESS_TOKEN_TTL_HOURS = 24 * 365; // 1 year
  public static final long REFRESH_TOKEN_TTL_DAYS = 365; // 1 year

  public static String accessTokenKey(String token) {
    return "auth:access:" + token;
  }

  public static String refreshTokenKey(String token) {
    return "auth:refresh:" + token;
  }
}
