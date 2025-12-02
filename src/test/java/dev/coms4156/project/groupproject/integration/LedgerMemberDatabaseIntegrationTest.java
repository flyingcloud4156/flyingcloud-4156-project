package dev.coms4156.project.groupproject.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
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
 * Integration test for LedgerService and LedgerMemberMapper working together.
 *
 * <p>Tests the integration where LedgerService automatically creates a LedgerMember when a ledger
 * is created. Verifies that: - LedgerService calls LedgerMapper to insert ledger - LedgerService
 * calls LedgerMemberMapper to insert owner as member - Shared data (ledger_id, user_id) is
 * correctly maintained across both tables
 *
 * <p>Uses real database with @Transactional for automatic rollback.
 */
@SpringBootTest
@Transactional
class LedgerMemberDatabaseIntegrationTest {

  @Autowired private LedgerService ledgerService;
  @Autowired private LedgerMapper ledgerMapper;
  @Autowired private UserMapper userMapper;
  @Autowired private LedgerMemberMapper ledgerMemberMapper;

  private Long testUserId;

  @BeforeEach
  void setUp() {
    User user = new User();
    user.setEmail("integration_ledger_member@example.com");
    user.setName("Ledger Member Test User");
    user.setPasswordHash("hash");
    userMapper.insert(user);
    testUserId = user.getId();

    UserView userView = new UserView(testUserId, "Ledger Member Test User");
    CurrentUserContext.set(userView);
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  void whenCreateLedger_thenOwnerIsAutomaticallyAddedAsMember_andDataIsShared() {
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Test Ledger");
    request.setLedgerType("GROUP_BALANCE");
    request.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Food");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    request.setCategory(category);

    LedgerResponse response = ledgerService.createLedger(request);

    assertNotNull(response);
    assertNotNull(response.getLedgerId());
    assertEquals("OWNER", response.getRole());

    Ledger ledger = ledgerMapper.selectById(response.getLedgerId());
    assertNotNull(ledger);
    assertEquals(testUserId, ledger.getOwnerId());
    assertEquals("Test Ledger", ledger.getName());

    List<LedgerMember> members =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, response.getLedgerId()));

    assertEquals(1, members.size());
    LedgerMember member = members.get(0);
    assertEquals(testUserId, member.getUserId());
    assertEquals(response.getLedgerId(), member.getLedgerId());
    assertEquals("OWNER", member.getRole());
    assertNotNull(member.getJoinedAt());
  }

  @Test
  void verifySharedDataIntegrity_ledgerIdConsistencyAcrossTables() {
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Data Integrity Test");
    request.setLedgerType("DEBT_NETWORK");
    request.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Travel");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    request.setCategory(category);

    LedgerResponse response = ledgerService.createLedger(request);
    Long ledgerId = response.getLedgerId();

    Ledger ledger = ledgerMapper.selectById(ledgerId);
    LedgerMember member =
        ledgerMemberMapper.selectOne(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, ledgerId)
                .eq(LedgerMember::getUserId, testUserId));

    assertNotNull(ledger);
    assertNotNull(member);
    assertEquals(ledger.getId(), member.getLedgerId());
    assertEquals(ledger.getOwnerId(), member.getUserId());
  }

  @Test
  void whenCreateMultipleLedgers_eachHasCorrectOwnerMembership() {
    CreateLedgerRequest request1 = new CreateLedgerRequest();
    request1.setName("Ledger 1");
    request1.setLedgerType("GROUP_BALANCE");
    request1.setBaseCurrency("USD");

    CreateCategoryRequest category1 = new CreateCategoryRequest();
    category1.setName("Food");
    category1.setKind("EXPENSE");
    category1.setIsActive(true);
    request1.setCategory(category1);

    CreateLedgerRequest request2 = new CreateLedgerRequest();
    request2.setName("Ledger 2");
    request2.setLedgerType("SINGLE");
    request2.setBaseCurrency("USD");

    CreateCategoryRequest category2 = new CreateCategoryRequest();
    category2.setName("Transport");
    category2.setKind("EXPENSE");
    category2.setIsActive(true);
    request2.setCategory(category2);

    LedgerResponse response1 = ledgerService.createLedger(request1);
    LedgerResponse response2 = ledgerService.createLedger(request2);

    List<LedgerMember> members1 =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, response1.getLedgerId()));

    List<LedgerMember> members2 =
        ledgerMemberMapper.selectList(
            new LambdaQueryWrapper<LedgerMember>()
                .eq(LedgerMember::getLedgerId, response2.getLedgerId()));

    assertEquals(1, members1.size());
    assertEquals(1, members2.size());
    assertEquals(testUserId, members1.get(0).getUserId());
    assertEquals(testUserId, members2.get(0).getUserId());
  }

  @Test
  void verifyLedgerServiceCallsBothMappers() {
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Multi Mapper Test");
    request.setLedgerType("GROUP_BALANCE");
    request.setBaseCurrency("USD");

    CreateCategoryRequest category = new CreateCategoryRequest();
    category.setName("Entertainment");
    category.setKind("EXPENSE");
    category.setIsActive(true);
    request.setCategory(category);

    int ledgerCountBefore = ledgerMapper.selectList(null).size();
    int memberCountBefore = ledgerMemberMapper.selectList(null).size();

    LedgerResponse response = ledgerService.createLedger(request);

    int ledgerCountAfter = ledgerMapper.selectList(null).size();
    int memberCountAfter = ledgerMemberMapper.selectList(null).size();

    assertEquals(ledgerCountBefore + 1, ledgerCountAfter);
    assertEquals(memberCountBefore + 1, memberCountAfter);
  }
}
