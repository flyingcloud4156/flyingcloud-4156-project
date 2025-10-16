package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.*;
import dev.coms4156.project.groupproject.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "User APIs (password only)")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ==== Auth ====

    @PostMapping("/auth/register")
    @Operation(summary = "Register with email + password")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req);
        return Result.ok();
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Login with email + password; returns accessToken & refreshToken")
    public Result<TokenPair> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(userService.login(req));
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "Refresh an access token (rotate refresh token)")
    public Result<TokenPair> refresh(@RequestParam("refreshToken") String refreshToken) {
        return Result.ok(userService.refresh(refreshToken));
    }

    @PostMapping("/auth/logout")
    @Operation(summary = "Logout by invalidating refresh token")
    public Result<Void> logout(@RequestParam("refreshToken") String refreshToken) {
        userService.logout(refreshToken);
        return Result.ok();
    }

    // ==== Users ====

    @GetMapping("/users/me")
    @Operation(summary = "Get current user (id, name)", security = { @SecurityRequirement(name = "X-Auth-Token") })
    public Result<UserView> me() {
        return Result.ok(userService.currentUser());
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user profile by id (no timestamps)", security = { @SecurityRequirement(name = "X-Auth-Token") })
    public Result<UserView> profile(@PathVariable("id") Long id) {
        return Result.ok(userService.getProfile(id));
    }

    @PutMapping("/users/me")
    @Operation(summary = "Update current user's profile (name/timezone)", security = { @SecurityRequirement(name = "X-Auth-Token") })
    public Result<Void> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        userService.updateMe(req);
        return Result.ok();
    }

    @PostMapping("/users/me/change-password")
    @Operation(summary = "Change password (requires oldPassword + newPassword)", security = { @SecurityRequirement(name = "X-Auth-Token") })
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(req);
        return Result.ok();
    }
}
