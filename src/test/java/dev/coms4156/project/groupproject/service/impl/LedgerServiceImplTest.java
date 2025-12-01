package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.SettlementPlanResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LedgerServiceImpl}.
 *
 * <p>Approach: Spy the service to stub MyBatis-Plus inherited methods (save, getById). Mock mappers
 * (LedgerMemberMapper, UserMapper). Stub CurrentUserContext for auth. Cover
 * typical/atypical/invalid paths with AAA structure.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

  @Mock private LedgerMemberMapper ledgerMemberMapper;
  @Mock private UserMapper userMapper;
  @Mock private DebtEdgeMapper debtEdgeMapper;

  @Spy @InjectMocks private LedgerServiceImpl service;

  @AfterEach
  void clearContext() {
    CurrentUserContext.clear();
  }

  private static CreateLedgerRequest createLedgerReq(String name) {
    CreateLedgerRequest req = new CreateLedgerRequest();
    req.setName(name);
    req.setLedgerType("GROUP_BALANCE");
    req.setBaseCurrency("USD");
    req.setShareStartDate(LocalDate.of(2025, 1, 1));
    return req;
  }

  private static Ledger ledger(long id, String name) {
    Ledger l = new Ledger();
    l.setId(id);
    l.setName(name);
    l.setLedgerType("GROUP_BALANCE");
    l.setBaseCurrency("USD");
    l.setOwnerId(1L);
    return l;
  }

  private static LedgerMember member(long ledgerId, long userId, String role) {
    LedgerMember m = new LedgerMember();
    m.setLedgerId(ledgerId);
    m.setUserId(userId);
    m.setRole(role);
    return m;
  }

  @Test
  @DisplayName("createLedger: typical -> saves ledger and inserts owner member")
  void createLedger_typical() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doAnswer(
            inv -> {
              Ledger l = inv.getArgument(0);
              l.setId(10L);
              return true;
            })
        .when(service)
        .save(any(Ledger.class));

    doReturn(1).when(ledgerMemberMapper).insert(any(LedgerMember.class));

    CreateLedgerRequest req = createLedgerReq("Family");
    LedgerResponse resp = service.createLedger(req);

    assertNotNull(resp);
    assertEquals(10L, resp.getLedgerId());
    assertEquals("Family", resp.getName());
    assertEquals("OWNER", resp.getRole());

    verify(service, times(1)).save(any(Ledger.class));
    verify(ledgerMemberMapper, times(1)).insert(any(LedgerMember.class));
  }

  @Test
  @DisplayName("createLedger: not logged in -> throws AUTH_REQUIRED")
  void createLedger_notLoggedIn() {
    CurrentUserContext.clear();
    CreateLedgerRequest req = createLedgerReq("Test");
    assertThrows(RuntimeException.class, () -> service.createLedger(req));
  }

  @Test
  @DisplayName("getMyLedgers: typical -> returns list of ledgers with roles")
  void getMyLedgers_typical() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    LedgerMember m1 = member(10L, 1L, "OWNER");
    LedgerMember m2 = member(20L, 1L, "EDITOR");

    doReturn(Arrays.asList(m1, m2))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(ledger(20L, "Work")).when(service).getById(20L);

    MyLedgersResponse resp = service.getMyLedgers();

    assertNotNull(resp);
    assertEquals(2, resp.getItems().size());
    assertEquals("Family", resp.getItems().get(0).getName());
    assertEquals("OWNER", resp.getItems().get(0).getRole());
    assertEquals("Work", resp.getItems().get(1).getName());
    assertEquals("EDITOR", resp.getItems().get(1).getRole());
  }

  @Test
  @DisplayName("getMyLedgers: not logged in -> throws")
  void getMyLedgers_notLoggedIn() {
    CurrentUserContext.clear();
    assertThrows(RuntimeException.class, () -> service.getMyLedgers());
  }

  @Test
  @DisplayName("getMyLedgers: no memberships -> returns empty list")
  void getMyLedgers_empty() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    doReturn(Collections.emptyList())
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    MyLedgersResponse resp = service.getMyLedgers();
    assertEquals(0, resp.getItems().size());
  }

  @Test
  @DisplayName("getLedgerDetails: typical -> returns ledger with user role")
  void getLedgerDetails_typical() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerResponse resp = service.getLedgerDetails(10L);

    assertEquals(10L, resp.getLedgerId());
    assertEquals("Family", resp.getName());
    assertEquals("OWNER", resp.getRole());
  }

  @Test
  @DisplayName("getLedgerDetails: ledger not found -> throws")
  void getLedgerDetails_notFound() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    doReturn(null).when(service).getById(999L);

    assertThrows(RuntimeException.class, () -> service.getLedgerDetails(999L));
  }

  @Test
  @DisplayName("getLedgerDetails: not member -> throws")
  void getLedgerDetails_notMember() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(null)
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    assertThrows(RuntimeException.class, () -> service.getLedgerDetails(10L));
  }

  @Test
  @DisplayName("getLedgerDetails: not logged in -> throws")
  void getLedgerDetails_notLoggedIn() {
    CurrentUserContext.clear();
    assertThrows(RuntimeException.class, () -> service.getLedgerDetails(10L));
  }

  @Test
  @DisplayName("addMember: typical by OWNER -> adds new member")
  void addMember_typicalByOwner() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "OWNER"))
        .doReturn(null)
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(1).when(ledgerMemberMapper).insert(any(LedgerMember.class));

    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(2L);
    req.setRole("EDITOR");

    LedgerMemberResponse resp = service.addMember(10L, req);

    assertEquals(10L, resp.getLedgerId());
    assertEquals(2L, resp.getUserId());
    assertEquals("EDITOR", resp.getRole());
    verify(ledgerMemberMapper, times(1)).insert(any(LedgerMember.class));
  }

  @Test
  @DisplayName("addMember: by ADMIN -> adds new member")
  void addMember_typicalByAdmin() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "ADMIN"))
        .doReturn(null)
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(1).when(ledgerMemberMapper).insert(any(LedgerMember.class));

    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(2L);
    req.setRole("VIEWER");

    LedgerMemberResponse resp = service.addMember(10L, req);

    assertEquals("VIEWER", resp.getRole());
    verify(ledgerMemberMapper, times(1)).insert(any(LedgerMember.class));
  }

  @Test
  @DisplayName("addMember: member already exists -> returns existing role")
  void addMember_alreadyExists() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "OWNER"))
        .doReturn(member(10L, 2L, "EDITOR"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(2L);
    req.setRole("VIEWER");

    LedgerMemberResponse resp = service.addMember(10L, req);

    assertEquals("EDITOR", resp.getRole());
    verify(ledgerMemberMapper, times(0)).insert(any(LedgerMember.class));
  }

  @Test
  @DisplayName("addMember: not OWNER/ADMIN -> throws")
  void addMember_notAuthorized() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "EDITOR"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(2L);
    req.setRole("VIEWER");

    assertThrows(RuntimeException.class, () -> service.addMember(10L, req));
  }

  @Test
  @DisplayName("addMember: not logged in -> throws")
  void addMember_notLoggedIn() {
    CurrentUserContext.clear();
    AddLedgerMemberRequest req = new AddLedgerMemberRequest();
    req.setUserId(2L);
    req.setRole("VIEWER");

    assertThrows(RuntimeException.class, () -> service.addMember(10L, req));
  }

  @Test
  @DisplayName("listMembers: typical -> returns all members with names")
  void listMembers_typical() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    LedgerMember m1 = member(10L, 1L, "OWNER");
    LedgerMember m2 = member(10L, 2L, "EDITOR");

    doReturn(Arrays.asList(m1, m2))
        .when(ledgerMemberMapper)
        .selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    User u1 = new User();
    u1.setId(1L);
    u1.setName("Alice");
    User u2 = new User();
    u2.setId(2L);
    u2.setName("Bob");

    doReturn(u1).when(userMapper).selectById(1L);
    doReturn(u2).when(userMapper).selectById(2L);

    ListLedgerMembersResponse resp = service.listMembers(10L);

    assertEquals(2, resp.getItems().size());
    assertEquals("Alice", resp.getItems().get(0).getName());
    assertEquals("OWNER", resp.getItems().get(0).getRole());
    assertEquals("Bob", resp.getItems().get(1).getName());
    assertEquals("EDITOR", resp.getItems().get(1).getRole());
  }

  @Test
  @DisplayName("listMembers: not member -> throws")
  void listMembers_notMember() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(null)
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    assertThrows(RuntimeException.class, () -> service.listMembers(10L));
  }

  @Test
  @DisplayName("listMembers: not logged in -> throws")
  void listMembers_notLoggedIn() {
    CurrentUserContext.clear();
    assertThrows(RuntimeException.class, () -> service.listMembers(10L));
  }

  @Test
  @DisplayName("removeMember: typical by OWNER -> deletes member")
  void removeMember_typical() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(1)
        .when(ledgerMemberMapper)
        .delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    service.removeMember(10L, 2L);

    verify(ledgerMemberMapper, times(1))
        .delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
  }

  @Test
  @DisplayName("removeMember: remove self as last owner -> throws")
  void removeMember_lastOwner() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(1L)
        .when(ledgerMemberMapper)
        .selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    assertThrows(RuntimeException.class, () -> service.removeMember(10L, 1L));
  }

  @Test
  @DisplayName("removeMember: remove self with multiple owners -> succeeds")
  void removeMember_selfWithMultipleOwners() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(2L)
        .when(ledgerMemberMapper)
        .selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(1)
        .when(ledgerMemberMapper)
        .delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    service.removeMember(10L, 1L);

    verify(ledgerMemberMapper, times(1))
        .delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
  }

  @Test
  @DisplayName("removeMember: not OWNER/ADMIN -> throws")
  void removeMember_notAuthorized() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(member(10L, 1L, "VIEWER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    assertThrows(RuntimeException.class, () -> service.removeMember(10L, 2L));
  }

  @Test
  @DisplayName("removeMember: not logged in -> throws")
  void removeMember_notLoggedIn() {
    CurrentUserContext.clear();
    assertThrows(RuntimeException.class, () -> service.removeMember(10L, 2L));
  }

  private static DebtEdge debtEdge(
      long ledgerId, long transactionId, long fromUserId, long toUserId, BigDecimal amount) {
    DebtEdge edge = new DebtEdge();
    edge.setLedgerId(ledgerId);
    edge.setTransactionId(transactionId);
    edge.setFromUserId(fromUserId);
    edge.setToUserId(toUserId);
    edge.setAmount(amount);
    edge.setEdgeCurrency("USD");
    edge.setCreatedAt(LocalDateTime.now());
    return edge;
  }

  private static User user(long id, String name) {
    User u = new User();
    u.setId(id);
    u.setName(name);
    return u;
  }

  @Test
  @DisplayName("getSettlementPlan: typical single debt -> returns one transfer")
  void getSettlementPlan_typicalSingleDebt() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("25.00"));
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertEquals(10L, resp.getLedgerId());
    assertEquals("USD", resp.getCurrency());
    assertEquals(1, resp.getTransferCount());
    assertEquals(1, resp.getTransfers().size());
    assertEquals(2L, resp.getTransfers().get(0).getFromUserId());
    assertEquals("Bob", resp.getTransfers().get(0).getFromUserName());
    assertEquals(1L, resp.getTransfers().get(0).getToUserId());
    assertEquals("Alice", resp.getTransfers().get(0).getToUserName());
    assertEquals(new BigDecimal("25.00"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan: handles symmetry - bidirectional debts netted correctly")
  void getSettlementPlan_bidirectionalNetting() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("20.00"));
    DebtEdge edge2 = debtEdge(10L, 2L, 2L, 1L, new BigDecimal("40.00"));
    doReturn(Arrays.asList(edge1, edge2)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    assertEquals(1L, resp.getTransfers().get(0).getFromUserId());
    assertEquals("Alice", resp.getTransfers().get(0).getFromUserName());
    assertEquals(2L, resp.getTransfers().get(0).getToUserId());
    assertEquals("Bob", resp.getTransfers().get(0).getToUserName());
    assertEquals(new BigDecimal("20.00"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan: multiple members with minimal transfers")
  void getSettlementPlan_multipleMembers() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("25.00"));
    DebtEdge edge2 = debtEdge(10L, 2L, 1L, 3L, new BigDecimal("30.00"));
    DebtEdge edge3 = debtEdge(10L, 3L, 2L, 3L, new BigDecimal("20.00"));
    doReturn(Arrays.asList(edge1, edge2, edge3)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    User charlie = user(3L, "Charlie");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);
    doReturn(charlie).when(userMapper).selectById(3L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertEquals("USD", resp.getCurrency());
    assertNotNull(resp.getTransfers());
    BigDecimal totalTransferred =
        resp.getTransfers().stream()
            .map(SettlementPlanResponse.TransferItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertEquals(new BigDecimal("55.00"), totalTransferred);
  }

  @Test
  @DisplayName("getSettlementPlan: no debt edges -> returns empty transfer list")
  void getSettlementPlan_noDebtEdges() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    doReturn(Collections.emptyList()).when(debtEdgeMapper).findByLedgerId(10L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertEquals(10L, resp.getLedgerId());
    assertEquals("USD", resp.getCurrency());
    assertEquals(0, resp.getTransferCount());
    assertEquals(0, resp.getTransfers().size());
  }

  @Test
  @DisplayName("getSettlementPlan: fully offset debts -> returns empty transfer list")
  void getSettlementPlan_fullyOffset() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("25.00"));
    DebtEdge edge2 = debtEdge(10L, 2L, 2L, 1L, new BigDecimal("25.00"));
    doReturn(Arrays.asList(edge1, edge2)).when(debtEdgeMapper).findByLedgerId(10L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertEquals(0, resp.getTransferCount());
    assertEquals(0, resp.getTransfers().size());
  }

  @Test
  @DisplayName("getSettlementPlan: minimal transfers - heap-greedy optimization")
  void getSettlementPlan_minimalTransfers() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("10.00"));
    DebtEdge edge2 = debtEdge(10L, 2L, 1L, 3L, new BigDecimal("10.00"));
    DebtEdge edge3 = debtEdge(10L, 3L, 2L, 3L, new BigDecimal("10.00"));
    doReturn(Arrays.asList(edge1, edge2, edge3)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User charlie = user(3L, "Charlie");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(charlie).when(userMapper).selectById(3L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertNotNull(resp.getTransfers());
    assertEquals(1, resp.getTransferCount());
    assertEquals(new BigDecimal("20.00"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan: not logged in -> throws")
  void getSettlementPlan_notLoggedIn() {
    CurrentUserContext.clear();
    assertThrows(RuntimeException.class, () -> service.getSettlementPlan(10L));
  }

  @Test
  @DisplayName("getSettlementPlan: ledger not found -> throws")
  void getSettlementPlan_ledgerNotFound() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    doReturn(null).when(service).getById(999L);

    assertThrows(RuntimeException.class, () -> service.getSettlementPlan(999L));
  }

  @Test
  @DisplayName("getSettlementPlan: not member -> throws")
  void getSettlementPlan_notMember() {
    CurrentUserContext.set(new UserView(1L, "Alice"));
    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(null)
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    assertThrows(RuntimeException.class, () -> service.getSettlementPlan(10L));
  }

  @Test
  @DisplayName("getSettlementPlan: scales to multiple members")
  void getSettlementPlan_scalesToMultipleMembers() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    List<DebtEdge> edges =
        Arrays.asList(
            debtEdge(10L, 1L, 1L, 2L, new BigDecimal("10.00")),
            debtEdge(10L, 2L, 2L, 3L, new BigDecimal("15.00")),
            debtEdge(10L, 3L, 3L, 4L, new BigDecimal("20.00")),
            debtEdge(10L, 4L, 4L, 5L, new BigDecimal("25.00")));
    doReturn(edges).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    User charlie = user(3L, "Charlie");
    User david = user(4L, "David");
    User eve = user(5L, "Eve");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);
    doReturn(charlie).when(userMapper).selectById(3L);
    doReturn(david).when(userMapper).selectById(4L);
    doReturn(eve).when(userMapper).selectById(5L);

    SettlementPlanResponse resp = service.getSettlementPlan(10L);

    assertNotNull(resp);
    assertNotNull(resp.getTransfers());
    BigDecimal total =
        resp.getTransfers().stream()
            .map(SettlementPlanResponse.TransferItem::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertEquals(new BigDecimal("25.00"), total);
    assertNotNull(resp.getTransfers());
  }
}
