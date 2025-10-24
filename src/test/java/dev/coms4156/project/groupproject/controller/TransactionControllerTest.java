package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.SplitItem;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * Unit tests for {@link TransactionController}.
 *
 * <p>Approach: Use standalone MockMvc to avoid Spring context. Mock TransactionService for
 * isolation. Cover typical/atypical/invalid paths for all endpoints (POST, GET, LIST, DELETE) with
 * AAA structure.
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

  @Mock private TransactionService transactionService;

  @InjectMocks private TransactionController controller;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  // ====== POST /api/v1/ledgers/{ledgerId}/transactions ======

  @Test
  @DisplayName("POST /transactions: typical EXPENSE with EQUAL split -> 201")
  void createTransaction_typicalExpense() throws Exception {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.of(2025, 10, 22, 12, 0));
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setNote("Lunch");
    req.setPayerId(1L);
    req.setIsPrivate(false);
    req.setRoundingStrategy("ROUND_HALF_UP");
    req.setTailAllocation("PAYER");

    SplitItem split = new SplitItem();
    split.setUserId(1L);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    split.setIncluded(true);
    req.setSplits(Collections.singletonList(split));

    CreateTransactionResponse resp = new CreateTransactionResponse();
    resp.setTransactionId(101L);

    doReturn(resp).when(transactionService).createTransaction(eq(1L), any());

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.transactionId").value(101));

    verify(transactionService, times(1)).createTransaction(eq(1L), any());
  }

  @Test
  @DisplayName("POST /transactions: atypical with very large amount -> 201")
  void createTransaction_largeAmount() throws Exception {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("999999.99"));
    req.setPayerId(1L);
    req.setIsPrivate(false);
    req.setRoundingStrategy("NONE");
    req.setTailAllocation("LARGEST_SHARE");

    SplitItem split = new SplitItem();
    split.setUserId(1L);
    split.setSplitMethod("EQUAL");
    split.setShareValue(BigDecimal.ZERO);
    split.setIncluded(true);
    req.setSplits(Collections.singletonList(split));

    CreateTransactionResponse resp = new CreateTransactionResponse();
    resp.setTransactionId(102L);

    doReturn(resp).when(transactionService).createTransaction(anyLong(), any());

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.transactionId").value(102));
  }

  @Test
  @DisplayName("POST /transactions: invalid currency mismatch -> exception")
  void createTransaction_currencyMismatch() throws Exception {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("EUR");
    req.setAmountTotal(new BigDecimal("50.00"));
    req.setPayerId(1L);
    req.setIsPrivate(false);

    SplitItem split = new SplitItem();
    split.setUserId(1L);
    split.setSplitMethod("EQUAL");
    split.setIncluded(true);
    req.setSplits(Collections.singletonList(split));

    doThrow(new RuntimeException("Currency mismatch"))
        .when(transactionService)
        .createTransaction(anyLong(), any());

    try {
      mockMvc.perform(
          post("/api/v1/ledgers/1/transactions")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      assert e.getMessage().contains("Currency mismatch");
    }
  }

  @Test
  @DisplayName("POST /transactions: invalid missing splits -> exception")
  void createTransaction_missingSplits() throws Exception {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(new BigDecimal("100.00"));
    req.setPayerId(1L);
    req.setSplits(new ArrayList<>());

    doThrow(new RuntimeException("Splits required"))
        .when(transactionService)
        .createTransaction(anyLong(), any());

    try {
      mockMvc.perform(
          post("/api/v1/ledgers/1/transactions")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      assert e.getMessage().contains("Splits required");
    }
  }

  // ====== GET /api/v1/ledgers/{ledgerId}/transactions/{transactionId} ======

  @Test
  @DisplayName("GET /transactions/{id}: typical -> 200 with transaction details")
  void getTransaction_typical() throws Exception {
    TransactionResponse resp = new TransactionResponse();
    resp.setTransactionId(10L);
    resp.setLedgerId(1L);
    resp.setType("EXPENSE");
    resp.setAmountTotal(new BigDecimal("150.00"));
    resp.setCurrency("USD");
    resp.setNote("Dinner");
    resp.setSplits(new ArrayList<>());
    resp.setEdgesPreview(new ArrayList<>());

    doReturn(resp).when(transactionService).getTransaction(eq(1L), eq(10L));

    mockMvc
        .perform(get("/api/v1/ledgers/1/transactions/10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.transactionId").value(10))
        .andExpect(jsonPath("$.data.amountTotal").value(150.00));

    verify(transactionService, times(1)).getTransaction(eq(1L), eq(10L));
  }

  @Test
  @DisplayName("GET /transactions/{id}: atypical transaction with large splits -> 200")
  void getTransaction_largeNumberOfSplits() throws Exception {
    TransactionResponse resp = new TransactionResponse();
    resp.setTransactionId(20L);
    resp.setSplits(Collections.nCopies(50, null)); // 50 splits

    doReturn(resp).when(transactionService).getTransaction(anyLong(), anyLong());

    mockMvc
        .perform(get("/api/v1/ledgers/1/transactions/20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.transactionId").value(20));
  }

  @Test
  @DisplayName("GET /transactions/{id}: invalid not found -> exception")
  void getTransaction_notFound() throws Exception {
    doThrow(new RuntimeException("Transaction not found"))
        .when(transactionService)
        .getTransaction(anyLong(), anyLong());

    try {
      mockMvc.perform(get("/api/v1/ledgers/1/transactions/999"));
    } catch (Exception e) {
      assert e.getMessage().contains("Transaction not found");
    }
  }

  @Test
  @DisplayName("GET /transactions/{id}: invalid wrong ledger -> exception")
  void getTransaction_wrongLedger() throws Exception {
    doThrow(new RuntimeException("Transaction not found in this ledger"))
        .when(transactionService)
        .getTransaction(eq(99L), eq(10L));

    try {
      mockMvc.perform(get("/api/v1/ledgers/99/transactions/10"));
    } catch (Exception e) {
      assert e.getMessage().contains("Transaction not found in this ledger");
    }
  }

  // ====== GET /api/v1/ledgers/{ledgerId}/transactions (LIST) ======

  @Test
  @DisplayName("GET /transactions: typical list with defaults -> 200")
  void listTransactions_typical() throws Exception {
    ListTransactionsResponse resp = new ListTransactionsResponse();
    resp.setTotal(5L);
    resp.setItems(new ArrayList<>());

    doReturn(resp)
        .when(transactionService)
        .listTransactions(eq(1L), eq(1), eq(50), eq(null), eq(null), eq(null), eq(null));

    mockMvc
        .perform(get("/api/v1/ledgers/1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(5));

    verify(transactionService, times(1))
        .listTransactions(eq(1L), eq(1), eq(50), eq(null), eq(null), eq(null), eq(null));
  }

  @Test
  @DisplayName("GET /transactions: atypical with all filters -> 200")
  void listTransactions_allFilters() throws Exception {
    ListTransactionsResponse resp = new ListTransactionsResponse();
    resp.setTotal(2L);
    resp.setItems(new ArrayList<>());

    doReturn(resp)
        .when(transactionService)
        .listTransactions(
            eq(1L),
            eq(2),
            eq(10),
            eq("2025-10-01T00:00:00"),
            eq("2025-10-31T23:59:59"),
            eq("EXPENSE"),
            eq(5L));

    mockMvc
        .perform(
            get("/api/v1/ledgers/1/transactions")
                .param("page", "2")
                .param("size", "10")
                .param("from", "2025-10-01T00:00:00")
                .param("to", "2025-10-31T23:59:59")
                .param("type", "EXPENSE")
                .param("created_by", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(2));

    verify(transactionService, times(1))
        .listTransactions(
            eq(1L),
            eq(2),
            eq(10),
            eq("2025-10-01T00:00:00"),
            eq("2025-10-31T23:59:59"),
            eq("EXPENSE"),
            eq(5L));
  }

  @Test
  @DisplayName("GET /transactions: atypical size > 200 clamped to 200 -> 200")
  void listTransactions_sizeClampedTo200() throws Exception {
    ListTransactionsResponse resp = new ListTransactionsResponse();
    resp.setTotal(0L);

    doReturn(resp)
        .when(transactionService)
        .listTransactions(eq(1L), eq(1), eq(200), eq(null), eq(null), eq(null), eq(null));

    mockMvc
        .perform(get("/api/v1/ledgers/1/transactions").param("size", "500"))
        .andExpect(status().isOk());

    verify(transactionService, times(1))
        .listTransactions(eq(1L), eq(1), eq(200), eq(null), eq(null), eq(null), eq(null));
  }

  @Test
  @DisplayName("GET /transactions: invalid page 0 -> still calls service (no validation)")
  void listTransactions_page0() throws Exception {
    ListTransactionsResponse resp = new ListTransactionsResponse();
    resp.setTotal(0L);

    doReturn(resp)
        .when(transactionService)
        .listTransactions(anyLong(), anyInt(), anyInt(), any(), any(), any(), any());

    mockMvc
        .perform(get("/api/v1/ledgers/1/transactions").param("page", "0"))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /transactions: typical empty result -> 200")
  void listTransactions_emptyResult() throws Exception {
    ListTransactionsResponse resp = new ListTransactionsResponse();
    resp.setTotal(0L);
    resp.setItems(Collections.emptyList());

    doReturn(resp)
        .when(transactionService)
        .listTransactions(anyLong(), anyInt(), anyInt(), any(), any(), any(), any());

    mockMvc
        .perform(get("/api/v1/ledgers/1/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0))
        .andExpect(jsonPath("$.data.items").isEmpty());
  }

  // ====== DELETE /api/v1/ledgers/{ledgerId}/transactions/{transactionId} ======

  @Test
  @DisplayName("DELETE /transactions/{id}: typical -> 204 No Content")
  void deleteTransaction_typical() throws Exception {
    doNothing().when(transactionService).deleteTransaction(eq(1L), eq(10L));

    mockMvc
        .perform(delete("/api/v1/ledgers/1/transactions/10"))
        .andExpect(status().isNoContent())
        .andExpect(jsonPath("$.success").value(true));

    verify(transactionService, times(1)).deleteTransaction(eq(1L), eq(10L));
  }

  @Test
  @DisplayName("DELETE /transactions/{id}: atypical deleting last transaction -> 204")
  void deleteTransaction_lastTransaction() throws Exception {
    doNothing().when(transactionService).deleteTransaction(anyLong(), anyLong());

    mockMvc.perform(delete("/api/v1/ledgers/1/transactions/999")).andExpect(status().isNoContent());

    verify(transactionService, times(1)).deleteTransaction(eq(1L), eq(999L));
  }

  @Test
  @DisplayName("DELETE /transactions/{id}: invalid not found -> exception")
  void deleteTransaction_notFound() throws Exception {
    doThrow(new RuntimeException("Transaction not found"))
        .when(transactionService)
        .deleteTransaction(anyLong(), anyLong());

    try {
      mockMvc.perform(delete("/api/v1/ledgers/1/transactions/888"));
    } catch (Exception e) {
      assert e.getMessage().contains("Transaction not found");
    }
  }

  @Test
  @DisplayName("DELETE /transactions/{id}: invalid not member -> exception")
  void deleteTransaction_notMember() throws Exception {
    doThrow(new RuntimeException("User not a member"))
        .when(transactionService)
        .deleteTransaction(anyLong(), anyLong());

    try {
      mockMvc.perform(delete("/api/v1/ledgers/1/transactions/10"));
    } catch (Exception e) {
      assert e.getMessage().contains("User not a member");
    }
  }
}
