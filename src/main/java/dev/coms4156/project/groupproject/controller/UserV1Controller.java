package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.LoginRequest;
import dev.coms4156.project.groupproject.dto.RegisterRequest;
import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.dto.TokenPair;
import dev.coms4156.project.groupproject.dto.UserLookupResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller for user-related operations. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "User APIs (password only)")
public class UserV1Controller {

  private final UserService userService;

  @Autowired
  public UserV1Controller(UserService userService) {
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

  @GetMapping("/users/lookup")
  @Operation(
      summary = "Lookup user by email or phone",
      security = {@SecurityRequirement(name = "X-Auth-Token")})
  public Result<UserLookupResponse> lookupUser(@RequestParam String email) {
    return Result.ok(userService.lookupUser(email));
  }

  @GetMapping("/users/me")
  @Operation(
      summary = "Get current user (id, name)",
      security = {@SecurityRequirement(name = "X-Auth-Token")})
  public Result<UserView> me() {
    return Result.ok(userService.currentUser());
  }

  @GetMapping("/users/{id}")
  @Operation(
      summary = "Get user profile by id (no timestamps)",
      security = {@SecurityRequirement(name = "X-Auth-Token")})
  public Result<UserView> profile(@PathVariable("id") Long id) {
    return Result.ok(userService.getProfile(id));
  }
}
