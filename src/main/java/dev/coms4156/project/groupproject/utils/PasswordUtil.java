package dev.coms4156.project.groupproject.utils;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Password hashing via PBKDF2WithHmacSHA256. */
public final class PasswordUtil {
  private static final int SALT_BYTES = 16;
  private static final int ITER = 120_000;
  private static final int KEY_LEN = 256;

  private PasswordUtil() {}

  public static String hashPassword(String raw) {
    try {
      byte[] salt = new byte[SALT_BYTES];
      new SecureRandom().nextBytes(salt);
      byte[] hashed = pbkdf2(raw.toCharArray(), salt, ITER, KEY_LEN);
      return Base64.getEncoder().encodeToString(salt)
          + "@"
          + Base64.getEncoder().encodeToString(hashed);
    } catch (Exception e) {
      throw new RuntimeException("Hash error", e);
    }
  }

  public static boolean verifyPassword(String raw, String stored) {
    try {
      String[] parts = stored.split("@", 2);
      if (parts.length != 2) return false;
      byte[] salt = Base64.getDecoder().decode(parts[0]);
      byte[] expect = Base64.getDecoder().decode(parts[1]);
      byte[] actual = pbkdf2(raw.toCharArray(), salt, ITER, KEY_LEN);
      if (actual.length != expect.length) return false;
      int diff = 0;
      for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expect[i];
      return diff == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private static byte[] pbkdf2(char[] pwd, byte[] salt, int iter, int keyLen) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(pwd, salt, iter, keyLen);
    SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    return skf.generateSecret(spec).getEncoded();
  }
}
