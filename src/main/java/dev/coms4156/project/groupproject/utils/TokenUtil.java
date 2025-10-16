package dev.coms4156.project.groupproject.utils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** Token utilities. */
public final class TokenUtil {
    private static final SecureRandom RAND = new SecureRandom();
    private TokenUtil() {}
    public static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    public static String randomToken() {
        byte[] b = new byte[32];
        RAND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
