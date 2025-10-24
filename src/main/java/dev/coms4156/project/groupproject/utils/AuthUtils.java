package dev.coms4156.project.groupproject.utils;

import dev.coms4156.project.groupproject.entity.LedgerMember;
import java.util.Arrays;
import java.util.List;

/** Utility class for authorization checks. */
public class AuthUtils {

  /**
   * Checks if a member has one of the required roles.
   *
   * @param member the ledger member.
   * @param roles the required roles.
   */
  public static void checkRole(LedgerMember member, String... roles) {
    if (member == null) {
      throw new RuntimeException("FORBIDDEN: You are not a member of this ledger");
    }

    List<String> requiredRoles = Arrays.asList(roles);
    if (!requiredRoles.contains(member.getRole())) {
      throw new RuntimeException("ROLE_INSUFFICIENT: You do not have the required role");
    }
  }

  /**
   * Checks if a user is a member of a ledger.
   *
   * @param isMember true if the user is a member, false otherwise.
   */
  public static void checkMembership(boolean isMember) {
    if (!isMember) {
      throw new RuntimeException("FORBIDDEN: You are not a member of this ledger");
    }
  }
}
