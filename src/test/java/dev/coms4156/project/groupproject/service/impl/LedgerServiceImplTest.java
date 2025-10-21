package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.coms4156.project.groupproject.dto.AddLedgerMemberRequest;
import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerMemberResponse;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.ListLedgerMembersResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.LedgerMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the {@link LedgerServiceImpl} class.
 *
 * <p>This test suite focuses on verifying the business logic within the LedgerServiceImpl, ensuring
 * it behaves correctly in isolation. Dependencies such as mappers are mocked to achieve this
 * isolation. It covers creation, retrieval, membership management, and various access control
 * scenarios.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

  @Mock private LedgerMemberMapper ledgerMemberMapper;
  @Mock private UserMapper userMapper;
  @Mock private LedgerMapper ledgerMapper;

  // Using @Spy so we can mock the `save` method of the ServiceImpl base class
  @Spy @InjectMocks private LedgerServiceImpl ledgerService;

  private UserView testUser;
  private Ledger testLedger;
  private LedgerMember testOwnerMember;

  @BeforeEach
  void setUp() {
    testUser = new UserView(1L, "testuser");
    CurrentUserContext.set(testUser);

    testLedger = new Ledger();
    testLedger.setId(101L);
    testLedger.setName("Test Ledger");
    testLedger.setOwnerId(testUser.getId());
    testLedger.setLedgerType("GROUP_BALANCE");
    testLedger.setBaseCurrency("USD");
    testLedger.setShareStartDate(LocalDate.now());

    testOwnerMember = new LedgerMember();
    testOwnerMember.setLedgerId(testLedger.getId());
    testOwnerMember.setUserId(testUser.getId());
    testOwnerMember.setRole("OWNER");
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Nested
  @DisplayName("Create Ledger Tests")
  class CreateLedgerTests {

    @Test
    @DisplayName("createLedger() should succeed for an authenticated user")
    void createLedger_Success() {
      // Arrange
      CreateLedgerRequest request = new CreateLedgerRequest();
      request.setName("Test Ledger");
      request.setLedgerType("GROUP_BALANCE");
      request.setBaseCurrency("USD");
      request.setShareStartDate(LocalDate.now());

      ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
      ArgumentCaptor<LedgerMember> memberCaptor = ArgumentCaptor.forClass(LedgerMember.class);

      doReturn(true).when(ledgerService).save(ledgerCaptor.capture());
      when(ledgerMemberMapper.insert(memberCaptor.capture())).thenReturn(1);

      // Act
      LedgerResponse response = ledgerService.createLedger(request);

      // Assert
      assertNotNull(response);
      assertEquals(request.getName(), response.getName());
      assertEquals(request.getLedgerType(), response.getLedgerType());
      assertEquals("OWNER", response.getRole());

      Ledger savedLedger = ledgerCaptor.getValue();
      assertEquals(request.getName(), savedLedger.getName());
      assertEquals(testUser.getId(), savedLedger.getOwnerId());

      LedgerMember savedMember = memberCaptor.getValue();
      assertEquals(savedLedger.getId(), savedMember.getLedgerId());
      assertEquals(testUser.getId(), savedMember.getUserId());
      assertEquals("OWNER", savedMember.getRole());

      verify(ledgerService).save(any(Ledger.class));
      verify(ledgerMemberMapper).insert(any(LedgerMember.class));
    }

    @Test
    @DisplayName("createLedger() should throw exception if user is not authenticated")
    void createLedger_NoAuth() {
      // Arrange
      CurrentUserContext.clear(); // Ensure no user is in context
      CreateLedgerRequest request = new CreateLedgerRequest();

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> ledgerService.createLedger(request));

      assertEquals("AUTH_REQUIRED", exception.getMessage());
      verify(ledgerService, never()).save(any(Ledger.class));
      verify(ledgerMemberMapper, never()).insert(any(LedgerMember.class));
    }
  }

  @Nested
  @DisplayName("Get My Ledgers Tests")
  class GetMyLedgersTests {

    @Test
    @DisplayName("getMyLedgers() should return ledgers for an authenticated user")
    void getMyLedgers_Success() {
      // Arrange
      LedgerMember member1 = new LedgerMember();
      member1.setLedgerId(101L);
      member1.setRole("OWNER");
      LedgerMember member2 = new LedgerMember();
      member2.setLedgerId(102L);
      member2.setRole("ADMIN");

      Ledger ledger1 = new Ledger();
      ledger1.setId(101L);
      ledger1.setName("Ledger 1");
      Ledger ledger2 = new Ledger();
      ledger2.setId(102L);
      ledger2.setName("Ledger 2");

      when(ledgerMemberMapper.selectList(any())).thenReturn(List.of(member1, member2));
      doReturn(ledger1).when(ledgerService).getById(101L);
      doReturn(ledger2).when(ledgerService).getById(102L);

      // Act
      MyLedgersResponse response = ledgerService.getMyLedgers();

      // Assert
      assertNotNull(response);
      assertEquals(2, response.getItems().size());
      assertEquals("Ledger 1", response.getItems().get(0).getName());
      assertEquals("OWNER", response.getItems().get(0).getRole());
      assertEquals("Ledger 2", response.getItems().get(1).getName());
      assertEquals("ADMIN", response.getItems().get(1).getRole());
    }

    @Test
    @DisplayName("getMyLedgers() should return an empty list if user has no memberships")
    void getMyLedgers_NoMemberships() {
      // Arrange
      when(ledgerMemberMapper.selectList(any())).thenReturn(Collections.emptyList());

      // Act
      MyLedgersResponse response = ledgerService.getMyLedgers();

      // Assert
      assertNotNull(response);
      assertTrue(response.getItems().isEmpty());
    }

    @Test
    @DisplayName("getMyLedgers() should throw exception if user is not authenticated")
    void getMyLedgers_NoAuth() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> ledgerService.getMyLedgers());

      assertEquals("AUTH_REQUIRED", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Get Ledger Details Tests")
  class GetLedgerDetailsTests {

    @Test
    @DisplayName("getLedgerDetails() should return details when user is a member")
    void getLedgerDetails_Success() {
      // Arrange
      doReturn(testLedger).when(ledgerService).getById(testLedger.getId());
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testOwnerMember);

      // Act
      LedgerResponse response = ledgerService.getLedgerDetails(testLedger.getId());

      // Assert
      assertNotNull(response);
      assertEquals(testLedger.getId(), response.getLedgerId());
      assertEquals(testLedger.getName(), response.getName());
      assertEquals(testOwnerMember.getRole(), response.getRole());
    }

    @Test
    @DisplayName("getLedgerDetails() should throw exception if user is not authenticated")
    void getLedgerDetails_NoAuth() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> ledgerService.getLedgerDetails(anyLong()));
      assertEquals("AUTH_REQUIRED", exception.getMessage());
    }

    @Test
    @DisplayName("getLedgerDetails() should throw exception if ledger not found")
    void getLedgerDetails_LedgerNotFound() {
      // Arrange
      doReturn(null).when(ledgerService).getById(anyLong());

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> ledgerService.getLedgerDetails(anyLong()));
      assertEquals("LEDGER_NOT_FOUND", exception.getMessage());
    }

    @Test
    @DisplayName("getLedgerDetails() should throw exception if user is not a member")
    void getLedgerDetails_UserNotMember() {
      // Arrange
      doReturn(testLedger).when(ledgerService).getById(testLedger.getId());
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> ledgerService.getLedgerDetails(testLedger.getId()));
      assertEquals("FORBIDDEN: You are not a member of this ledger", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Add Member Tests")
  class AddMemberTests {

    private AddLedgerMemberRequest addMemberRequest;
    private UserView otherUser;
    private LedgerMember otherUserMember;

    @BeforeEach
    void setupAddMember() {
      otherUser = new UserView(2L, "otheruser");
      addMemberRequest = new AddLedgerMemberRequest();
      addMemberRequest.setUserId(otherUser.getId());
      addMemberRequest.setRole("EDITOR");

      otherUserMember = new LedgerMember();
      otherUserMember.setLedgerId(testLedger.getId());
      otherUserMember.setUserId(otherUser.getId());
      otherUserMember.setRole("EDITOR");
    }

    @Test
    @DisplayName("addMember() should succeed when current user is OWNER")
    void addMember_OwnerAddsMember_Success() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember) // First call: for callingUserMember
          .thenReturn(null); // Second call: for existingMember (new member)
      when(ledgerMemberMapper.insert(any(LedgerMember.class))).thenReturn(1);

      // Act
      LedgerMemberResponse response = ledgerService.addMember(testLedger.getId(), addMemberRequest);

      // Assert
      assertNotNull(response);
      assertEquals(testLedger.getId(), response.getLedgerId());
      assertEquals(otherUser.getId(), response.getUserId());
      assertEquals(addMemberRequest.getRole(), response.getRole()); // Assert against requested role
      verify(ledgerMemberMapper, times(1)).insert(any(LedgerMember.class));
    }

    @Test
    @DisplayName("addMember() should succeed when current user is ADMIN")
    void addMember_AdminAddsMember_Success() {
      // Arrange
      LedgerMember adminMember = new LedgerMember();
      adminMember.setLedgerId(testLedger.getId());
      adminMember.setUserId(testUser.getId());
      adminMember.setRole("ADMIN");

      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(adminMember) // First call: for callingUserMember
          .thenReturn(null); // Second call: for existingMember (new member)
      when(ledgerMemberMapper.insert(any(LedgerMember.class))).thenReturn(1);

      // Act
      LedgerMemberResponse response = ledgerService.addMember(testLedger.getId(), addMemberRequest);

      // Assert
      assertNotNull(response);
      assertEquals(testLedger.getId(), response.getLedgerId());
      assertEquals(otherUser.getId(), response.getUserId());
      assertEquals(addMemberRequest.getRole(), response.getRole()); // Assert against requested role
      verify(ledgerMemberMapper, times(1)).insert(any(LedgerMember.class));
    }

    @Test
    @DisplayName("addMember() should return existing member if already present")
    void addMember_MemberAlreadyExists() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember) // First call: for callingUserMember
          .thenReturn(otherUserMember); // Second call: for existingMember (member already exists)

      // Act
      LedgerMemberResponse response = ledgerService.addMember(testLedger.getId(), addMemberRequest);

      // Assert
      assertNotNull(response);
      assertEquals(testLedger.getId(), response.getLedgerId());
      assertEquals(otherUser.getId(), response.getUserId());
      assertEquals(otherUserMember.getRole(), response.getRole()); // Assert actual existing role
      verify(ledgerMemberMapper, never()).insert(any(LedgerMember.class));
    }

    @Test
    @DisplayName("addMember() should throw exception if user is not authenticated")
    void addMember_NoAuth() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> ledgerService.addMember(testLedger.getId(), addMemberRequest));
      assertEquals("AUTH_REQUIRED", exception.getMessage());
      verify(ledgerMemberMapper, never()).insert(any(LedgerMember.class));
    }

    @Test
    @DisplayName("addMember() should throw exception if current user has insufficient role")
    void addMember_InsufficientRole() {
      // Arrange
      LedgerMember editorMember = new LedgerMember();
      editorMember.setLedgerId(testLedger.getId());
      editorMember.setUserId(testUser.getId());
      editorMember.setRole("EDITOR");

      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(editorMember); // Current user is EDITOR

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> ledgerService.addMember(testLedger.getId(), addMemberRequest));
      assertEquals("ROLE_INSUFFICIENT: You do not have the required role", exception.getMessage());
      verify(ledgerMemberMapper, never()).insert(any(LedgerMember.class));
    }
  }

  @Nested
  @DisplayName("List Members Tests")
  class ListMembersTests {

    @Test
    @DisplayName("listMembers() should return members when user is a member")
    void listMembers_Success() {
      // Arrange
      User user1Entity = new User();
      user1Entity.setId(testUser.getId());
      user1Entity.setName(testUser.getName());

      User user2Entity = new User();
      user2Entity.setId(2L);
      user2Entity.setName("User Two");

      LedgerMember member1 = new LedgerMember();
      member1.setUserId(testUser.getId());
      member1.setRole("OWNER");
      LedgerMember member2 = new LedgerMember();
      member2.setUserId(user2Entity.getId());
      member2.setRole("EDITOR");

      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember); // Current user is a member
      when(ledgerMemberMapper.selectList(any(LambdaQueryWrapper.class)))
          .thenReturn(Arrays.asList(member1, member2));
      when(userMapper.selectById(testUser.getId())).thenReturn(user1Entity);
      when(userMapper.selectById(user2Entity.getId())).thenReturn(user2Entity);

      // Act
      ListLedgerMembersResponse response = ledgerService.listMembers(testLedger.getId());

      // Assert
      assertNotNull(response);
      assertEquals(2, response.getItems().size());
      assertEquals(testUser.getId(), response.getItems().get(0).getUserId());
      assertEquals(testUser.getName(), response.getItems().get(0).getName());
      assertEquals("OWNER", response.getItems().get(0).getRole());
      assertEquals(user2Entity.getId(), response.getItems().get(1).getUserId());
      assertEquals(user2Entity.getName(), response.getItems().get(1).getName());
      assertEquals("EDITOR", response.getItems().get(1).getRole());
    }

    @Test
    @DisplayName("listMembers() should throw exception if user is not authenticated")
    void listMembers_NoAuth() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> ledgerService.listMembers(anyLong()));
      assertEquals("AUTH_REQUIRED", exception.getMessage());
    }

    @Test
    @DisplayName("listMembers() should throw exception if user is not a member")
    void listMembers_UserNotMember() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

      // Act & Assert
      RuntimeException exception =
          assertThrows(RuntimeException.class, () -> ledgerService.listMembers(testLedger.getId()));
      assertEquals("FORBIDDEN: You are not a member of this ledger", exception.getMessage());
    }
  }

  @Nested
  @DisplayName("Remove Member Tests")
  class RemoveMemberTests {

    private UserView otherUser;
    private LedgerMember otherUserMember;
    private final Long NON_EXISTENT_USER_ID = 999L;

    @BeforeEach
    void setupRemoveMember() {
      otherUser = new UserView(2L, "otheruser");
      otherUserMember = new LedgerMember();
      otherUserMember.setLedgerId(testLedger.getId());
      otherUserMember.setUserId(otherUser.getId());
      otherUserMember.setRole("EDITOR");
    }

    @Test
    @DisplayName("removeMember() should succeed when current user is OWNER")
    void removeMember_OwnerRemovesMember_Success() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember) // Current user is OWNER
          .thenReturn(otherUserMember); // Other user is present
      when(ledgerMemberMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

      // Act
      ledgerService.removeMember(testLedger.getId(), otherUser.getId());

      // Assert
      verify(ledgerMemberMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("removeMember() should succeed when current user is ADMIN")
    void removeMember_AdminRemovesMember_Success() {
      // Arrange
      LedgerMember adminMember = new LedgerMember();
      adminMember.setLedgerId(testLedger.getId());
      adminMember.setUserId(testUser.getId());
      adminMember.setRole("ADMIN");

      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(adminMember) // Current user is ADMIN
          .thenReturn(otherUserMember); // Other user is present
      when(ledgerMemberMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

      // Act
      ledgerService.removeMember(testLedger.getId(), otherUser.getId());

      // Assert
      verify(ledgerMemberMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("removeMember() should throw exception if user is not authenticated")
    void removeMember_NoAuth() {
      // Arrange
      CurrentUserContext.clear();

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> ledgerService.removeMember(testLedger.getId(), otherUser.getId()));
      assertEquals("AUTH_REQUIRED", exception.getMessage());
      verify(ledgerMemberMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("removeMember() should throw exception if current user has insufficient role")
    void removeMember_InsufficientRole() {
      // Arrange
      LedgerMember editorMember = new LedgerMember();
      editorMember.setLedgerId(testLedger.getId());
      editorMember.setUserId(testUser.getId());
      editorMember.setRole("EDITOR");

      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(editorMember); // Current user is EDITOR

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> ledgerService.removeMember(testLedger.getId(), otherUser.getId()));
      assertEquals("ROLE_INSUFFICIENT: You do not have the required role", exception.getMessage());
      verify(ledgerMemberMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("removeMember() should throw exception when removing last OWNER")
    void removeMember_RemovingLastOwner_ThrowsException() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember); // Current user is OWNER
      when(ledgerMemberMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> ledgerService.removeMember(testLedger.getId(), testUser.getId()));
      assertEquals("CANNOT_REMOVE_LAST_OWNER", exception.getMessage());
      verify(ledgerMemberMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("removeMember() should succeed when removing self but not last OWNER")
    void removeMember_RemoveSelfNotLastOwner_Success() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember); // Current user is OWNER
      when(ledgerMemberMapper.selectCount(any(LambdaQueryWrapper.class)))
          .thenReturn(2L); // Two owners
      when(ledgerMemberMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

      // Act
      ledgerService.removeMember(testLedger.getId(), testUser.getId());

      // Assert
      verify(ledgerMemberMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("removeMember() tries to remove a non-existent member")
    void removeMember_nonExistentMember_doesNothing() {
      // Arrange
      when(ledgerMemberMapper.selectOne(any(LambdaQueryWrapper.class)))
          .thenReturn(testOwnerMember); // Current user is OWNER
      // Mock the delete call; it should return 0 for no deletions
      when(ledgerMemberMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

      // Act
      ledgerService.removeMember(testLedger.getId(), NON_EXISTENT_USER_ID);

      // Assert
      // Verify that delete was attempted but resulted in no change, which is fine.
      verify(ledgerMemberMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }
  }
}
