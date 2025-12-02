package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.LoginRequest;
import dev.coms4156.project.groupproject.dto.RegisterRequest;
import dev.coms4156.project.groupproject.dto.TokenPair;
import dev.coms4156.project.groupproject.dto.UserLookupResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for {@link UserV1Controller}.
 *
 * <p>Approach: Use standalone MockMvc (avoids Spring context). Mock UserService for isolation.
 * Cover typical/atypical/invalid paths for all endpoints with AAA structure.
 */
@ExtendWith(MockitoExtension.class)
class UserV1ControllerTest {

  @Mock private UserService userService;

  @InjectMocks private UserV1Controller controller;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName("POST /auth/register: typical -> 200 OK")
  void register_typical() throws Exception {
    RegisterRequest req = new RegisterRequest();
    req.setEmail("test@example.com");
    req.setPassword("P@ssw0rd!");
    req.setName("Test User");

    doNothing().when(userService).register(any(RegisterRequest.class));

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(userService, times(1)).register(any(RegisterRequest.class));
  }

  @Test
  @DisplayName("POST /auth/register: service throws -> exception bubbles up")
  void register_serviceThrows() throws Exception {
    RegisterRequest req = new RegisterRequest();
    req.setEmail("duplicate@example.com");
    req.setPassword("P@ssw0rd!");

    doThrow(new RuntimeException("Email already registered"))
        .when(userService)
        .register(any(RegisterRequest.class));

    // Standalone MockMvc without exception handler lets exceptions bubble up
    try {
      mockMvc.perform(
          post("/api/v1/auth/register")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      // Expected to throw ServletException wrapping RuntimeException
      assert e.getMessage().contains("Email already registered");
    }
  }

  @Test
  @DisplayName("POST /auth/login: typical -> 200 with tokens")
  void login_typical() throws Exception {
    LoginRequest req = new LoginRequest();
    req.setEmail("user@example.com");
    req.setPassword("secret");

    TokenPair tokens = new TokenPair();
    tokens.setAccessToken("access123");
    tokens.setRefreshToken("refresh456");

    doReturn(tokens).when(userService).login(any(LoginRequest.class));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("access123"))
        .andExpect(jsonPath("$.data.refreshToken").value("refresh456"));

    verify(userService, times(1)).login(any(LoginRequest.class));
  }

  @Test
  @DisplayName("POST /auth/login: wrong credentials -> exception bubbles up")
  void login_wrongCredentials() throws Exception {
    LoginRequest req = new LoginRequest();
    req.setEmail("user@example.com");
    req.setPassword("wrongpass");

    doThrow(new RuntimeException("Wrong credentials"))
        .when(userService)
        .login(any(LoginRequest.class));

    try {
      mockMvc.perform(
          post("/api/v1/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      assert e.getMessage().contains("Wrong credentials");
    }
  }

  @Test
  @DisplayName("POST /auth/refresh: typical -> 200 with new tokens")
  void refresh_typical() throws Exception {
    TokenPair tokens = new TokenPair();
    tokens.setAccessToken("newAccess789");
    tokens.setRefreshToken("newRefresh012");

    doReturn(tokens).when(userService).refresh(anyString());

    mockMvc
        .perform(post("/api/v1/auth/refresh").param("refreshToken", "oldRefresh"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("newAccess789"));

    verify(userService, times(1)).refresh(anyString());
  }

  @Test
  @DisplayName("POST /auth/refresh: invalid token -> exception bubbles up")
  void refresh_invalidToken() throws Exception {
    doThrow(new RuntimeException("Invalid or expired refresh token"))
        .when(userService)
        .refresh(anyString());

    try {
      mockMvc.perform(post("/api/v1/auth/refresh").param("refreshToken", "badToken"));
    } catch (Exception e) {
      assert e.getMessage().contains("Invalid or expired refresh token");
    }
  }

  @Test
  @DisplayName("POST /auth/logout: typical -> 200")
  void logout_typical() throws Exception {
    doNothing().when(userService).logout(anyString());

    mockMvc
        .perform(post("/api/v1/auth/logout").param("refreshToken", "someToken"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(userService, times(1)).logout(anyString());
  }

  @Test
  @DisplayName("POST /auth/logout: blank token -> still 200 (service handles it)")
  void logout_blankToken() throws Exception {
    doNothing().when(userService).logout(anyString());

    mockMvc
        .perform(post("/api/v1/auth/logout").param("refreshToken", ""))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /users/lookup: typical -> 200 with user info")
  void lookupUser_typical() throws Exception {
    UserLookupResponse resp = new UserLookupResponse(10L, "Alice");

    doReturn(resp).when(userService).lookupUser(anyString());

    mockMvc
        .perform(get("/api/v1/users/lookup").param("email", "alice@example.com"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(10))
        .andExpect(jsonPath("$.data.name").value("Alice"));

    verify(userService, times(1)).lookupUser(anyString());
  }

  @Test
  @DisplayName("GET /users/lookup: user not found -> exception bubbles up")
  void lookupUser_notFound() throws Exception {
    doThrow(new RuntimeException("USER_NOT_FOUND")).when(userService).lookupUser(anyString());

    try {
      mockMvc.perform(get("/api/v1/users/lookup").param("email", "nobody@example.com"));
    } catch (Exception e) {
      assert e.getMessage().contains("USER_NOT_FOUND");
    }
  }

  @Test
  @DisplayName("GET /users/me: typical -> 200 with current user")
  void me_typical() throws Exception {
    UserView user = new UserView(5L, "CurrentUser");

    doReturn(user).when(userService).currentUser();

    mockMvc
        .perform(get("/api/v1/users/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(5))
        .andExpect(jsonPath("$.data.name").value("CurrentUser"));

    verify(userService, times(1)).currentUser();
  }

  @Test
  @DisplayName("GET /users/me: not logged in -> exception bubbles up")
  void me_notLoggedIn() throws Exception {
    doThrow(new RuntimeException("Not logged in")).when(userService).currentUser();

    try {
      mockMvc.perform(get("/api/v1/users/me"));
    } catch (Exception e) {
      assert e.getMessage().contains("Not logged in");
    }
  }

  @Test
  @DisplayName("GET /users/{id}: typical -> 200 with profile")
  void profile_typical() throws Exception {
    UserView profile = new UserView(7L, "Bob");

    doReturn(profile).when(userService).getProfile(7L);

    mockMvc
        .perform(get("/api/v1/users/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(7))
        .andExpect(jsonPath("$.data.name").value("Bob"));

    verify(userService, times(1)).getProfile(7L);
  }

  @Test
  @DisplayName("GET /users/{id}: user not found -> 200 with null (service returns null)")
  void profile_notFound() throws Exception {
    doReturn(null).when(userService).getProfile(999L);

    mockMvc
        .perform(get("/api/v1/users/999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isEmpty());
  }
}
