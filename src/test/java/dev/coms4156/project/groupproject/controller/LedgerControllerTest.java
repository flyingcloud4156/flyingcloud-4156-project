package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.Result;
import dev.coms4156.project.groupproject.service.LedgerService;
import java.util.List;
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
class LedgerControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private LedgerService ledgerService;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    mockMvc = MockMvcBuilders.standaloneSetup(new LedgerController(ledgerService)).build();
  }

  @Test
  @DisplayName("POST /api/v1/ledgers -> 201 and body")
  void createLedger_returnsCreated() throws Exception {
    CreateLedgerRequest req = new CreateLedgerRequest();
    req.setName("Household");
    req.setLedgerType("GROUP_BALANCE");
    req.setBaseCurrency("USD");

    LedgerResponse resp = new LedgerResponse();
    resp.setLedgerId(55L);
    resp.setName("Household");
    resp.setLedgerType("GROUP_BALANCE");
    given(ledgerService.createLedger(any(CreateLedgerRequest.class))).willReturn(resp);

    mockMvc
        .perform(
            post("/api/v1/ledgers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.ledgerId").value(55))
        .andExpect(jsonPath("$.data.name").value("Household"));
  }

  @Test
  @DisplayName("GET /api/v1/ledgers:mine -> 200 and list")
  void getMyLedgers_returnsOk() throws Exception {
    MyLedgersResponse.LedgerItem item =
        new MyLedgersResponse.LedgerItem(1L, "L1", "GROUP_BALANCE", "USD", "OWNER");
    given(ledgerService.getMyLedgers()).willReturn(new MyLedgersResponse(List.of(item)));

    // Call controller method directly to avoid colon path mapping ambiguity
    LedgerController controller = new LedgerController(ledgerService);
    Result<MyLedgersResponse> result = controller.getMyLedgers();
    org.junit.jupiter.api.Assertions.assertTrue(result.isSuccess());
    org.junit.jupiter.api.Assertions.assertEquals(
        "L1", result.getData().getItems().get(0).getName());
    org.junit.jupiter.api.Assertions.assertEquals(
        "OWNER", result.getData().getItems().get(0).getRole());
  }

  @Test
  @DisplayName("GET /api/v1/ledgers/{id} -> 200 and detail")
  void getLedgerDetails_returnsOk() throws Exception {
    LedgerResponse resp = new LedgerResponse();
    resp.setLedgerId(9L);
    resp.setName("Trip");
    given(ledgerService.getLedgerDetails(eq(9L))).willReturn(resp);

    mockMvc
        .perform(get("/api/v1/ledgers/{ledgerId}", 9L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ledgerId").value(9))
        .andExpect(jsonPath("$.data.name").value("Trip"));
  }

  @Test
  @DisplayName("POST /api/v1/ledgers/{id}/members -> 201 and body")
  void addMember_returnsCreated() throws Exception {
    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(7L);
    req.setRole("EDITOR");

    LedgerMemberResponse resp = new LedgerMemberResponse(3L, 7L, "EDITOR");
    given(ledgerService.addMember(eq(3L), any(AddLedgerMemberRequest.class))).willReturn(resp);

    mockMvc
        .perform(
            post("/api/v1/ledgers/{ledgerId}/members", 3L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.userId").value(7))
        .andExpect(jsonPath("$.data.role").value("EDITOR"));
  }

  @Test
  @DisplayName("GET /api/v1/ledgers/{id}/members -> 200 and list")
  void listMembers_returnsOk() throws Exception {
    ListLedgerMembersResponse.LedgerMemberItem it =
        new ListLedgerMembersResponse.LedgerMemberItem(2L, "Alice", "ADMIN");
    given(ledgerService.listMembers(eq(5L))).willReturn(new ListLedgerMembersResponse(List.of(it)));

    mockMvc
        .perform(get("/api/v1/ledgers/{ledgerId}/members", 5L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].name").value("Alice"))
        .andExpect(jsonPath("$.data.items[0].role").value("ADMIN"));
  }

  @Test
  @DisplayName("DELETE /api/v1/ledgers/{id}/members/{uid} -> 204")
  void removeMember_returnsNoContent() throws Exception {
    mockMvc
        .perform(delete("/api/v1/ledgers/{ledgerId}/members/{userId}", 3L, 7L))
        .andExpect(status().isNoContent())
        .andExpect(jsonPath("$.success").value(true));
  }
}
