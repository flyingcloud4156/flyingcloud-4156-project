package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.CreateTransactionRequest;
import dev.coms4156.project.groupproject.dto.CreateTransactionResponse;
import dev.coms4156.project.groupproject.dto.ListTransactionsResponse;
import dev.coms4156.project.groupproject.dto.TransactionResponse;
import dev.coms4156.project.groupproject.dto.UpdateTransactionRequest;
import dev.coms4156.project.groupproject.dto.UpdateTransactionResponse;
import dev.coms4156.project.groupproject.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private TransactionService transactionService;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TransactionController(transactionService)).build();
  }

  @Test
  @DisplayName("POST /api/v1/ledgers/{lid}/transactions -> 201")
  void createTransaction_returnsCreated() throws Exception {
    Long ledgerId = 4L;
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setTxnAt(java.time.LocalDateTime.now());
    req.setType("EXPENSE");
    req.setCurrency("USD");
    req.setAmountTotal(java.math.BigDecimal.valueOf(10.00));
    req.setPayerId(1L);
    CreateTransactionResponse resp = new CreateTransactionResponse();
    resp.setTransactionId(99L);
    given(transactionService.createTransaction(eq(ledgerId), any(CreateTransactionRequest.class)))
        .willReturn(resp);

    mockMvc
        .perform(
            post("/api/v1/ledgers/{ledgerId}/transactions", ledgerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.transactionId").value(99));
  }

  @Test
  @DisplayName("GET /api/v1/ledgers/{lid}/transactions/{tid} -> 200")
  void getTransaction_returnsOk() throws Exception {
    Long ledgerId = 3L;
    Long txnId = 7L;
    TransactionResponse resp = new TransactionResponse();
    resp.setTransactionId(txnId);
    given(transactionService.getTransaction(ledgerId, txnId)).willReturn(resp);

    mockMvc
        .perform(get("/api/v1/ledgers/{ledgerId}/transactions/{tid}", ledgerId, txnId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.transactionId").value(7));
  }

  @Test
  @DisplayName("GET /api/v1/ledgers/{lid}/transactions -> 200 list (size clamp)")
  void listTransactions_returnsOk() throws Exception {
    Long ledgerId = 8L;
    ListTransactionsResponse resp = new ListTransactionsResponse();
    given(
            transactionService.listTransactions(
                eq(ledgerId), anyInt(), anyInt(), any(), any(), any(), any(), any()))
        .willReturn(resp);

    mockMvc
        .perform(get("/api/v1/ledgers/{ledgerId}/transactions?size=500", ledgerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("PATCH /api/v1/ledgers/{lid}/transactions/{tid} -> 200")
  void updateTransaction_returnsOk() throws Exception {
    Long ledgerId = 2L;
    Long txnId = 10L;
    UpdateTransactionRequest req = new UpdateTransactionRequest();
    UpdateTransactionResponse resp = new UpdateTransactionResponse();
    resp.setTransactionId(txnId);
    given(
            transactionService.updateTransaction(
                eq(ledgerId), eq(txnId), any(UpdateTransactionRequest.class)))
        .willReturn(resp);

    mockMvc
        .perform(
            patch("/api/v1/ledgers/{ledgerId}/transactions/{tid}", ledgerId, txnId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.transactionId").value(10));
  }

  @Test
  @DisplayName("DELETE /api/v1/ledgers/{lid}/transactions/{tid} -> 204")
  void deleteTransaction_returnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/v1/ledgers/{ledgerId}/transactions/{tid}", 1L, 2L))
        .andExpect(status().isNoContent())
        .andExpect(jsonPath("$.success").value(true));
  }
}
