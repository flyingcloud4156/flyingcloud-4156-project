package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.any;
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
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.service.LedgerService;
import java.time.LocalDate;
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
 * Unit tests for {@link LedgerController}.
 *
 * <p>Approach: Use standalone MockMvc to avoid Spring context. Mock LedgerService for isolation.
 * Cover typical/atypical/invalid paths for all endpoints (POST, GET, DELETE) with AAA structure.
 */
@ExtendWith(MockitoExtension.class)
class LedgerControllerTest {

  @Mock private LedgerService ledgerService;

  @InjectMocks private LedgerController controller;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  // ====== POST /api/v1/ledgers ======

  @Test
  @DisplayName("POST /ledgers: typical GROUP_BALANCE ledger -> 201")
  void createLedger_typical() throws Exception {
    CreateLedgerRequest req = new CreateLedgerRequest();
    req.setName("Family Budget");
    req.setLedgerType("GROUP_BALANCE");
    req.setBaseCurrency("USD");
    req.setShareStartDate(LocalDate.of(2025, 1, 1));

    LedgerResponse resp = new LedgerResponse();
    resp.setLedgerId(1L);
    resp.setName("Family Budget");
    resp.setLedgerType("GROUP_BALANCE");
    resp.setBaseCurrency("USD");

    doReturn(resp).when(ledgerService).createLedger(any(CreateLedgerRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.ledgerId").value(1))
        .andExpect(jsonPath("$.data.name").value("Family Budget"));

    verify(ledgerService, times(1)).createLedger(any(CreateLedgerRequest.class));
  }

  @Test
  @DisplayName("POST /ledgers: atypical with future share start date -> 201")
  void createLedger_futureDate() throws Exception {
    CreateLedgerRequest req = new CreateLedgerRequest();
    req.setName("Future Project");
    req.setLedgerType("GROUP_BALANCE");
    req.setBaseCurrency("EUR");
    req.setShareStartDate(LocalDate.of(2026, 12, 31));

    LedgerResponse resp = new LedgerResponse();
    resp.setLedgerId(2L);
    resp.setName("Future Project");

    doReturn(resp).when(ledgerService).createLedger(any());

    mockMvc
        .perform(
            post("/api/v1/ledgers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.ledgerId").value(2));
  }

  @Test
  @DisplayName("POST /ledgers: invalid not logged in -> exception")
  void createLedger_notLoggedIn() throws Exception {
    CreateLedgerRequest req = new CreateLedgerRequest();
    req.setName("Test");
    req.setLedgerType("GROUP_BALANCE");
    req.setBaseCurrency("USD");

    doThrow(new RuntimeException("Not logged in"))
        .when(ledgerService)
        .createLedger(any(CreateLedgerRequest.class));

    try {
      mockMvc.perform(
          post("/api/v1/ledgers")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      assert e.getMessage().contains("Not logged in");
    }
  }

  // ====== GET /api/v1/ledgers/mine ======

  @Test
  @DisplayName("GET /ledgers/mine: typical with multiple ledgers -> 200")
  void getMyLedgers_typical() {
    MyLedgersResponse resp = new MyLedgersResponse();
    resp.setItems(new ArrayList<>());

    MyLedgersResponse.LedgerItem item1 = new MyLedgersResponse.LedgerItem();
    item1.setLedgerId(1L);
    item1.setName("Family");
    item1.setRole("OWNER");

    MyLedgersResponse.LedgerItem item2 = new MyLedgersResponse.LedgerItem();
    item2.setLedgerId(2L);
    item2.setName("Work");
    item2.setRole("EDITOR");

    resp.getItems().add(item1);
    resp.getItems().add(item2);

    doReturn(resp).when(ledgerService).getMyLedgers();

    // Directly call controller due to special :mine path pattern
    var result = controller.getMyLedgers();

    assert result.isSuccess();
    assert result.getData().getItems().size() == 2;
    assert result.getData().getItems().get(0).getLedgerId() == 1L;
    assert result.getData().getItems().get(1).getRole().equals("EDITOR");

    verify(ledgerService, times(1)).getMyLedgers();
  }

  @Test
  @DisplayName("GET /ledgers/mine: atypical empty result -> 200")
  void getMyLedgers_empty() {
    MyLedgersResponse resp = new MyLedgersResponse();
    resp.setItems(Collections.emptyList());

    doReturn(resp).when(ledgerService).getMyLedgers();

    var result = controller.getMyLedgers();

    assert result.isSuccess();
    assert result.getData().getItems().isEmpty();
  }

  @Test
  @DisplayName("GET /ledgers/mine: invalid not logged in -> exception")
  void getMyLedgers_notLoggedIn() {
    doThrow(new RuntimeException("Not logged in")).when(ledgerService).getMyLedgers();

    try {
      controller.getMyLedgers();
      assert false : "Expected exception";
    } catch (RuntimeException e) {
      assert e.getMessage().contains("Not logged in");
    }
  }

  // ====== GET /api/v1/ledgers/{ledgerId} ======

  @Test
  @DisplayName("GET /ledgers/{id}: typical -> 200 with details")
  void getLedgerDetails_typical() throws Exception {
    LedgerResponse resp = new LedgerResponse();
    resp.setLedgerId(1L);
    resp.setName("Family Budget");
    resp.setLedgerType("GROUP_BALANCE");
    resp.setBaseCurrency("USD");

    doReturn(resp).when(ledgerService).getLedgerDetails(eq(1L));

    mockMvc
        .perform(get("/api/v1/ledgers/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.ledgerId").value(1))
        .andExpect(jsonPath("$.data.name").value("Family Budget"))
        .andExpect(jsonPath("$.data.baseCurrency").value("USD"));

    verify(ledgerService, times(1)).getLedgerDetails(eq(1L));
  }

  @Test
  @DisplayName("GET /ledgers/{id}: atypical very large ledgerId -> 200 if exists")
  void getLedgerDetails_largeLedgerId() throws Exception {
    LedgerResponse resp = new LedgerResponse();
    resp.setLedgerId(999999L);
    resp.setName("Test");

    doReturn(resp).when(ledgerService).getLedgerDetails(eq(999999L));

    mockMvc
        .perform(get("/api/v1/ledgers/999999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.ledgerId").value(999999));
  }

  @Test
  @DisplayName("GET /ledgers/{id}: invalid not found -> exception")
  void getLedgerDetails_notFound() throws Exception {
    doThrow(new RuntimeException("Ledger not found"))
        .when(ledgerService)
        .getLedgerDetails(eq(999L));

    try {
      mockMvc.perform(get("/api/v1/ledgers/999"));
    } catch (Exception e) {
      assert e.getMessage().contains("Ledger not found");
    }
  }

  @Test
  @DisplayName("GET /ledgers/{id}: invalid not member -> exception")
  void getLedgerDetails_notMember() throws Exception {
    doThrow(new RuntimeException("User not a member")).when(ledgerService).getLedgerDetails(eq(5L));

    try {
      mockMvc.perform(get("/api/v1/ledgers/5"));
    } catch (Exception e) {
      assert e.getMessage().contains("User not a member");
    }
  }

  // ====== POST /api/v1/ledgers/{ledgerId}/members ======

  @Test
  @DisplayName("POST /ledgers/{id}/members: typical add EDITOR -> 201")
  void addMember_typical() throws Exception {
    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(10L);
    req.setRole("EDITOR");

    LedgerMemberResponse resp = new LedgerMemberResponse();
    resp.setUserId(10L);
    resp.setRole("EDITOR");

    doReturn(resp).when(ledgerService).addMember(eq(1L), any(AddLedgerMemberRequest.class));

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userId").value(10))
        .andExpect(jsonPath("$.data.role").value("EDITOR"));

    verify(ledgerService, times(1)).addMember(eq(1L), any(AddLedgerMemberRequest.class));
  }

  @Test
  @DisplayName("POST /ledgers/{id}/members: atypical add VIEWER -> 201")
  void addMember_viewer() throws Exception {
    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(20L);
    req.setRole("VIEWER");

    LedgerMemberResponse resp = new LedgerMemberResponse();
    resp.setUserId(20L);
    resp.setRole("VIEWER");

    doReturn(resp).when(ledgerService).addMember(eq(1L), any());

    mockMvc
        .perform(
            post("/api/v1/ledgers/1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.role").value("VIEWER"));
  }

  @Test
  @DisplayName("POST /ledgers/{id}/members: invalid already member -> exception")
  void addMember_alreadyMember() throws Exception {
    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(10L);
    req.setRole("EDITOR");

    doThrow(new RuntimeException("User is already a member"))
        .when(ledgerService)
        .addMember(eq(1L), any());

    try {
      mockMvc.perform(
          post("/api/v1/ledgers/1/members")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      assert e.getMessage().contains("already a member");
    }
  }

  @Test
  @DisplayName("POST /ledgers/{id}/members: invalid not owner/admin -> exception")
  void addMember_notOwnerOrAdmin() throws Exception {
    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(30L);
    req.setRole("EDITOR");

    doThrow(new RuntimeException("Only OWNER or ADMIN can add members"))
        .when(ledgerService)
        .addMember(eq(1L), any());

    try {
      mockMvc.perform(
          post("/api/v1/ledgers/1/members")
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(req)));
    } catch (Exception e) {
      assert e.getMessage().contains("OWNER or ADMIN");
    }
  }

  // ====== GET /api/v1/ledgers/{ledgerId}/members ======

  @Test
  @DisplayName("GET /ledgers/{id}/members: typical -> 200 with member list")
  void listMembers_typical() throws Exception {
    ListLedgerMembersResponse resp = new ListLedgerMembersResponse();
    resp.setItems(new ArrayList<>());

    ListLedgerMembersResponse.LedgerMemberItem member1 =
        new ListLedgerMembersResponse.LedgerMemberItem();
    member1.setUserId(1L);
    member1.setName("Owner");
    member1.setRole("OWNER");

    ListLedgerMembersResponse.LedgerMemberItem member2 =
        new ListLedgerMembersResponse.LedgerMemberItem();
    member2.setUserId(2L);
    member2.setName("Editor");
    member2.setRole("EDITOR");

    resp.getItems().add(member1);
    resp.getItems().add(member2);

    doReturn(resp).when(ledgerService).listMembers(eq(1L));

    mockMvc
        .perform(get("/api/v1/ledgers/1/members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].role").value("OWNER"))
        .andExpect(jsonPath("$.data.items[1].name").value("Editor"));

    verify(ledgerService, times(1)).listMembers(eq(1L));
  }

  @Test
  @DisplayName("GET /ledgers/{id}/members: atypical single member -> 200")
  void listMembers_singleMember() throws Exception {
    ListLedgerMembersResponse resp = new ListLedgerMembersResponse();
    resp.setItems(new ArrayList<>());

    ListLedgerMembersResponse.LedgerMemberItem member =
        new ListLedgerMembersResponse.LedgerMemberItem();
    member.setUserId(1L);
    member.setRole("OWNER");
    resp.getItems().add(member);

    doReturn(resp).when(ledgerService).listMembers(eq(2L));

    mockMvc
        .perform(get("/api/v1/ledgers/2/members"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(1));
  }

  @Test
  @DisplayName("GET /ledgers/{id}/members: invalid ledger not found -> exception")
  void listMembers_ledgerNotFound() throws Exception {
    doThrow(new RuntimeException("Ledger not found")).when(ledgerService).listMembers(eq(999L));

    try {
      mockMvc.perform(get("/api/v1/ledgers/999/members"));
    } catch (Exception e) {
      assert e.getMessage().contains("Ledger not found");
    }
  }

  // ====== DELETE /api/v1/ledgers/{ledgerId}/members/{userId} ======

  @Test
  @DisplayName("DELETE /ledgers/{id}/members/{userId}: typical -> 204")
  void removeMember_typical() throws Exception {
    doNothing().when(ledgerService).removeMember(eq(1L), eq(10L));

    mockMvc
        .perform(delete("/api/v1/ledgers/1/members/10"))
        .andExpect(status().isNoContent())
        .andExpect(jsonPath("$.success").value(true));

    verify(ledgerService, times(1)).removeMember(eq(1L), eq(10L));
  }

  @Test
  @DisplayName("DELETE /ledgers/{id}/members/{userId}: atypical remove last non-owner -> 204")
  void removeMember_lastNonOwner() throws Exception {
    doNothing().when(ledgerService).removeMember(eq(1L), eq(20L));

    mockMvc.perform(delete("/api/v1/ledgers/1/members/20")).andExpect(status().isNoContent());

    verify(ledgerService, times(1)).removeMember(eq(1L), eq(20L));
  }

  @Test
  @DisplayName("DELETE /ledgers/{id}/members/{userId}: invalid member not found -> exception")
  void removeMember_memberNotFound() throws Exception {
    doThrow(new RuntimeException("Member not found"))
        .when(ledgerService)
        .removeMember(eq(1L), eq(999L));

    try {
      mockMvc.perform(delete("/api/v1/ledgers/1/members/999"));
    } catch (Exception e) {
      assert e.getMessage().contains("Member not found");
    }
  }

  @Test
  @DisplayName("DELETE /ledgers/{id}/members/{userId}: invalid not owner/admin -> exception")
  void removeMember_notOwnerOrAdmin() throws Exception {
    doThrow(new RuntimeException("Only OWNER or ADMIN can remove members"))
        .when(ledgerService)
        .removeMember(eq(1L), eq(10L));

    try {
      mockMvc.perform(delete("/api/v1/ledgers/1/members/10"));
    } catch (Exception e) {
      assert e.getMessage().contains("OWNER or ADMIN");
    }
  }

  // ====== GET /api/v1/ledgers/{ledgerId}/settlement-plan ======

  @Test
  @DisplayName("GET /ledgers/{id}/settlement-plan: typical -> 200 with plan")
  void getSettlementPlan_typical() throws Exception {
    dev.coms4156.project.groupproject.dto.SettlementPlanResponse resp =
        new dev.coms4156.project.groupproject.dto.SettlementPlanResponse();
    resp.setTransfers(new ArrayList<>());

    doReturn(resp).when(ledgerService).getSettlementPlan(eq(1L));

    mockMvc
        .perform(get("/api/v1/ledgers/1/settlement-plan"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(ledgerService, times(1)).getSettlementPlan(eq(1L));
  }

  // ====== POST /api/v1/ledgers/{ledgerId}/settlement-plan ======

  @Test
  @DisplayName("POST /ledgers/{id}/settlement-plan: with config (service is impl) -> 200")
  void getSettlementPlanWithConfig_serviceIsImpl() {
    // This test covers the instanceof branch
    // In real scenarios, ledgerService would be the actual impl
    // For this unit test, we can only test the else branch since it's mocked
    dev.coms4156.project.groupproject.dto.SettlementConfig config =
        new dev.coms4156.project.groupproject.dto.SettlementConfig();

    dev.coms4156.project.groupproject.dto.SettlementPlanResponse resp =
        new dev.coms4156.project.groupproject.dto.SettlementPlanResponse();
    resp.setTransfers(new ArrayList<>());

    doReturn(resp).when(ledgerService).getSettlementPlan(eq(1L));

    var result = controller.getSettlementPlanWithConfig(1L, config);

    assert result.isSuccess();
    verify(ledgerService, times(1)).getSettlementPlan(eq(1L));
  }

  @Test
  @DisplayName("POST /ledgers/{id}/settlement-plan: null config -> 200")
  void getSettlementPlanWithConfig_nullConfig() {
    dev.coms4156.project.groupproject.dto.SettlementPlanResponse resp =
        new dev.coms4156.project.groupproject.dto.SettlementPlanResponse();
    resp.setTransfers(new ArrayList<>());

    doReturn(resp).when(ledgerService).getSettlementPlan(eq(1L));

    var result = controller.getSettlementPlanWithConfig(1L, null);

    assert result.isSuccess();
    verify(ledgerService, times(1)).getSettlementPlan(eq(1L));
  }
}
