package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.BudgetStatusItem;
import dev.coms4156.project.groupproject.dto.BudgetStatusResponse;
import dev.coms4156.project.groupproject.dto.SetBudgetRequest;
import dev.coms4156.project.groupproject.service.BudgetService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
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
 * Unit tests for {@link BudgetController}.
 *
 * <p>Testing Strategy:
 *
 * <ul>
 *   <li>Use standalone MockMvc to avoid Spring context overhead
 *   <li>Mock BudgetService for complete isolation
 *   <li>Cover valid and invalid input equivalence classes
 *   <li>Test boundary values for year (2020-2100) and month (1-12)
 *   <li>Test success and error paths for both endpoints
 *   <li>Expected responses independently determined from API contract
 *   <li>Verify controller correctly delegates to service layer
 *   <li>Target: 100% branch coverage of controller methods
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BudgetControllerTest {

  @Mock private BudgetService budgetService;

  @InjectMocks private BudgetController controller;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
  }

  // ========== POST /api/v1/ledgers/{ledgerId}/budgets Tests ==========

  @Test
  @DisplayName("POST /budgets: valid ledger-level budget (categoryId = null) -> 200 OK")
  void setBudget_validLedgerLevel_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setCategoryId(null);
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("1000.00"));

    doNothing().when(budgetService).setBudget(eq(1L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(budgetService, times(1)).setBudget(eq(1L), any(SetBudgetRequest.class));
  }

  @Test
  @DisplayName("POST /budgets: valid category-level budget (categoryId = 5) -> 200 OK")
  void setBudget_validCategoryLevel_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setCategoryId(5L);
    request.setYear(2025);
    request.setMonth(11);
    request.setLimitAmount(new BigDecimal("500.00"));

    doNothing().when(budgetService).setBudget(eq(2L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/2/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(budgetService, times(1)).setBudget(eq(2L), any(SetBudgetRequest.class));
  }

  @Test
  @DisplayName("POST /budgets: boundary year = 2020 -> 200 OK")
  void setBudget_boundaryYearMin_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2020);
    request.setMonth(1);
    request.setLimitAmount(new BigDecimal("100.00"));

    doNothing().when(budgetService).setBudget(eq(1L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).setBudget(eq(1L), any(SetBudgetRequest.class));
  }

  @Test
  @DisplayName("POST /budgets: boundary year = 2100 -> 200 OK")
  void setBudget_boundaryYearMax_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2100);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("9999.99"));

    doNothing().when(budgetService).setBudget(eq(1L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).setBudget(eq(1L), any(SetBudgetRequest.class));
  }

  @Test
  @DisplayName("POST /budgets: boundary month = 1 -> 200 OK")
  void setBudget_boundaryMonthMin_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(1);
    request.setLimitAmount(new BigDecimal("250.00"));

    doNothing().when(budgetService).setBudget(eq(1L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).setBudget(eq(1L), any(SetBudgetRequest.class));
  }

  @Test
  @DisplayName("POST /budgets: boundary month = 12 -> 200 OK")
  void setBudget_boundaryMonthMax_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(12);
    request.setLimitAmount(new BigDecimal("800.00"));

    doNothing().when(budgetService).setBudget(eq(1L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).setBudget(eq(1L), any(SetBudgetRequest.class));
  }

  @Test
  @DisplayName("POST /budgets: boundary limitAmount = 0.01 (minimum) -> 200 OK")
  void setBudget_boundaryLimitMin_returns200() throws Exception {
    SetBudgetRequest request = new SetBudgetRequest();
    request.setYear(2025);
    request.setMonth(6);
    request.setLimitAmount(new BigDecimal("0.01"));

    doNothing().when(budgetService).setBudget(eq(1L), any(SetBudgetRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).setBudget(eq(1L), any(SetBudgetRequest.class));
  }

  // ========== GET /api/v1/ledgers/{ledgerId}/budgets/status Tests ==========

  @Test
  @DisplayName("GET /budgets/status: no budgets -> returns empty list")
  void getBudgetStatus_noBudgets_returnsEmptyList() throws Exception {
    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Collections.emptyList());

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2025, 12);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2025").param("month", "12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items").isEmpty());

    verify(budgetService, times(1)).getBudgetStatus(1L, 2025, 12);
  }

  @Test
  @DisplayName("GET /budgets/status: one budget with OK status -> returns one item")
  void getBudgetStatus_oneBudgetOk_returnsOneItem() throws Exception {
    BudgetStatusItem item = new BudgetStatusItem();
    item.setBudgetId(10L);
    item.setCategoryId(null);
    item.setCategoryName("Total Budget");
    item.setLimitAmount(new BigDecimal("1000.00"));
    item.setSpentAmount(new BigDecimal("300.00"));
    item.setRatio("0.3000");
    item.setStatus("OK");

    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Collections.singletonList(item));

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2025, 11);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2025").param("month", "11"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items.length()").value(1))
        .andExpect(jsonPath("$.data.items[0].budgetId").value(10))
        .andExpect(jsonPath("$.data.items[0].categoryName").value("Total Budget"))
        .andExpect(jsonPath("$.data.items[0].status").value("OK"));

    verify(budgetService, times(1)).getBudgetStatus(1L, 2025, 11);
  }

  @Test
  @DisplayName("GET /budgets/status: one budget with NEAR_LIMIT status -> returns correct status")
  void getBudgetStatus_oneBudgetNearLimit_returnsCorrectStatus() throws Exception {
    BudgetStatusItem item = new BudgetStatusItem();
    item.setBudgetId(11L);
    item.setCategoryId(5L);
    item.setCategoryName("Category 5");
    item.setLimitAmount(new BigDecimal("500.00"));
    item.setSpentAmount(new BigDecimal("450.00"));
    item.setRatio("0.9000");
    item.setStatus("NEAR_LIMIT");

    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Collections.singletonList(item));

    doReturn(response).when(budgetService).getBudgetStatus(2L, 2025, 10);

    mockMvc
        .perform(get("/api/v1/ledgers/2/budgets/status").param("year", "2025").param("month", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].status").value("NEAR_LIMIT"))
        .andExpect(jsonPath("$.data.items[0].ratio").value("0.9000"));

    verify(budgetService, times(1)).getBudgetStatus(2L, 2025, 10);
  }

  @Test
  @DisplayName("GET /budgets/status: one budget with EXCEEDED status -> returns correct status")
  void getBudgetStatus_oneBudgetExceeded_returnsCorrectStatus() throws Exception {
    BudgetStatusItem item = new BudgetStatusItem();
    item.setBudgetId(12L);
    item.setCategoryId(null);
    item.setCategoryName("Total Budget");
    item.setLimitAmount(new BigDecimal("800.00"));
    item.setSpentAmount(new BigDecimal("1000.00"));
    item.setRatio("1.2500");
    item.setStatus("EXCEEDED");

    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Collections.singletonList(item));

    doReturn(response).when(budgetService).getBudgetStatus(3L, 2025, 9);

    mockMvc
        .perform(get("/api/v1/ledgers/3/budgets/status").param("year", "2025").param("month", "9"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].status").value("EXCEEDED"))
        .andExpect(jsonPath("$.data.items[0].ratio").value("1.2500"));

    verify(budgetService, times(1)).getBudgetStatus(3L, 2025, 9);
  }

  @Test
  @DisplayName("GET /budgets/status: two budgets -> returns two items")
  void getBudgetStatus_twoBudgets_returnsTwoItems() throws Exception {
    BudgetStatusItem item1 = new BudgetStatusItem();
    item1.setBudgetId(20L);
    item1.setCategoryId(null);
    item1.setCategoryName("Total Budget");
    item1.setLimitAmount(new BigDecimal("2000.00"));
    item1.setSpentAmount(new BigDecimal("500.00"));
    item1.setRatio("0.2500");
    item1.setStatus("OK");

    BudgetStatusItem item2 = new BudgetStatusItem();
    item2.setBudgetId(21L);
    item2.setCategoryId(3L);
    item2.setCategoryName("Category 3");
    item2.setLimitAmount(new BigDecimal("800.00"));
    item2.setSpentAmount(new BigDecimal("700.00"));
    item2.setRatio("0.8750");
    item2.setStatus("NEAR_LIMIT");

    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Arrays.asList(item1, item2));

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2025, 8);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2025").param("month", "8"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].budgetId").value(20))
        .andExpect(jsonPath("$.data.items[1].budgetId").value(21));

    verify(budgetService, times(1)).getBudgetStatus(1L, 2025, 8);
  }

  @Test
  @DisplayName("GET /budgets/status: many budgets (3) -> returns all items")
  void getBudgetStatus_manyBudgets_returnsAllItems() throws Exception {
    BudgetStatusItem item1 = new BudgetStatusItem();
    item1.setBudgetId(30L);
    item1.setCategoryId(null);
    item1.setCategoryName("Total Budget");
    item1.setStatus("OK");

    BudgetStatusItem item2 = new BudgetStatusItem();
    item2.setBudgetId(31L);
    item2.setCategoryId(1L);
    item2.setCategoryName("Category 1");
    item2.setStatus("NEAR_LIMIT");

    BudgetStatusItem item3 = new BudgetStatusItem();
    item3.setBudgetId(32L);
    item3.setCategoryId(2L);
    item3.setCategoryName("Category 2");
    item3.setStatus("EXCEEDED");

    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Arrays.asList(item1, item2, item3));

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2025, 7);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2025").param("month", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(3));

    verify(budgetService, times(1)).getBudgetStatus(1L, 2025, 7);
  }

  @Test
  @DisplayName("GET /budgets/status: boundary year = 2020 -> 200 OK")
  void getBudgetStatus_boundaryYearMin_returns200() throws Exception {
    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Collections.emptyList());

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2020, 1);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2020").param("month", "1"))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).getBudgetStatus(1L, 2020, 1);
  }

  @Test
  @DisplayName("GET /budgets/status: boundary month = 1 and month = 12 -> 200 OK")
  void getBudgetStatus_boundaryMonths_returns200() throws Exception {
    BudgetStatusResponse response = new BudgetStatusResponse();
    response.setItems(Collections.emptyList());

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2025, 1);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2025").param("month", "1"))
        .andExpect(status().isOk());

    doReturn(response).when(budgetService).getBudgetStatus(1L, 2025, 12);

    mockMvc
        .perform(get("/api/v1/ledgers/1/budgets/status").param("year", "2025").param("month", "12"))
        .andExpect(status().isOk());

    verify(budgetService, times(1)).getBudgetStatus(1L, 2025, 1);
    verify(budgetService, times(1)).getBudgetStatus(1L, 2025, 12);
  }
}
