package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.LedgerService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration test for LedgerService with database.
 *
 * <p>Tests the integration: LedgerService → LedgerMapper → LedgerMemberMapper → Database
 *
 * <p>Verifies: - createLedger() inserts to ledgers table - Owner is automatically added to
 * ledger_members table - Shared data (ledger_id, user_id) is correctly maintained - listMembers()
 * returns consistent data
 */
@SpringBootTest
@Transactional
class LedgerDatabaseIntegrationTest {

  @Autowired private LedgerService ledgerService;
  @Autowired private LedgerMapper ledgerMapper;
  @Autowired private LedgerMemberMapper ledgerMemberMapper;
  @Autowired private UserMapper userMapper;

  private Long testUser1Id;
  private Long testUser2Id;

  @BeforeEach
  void setUp() {
    User user1 = new User();
    user1.setEmail("ledger_int_1@example.com");
    user1.setName("Ledger Test User 1");
    user1.setPasswordHash("hash1");
    userMapper.insert(user1);
    testUser1Id = user1.getId();

    User user2 = new User();
    user2.setEmail("ledger_int_2@example.com");
    user2.setName("Ledger Test User 2");
    user2.setPasswordHash("hash2");
    userMapper.insert(user2);
    testUser2Id = user2.getId();

    UserView userView = new UserView(testUser1Id, "Ledger Test User 1");
    CurrentUserContext.set(userView);
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  void testCreateLedger_insertsToLedgersTable() {
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Test Ledger");
    request.setLedgerType("GROUP_BALANCE");
    request.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Food");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    request.setCategories(java.util.Collections.singletonList(category));

    LedgerResponse response = ledgerService.createLedger(request);

    assertNotNull(response.getLedgerId());
    assertNotNull(response.getCategories());
    assertEquals(1, response.getCategories().size());
    assertEquals("Food", response.getCategories().get(0).getName());
    assertEquals("EXPENSE", response.getCategories().get(0).getKind());

    Ledger savedLedger = ledgerMapper.selectById(response.getLedgerId());
    assertNotNull(savedLedger);
    assertEquals("Test Ledger", savedLedger.getName());
    assertEquals(testUser1Id, savedLedger.getOwnerId());
    assertEquals("GROUP_BALANCE", savedLedger.getLedgerType());
  }

  @Test
  void testCreateLedger_insertsOwnerToLedgerMembersTable() {
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Test Ledger");
    request.setLedgerType("DEBT_NETWORK");
    request.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Transport");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    request.setCategories(java.util.Collections.singletonList(category));

    LedgerResponse response = ledgerService.createLedger(request);

    List<LedgerMember> members =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, response.getLedgerId()));

    assertEquals(1, members.size());
    LedgerMember member = members.get(0);
    assertEquals(testUser1Id, member.getUserId());
    assertEquals(response.getLedgerId(), member.getLedgerId());
    assertEquals("OWNER", member.getRole());
  }

  @Test
  void testAddMember_insertsToLedgerMembersTable() {
    CreateLedgerRequest createRequest = new CreateLedgerRequest();
    createRequest.setName("Test Ledger");
    createRequest.setLedgerType("GROUP_BALANCE");
    createRequest.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Entertainment");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    createRequest.setCategories(java.util.Collections.singletonList(category));

    LedgerResponse createResponse = ledgerService.createLedger(createRequest);
    Long ledgerId = createResponse.getLedgerId();

    AddLedgerMemberRequest addRequest = new AddLedgerMemberRequest();
    addRequest.setUserId(testUser2Id);
    addRequest.setRole("EDITOR");

    ledgerService.addMember(ledgerId, addRequest);

    List<LedgerMember> members =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>().eq(LedgerMember::getLedgerId, ledgerId));

    assertEquals(2, members.size());
    assertTrue(members.stream().anyMatch(m -> m.getUserId().equals(testUser2Id)));
  }

  @Test
  void testListMembers_returnsConsistentData() {
    CreateLedgerRequest createRequest = new CreateLedgerRequest();
    createRequest.setName("Test Ledger");
    createRequest.setLedgerType("GROUP_BALANCE");
    createRequest.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Utilities");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    createRequest.setCategories(java.util.Collections.singletonList(category));

    LedgerResponse createResponse = ledgerService.createLedger(createRequest);
    Long ledgerId = createResponse.getLedgerId();

    AddLedgerMemberRequest addRequest = new AddLedgerMemberRequest();
    addRequest.setUserId(testUser2Id);
    addRequest.setRole("EDITOR");
    ledgerService.addMember(ledgerId, addRequest);

    ListLedgerMembersResponse response = ledgerService.listMembers(ledgerId);

    assertNotNull(response);
    assertEquals(2, response.getItems().size());
    assertTrue(response.getItems().stream().anyMatch(m -> m.getUserId().equals(testUser1Id)));
    assertTrue(response.getItems().stream().anyMatch(m -> m.getUserId().equals(testUser2Id)));
  }

  @Test
  void verifySharedData_ledgerIdAndUserId_consistentAcrossTables() {
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Shared Data Test");
    request.setLedgerType("SINGLE");
    request.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Misc");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    request.setCategories(java.util.Collections.singletonList(category));

    LedgerResponse response = ledgerService.createLedger(request);
    Long ledgerId = response.getLedgerId();

    Ledger ledger = ledgerMapper.selectById(ledgerId);
    LedgerMember member =
        ledgerMemberMapper.selectOne(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, ledgerId)
                .eq(LedgerMember::getUserId, testUser1Id));

    assertNotNull(ledger);
    assertNotNull(member);
    assertEquals(ledger.getId(), member.getLedgerId());
    assertEquals(ledger.getOwnerId(), member.getUserId());
  }
}
