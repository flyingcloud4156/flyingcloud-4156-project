package dev.coms4156.project.groupproject.utils;

import dev.coms4156.project.groupproject.entity.LedgerMember;

import java.util.Arrays;
import java.util.List;

public class AuthUtils {

    public static void A(LedgerMember member, String... roles) {
        if (member == null) {
            throw new RuntimeException("FORBIDDEN: You are not a member of this ledger");
        }

        List<String> requiredRoles = Arrays.asList(roles);
        if (!requiredRoles.contains(member.getRole())) {
            throw new RuntimeException("ROLE_INSUFFICIENT: You do not have the required role");
        }
    }

    public static void B(boolean isMember) {
        if (!isMember) {
            throw new RuntimeException("FORBIDDEN: You are not a member of this ledger");
        }
    }
}
