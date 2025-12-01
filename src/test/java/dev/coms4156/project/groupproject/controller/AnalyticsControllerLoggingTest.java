package dev.coms4156.project.groupproject.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.dto.analytics.LedgerAnalyticsOverview;
import dev.coms4156.project.groupproject.service.AnalyticsService;
import dev.coms4156.project.groupproject.utils.RedisKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test for logging aspects of the AnalyticsController.
 *
 * <p>Verifies that requests to the analytics overview endpoint are correctly logged by the
 * AccessLogInterceptor, including request ID, parameters, status, and duration.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AnalyticsControllerLoggingTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AnalyticsService analyticsService;

  // Mock Redis to simulate auth
  @MockitoBean private StringRedisTemplate stringRedisTemplate;

  // Mock the inner ops
  @MockitoBean private ValueOperations<String, String> valueOperations;

  @Test
  void overviewEndpoint_shouldBeLogged(CapturedOutput output) throws Exception {
    // Given
    // 1. Mock user and auth flow. The AuthInterceptor will query Redis for the token.
    UserView mockUser = new UserView();
    mockUser.setId(1L);
    mockUser.setName("test-user");
    String userJson = objectMapper.writeValueAsString(mockUser);
    String mockToken = "mock-token-123";
    String redisKey = RedisKeys.accessTokenKey(mockToken);

    // When Redis is queried for our mock token, return the mock user JSON.
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(redisKey)).thenReturn(userJson);

    // 2. Mock the service layer to return a successful, empty response
    when(analyticsService.overview(anyLong(), any())).thenReturn(new LedgerAnalyticsOverview());

    // When
    mockMvc
        .perform(
            get("/api/v1/ledgers/123/analytics/overview")
                .param("months", "6")
                .header(RedisKeys.HEADER_TOKEN, mockToken)) // Provide the token for AuthInterceptor
        .andExpect(status().isOk());

    // Then
    String log = output.toString();
    assertThat(log).contains("event=ACCESS");
    assertThat(log).containsPattern("requestId=[a-f0-9\\-]{36}");
    assertThat(log).contains("method=GET");
    assertThat(log).contains("uri=/api/v1/ledgers/123/analytics/overview");
    assertThat(log).contains("params={months=6}");
    assertThat(log).contains("status=200");
    assertThat(log).contains("user=test-user");
    assertThat(log).containsPattern("durationMs=\\d+");
    assertThat(log).contains("exception=none");
  }
}
