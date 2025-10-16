package dev.coms4156.project.groupproject.utils;

import dev.coms4156.project.groupproject.dto.UserView;

/** Thread-local holder for current user. */
public final class CurrentUserContext {
    private static final ThreadLocal<UserView> LOCAL = new ThreadLocal<>();
    private CurrentUserContext() {}
    public static void set(UserView user) { LOCAL.set(user); }
    public static UserView get() { return LOCAL.get(); }
    public static void clear() { LOCAL.remove(); }
}
