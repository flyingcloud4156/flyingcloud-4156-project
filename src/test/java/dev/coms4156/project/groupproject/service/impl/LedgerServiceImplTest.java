package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import dev.coms4156.project.groupproject.dto.SettlementConfig;
import dev.coms4156.project.groupproject.dto.SettlementPlanResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Currency;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.CurrencyMapper;
import dev.coms4156.project.groupproject.mapper.DebtEdgeMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  @Mock private CurrencyMapper currencyMapper;

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
    return debtEdge(ledgerId, transactionId, fromUserId, toUserId, amount, "USD");
  }

  private static DebtEdge debtEdge(
      long ledgerId,
      long transactionId,
      long fromUserId,
      long toUserId,
      BigDecimal amount,
      String currency) {
    DebtEdge edge = new DebtEdge();
    edge.setLedgerId(ledgerId);
    edge.setTransactionId(transactionId);
    edge.setFromUserId(fromUserId);
    edge.setToUserId(toUserId);
    edge.setAmount(amount);
    edge.setEdgeCurrency(currency);
    edge.setCreatedAt(LocalDateTime.now());
    return edge;
  }

  private static Currency currency(String code, int exponent) {
    Currency c = new Currency();
    c.setCode(code);
    c.setExponent(exponent);
    return c;
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

  // ========== Enhanced Settlement Features Tests ==========

  @Test
  @DisplayName("getSettlementPlan with config: currency conversion - EUR to USD")
  void getSettlementPlan_currencyConversion() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    Ledger ledger = ledger(10L, "Family");
    ledger.setBaseCurrency("USD");
    doReturn(ledger).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    // Edge in EUR, needs conversion to USD (1 EUR = 1.1 USD)
    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("100.00"), "EUR");
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    // Mock currency
    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    Map<String, BigDecimal> rates = new HashMap<>();
    rates.put("EUR-USD", new BigDecimal("1.1"));
    config.setCurrencyRates(rates);

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // 100 EUR * 1.1 = 110 USD
    assertEquals(new BigDecimal("110.00"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan with config: rounding ROUND_HALF_UP")
  void getSettlementPlan_roundingHalfUp() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    // Amount that needs rounding: 25.555
    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("25.555"));
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setRoundingStrategy("ROUND_HALF_UP");

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // 25.555 rounded to 2 decimals with HALF_UP = 25.56
    assertEquals(new BigDecimal("25.56"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan with config: rounding TRIM_TO_UNIT")
  void getSettlementPlan_roundingTrimToUnit() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("25.999"));
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setRoundingStrategy("TRIM_TO_UNIT");

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // 25.999 trimmed to 2 decimals with DOWN = 25.99
    assertEquals(new BigDecimal("25.99"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan with config: rounding NONE")
  void getSettlementPlan_roundingNone() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    BigDecimal exactAmount = new BigDecimal("25.123456789");
    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, exactAmount);
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setRoundingStrategy("NONE");

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // Amount should remain unchanged
    assertEquals(exactAmount, resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan with config: transfer cap limits amount")
  void getSettlementPlan_transferCap() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    // Large debt that should be capped
    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("5000.00"));
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setMaxTransferAmount(new BigDecimal("1000.00"));

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // Should be capped at 1000
    assertEquals(new BigDecimal("1000.00"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan with config: payment channel constraint blocks transfer")
  void getSettlementPlan_paymentChannelConstraint() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("25.00"));
    DebtEdge edge2 = debtEdge(10L, 2L, 1L, 3L, new BigDecimal("30.00"));
    doReturn(Arrays.asList(edge1, edge2)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    User charlie = user(3L, "Charlie");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);
    doReturn(charlie).when(userMapper).selectById(3L);

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    // Block payment from user 2 to user 1
    Map<String, Set<String>> channels = new HashMap<>();
    channels.put("2-1", Collections.emptySet()); // Empty set means blocked
    config.setPaymentChannels(channels);

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    // Should still have transfers, but 2->1 should be blocked, so we get 1->3
    assertTrue(resp.getTransferCount() > 0);
    // Verify that blocked transfer (2->1) is not present
    boolean hasBlockedTransfer =
        resp.getTransfers().stream()
            .anyMatch(t -> t.getFromUserId() == 2L && t.getToUserId() == 1L);
    assertFalse(hasBlockedTransfer);
  }

  @Test
  @DisplayName("getSettlementPlan with config: force min-cost flow algorithm")
  void getSettlementPlan_forceMinCostFlow() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    // Complex scenario with multiple debts
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

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setForceMinCostFlow(true);

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertNotNull(resp.getTransfers());
    // Min-cost flow should produce a valid settlement
    assertTrue(resp.getTransferCount() > 0);
  }

  @Test
  @DisplayName("getSettlementPlan with config: min-cost flow fallback when threshold exceeded")
  void getSettlementPlan_minCostFlowFallback() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    // Create a scenario that would produce many transfers with heap-greedy
    List<DebtEdge> edges = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      edges.add(debtEdge(10L, (long) i, (long) i, (long) (i + 1), new BigDecimal("10.00")));
    }
    doReturn(edges).when(debtEdgeMapper).findByLedgerId(10L);

    // Mock all users
    for (int i = 1; i <= 11; i++) {
      doReturn(user((long) i, "User" + i)).when(userMapper).selectById((long) i);
    }

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setMinCostFlowThreshold(5); // If heap-greedy produces >5 transfers, use min-cost flow

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertNotNull(resp.getTransfers());
    // Should produce a valid settlement (either algorithm)
    assertTrue(resp.getTransferCount() > 0);
  }

  @Test
  @DisplayName("getSettlementPlan with config: multiple constraints combined")
  void getSettlementPlan_multipleConstraints() {
    CurrentUserContext.set(new UserView(1L, "Alice"));

    doReturn(ledger(10L, "Family")).when(service).getById(10L);
    doReturn(member(10L, 1L, "OWNER"))
        .when(ledgerMemberMapper)
        .selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

    DebtEdge edge1 = debtEdge(10L, 1L, 1L, 2L, new BigDecimal("1500.00"));
    doReturn(Arrays.asList(edge1)).when(debtEdgeMapper).findByLedgerId(10L);

    User alice = user(1L, "Alice");
    User bob = user(2L, "Bob");
    doReturn(alice).when(userMapper).selectById(1L);
    doReturn(bob).when(userMapper).selectById(2L);

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    SettlementConfig config = new SettlementConfig();
    config.setRoundingStrategy("ROUND_HALF_UP");
    config.setMaxTransferAmount(new BigDecimal("1000.00"));

    SettlementPlanResponse resp = service.getSettlementPlan(10L, config);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // Should be capped at 1000 and rounded
    assertEquals(new BigDecimal("1000.00"), resp.getTransfers().get(0).getAmount());
  }

  @Test
  @DisplayName("getSettlementPlan with config: null config uses defaults")
  void getSettlementPlan_nullConfig() {
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

    Currency usdCurrency = currency("USD", 2);
    doReturn(usdCurrency).when(currencyMapper).selectById("USD");

    // Pass null config - should use defaults
    SettlementPlanResponse resp = service.getSettlementPlan(10L, null);

    assertNotNull(resp);
    assertEquals(1, resp.getTransferCount());
    // Should work with default rounding (ROUND_HALF_UP)
    assertEquals(new BigDecimal("25.00"), resp.getTransfers().get(0).getAmount());
  }
}
