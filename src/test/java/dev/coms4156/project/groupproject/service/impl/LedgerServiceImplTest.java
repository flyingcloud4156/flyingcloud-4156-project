package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.coms4156.project.groupproject.dto.CreateLedgerRequest;
import dev.coms4156.project.groupproject.dto.LedgerResponse;
import dev.coms4156.project.groupproject.dto.MyLedgersResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Ledger;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * isolation.
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceImplTest {

  @Mock private LedgerMemberMapper ledgerMemberMapper;

  @Mock private UserMapper userMapper;

  // Using @Spy so we can mock the `save` method of the ServiceImpl base class
  @Spy @InjectMocks private LedgerServiceImpl ledgerService;

  private UserView testUser;

  @BeforeEach
  void setUp() {
    testUser = new UserView(1L, "testuser");
    CurrentUserContext.set(testUser);
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  @DisplayName("createLedger() should succeed for an authenticated user")
  void testCreateLedger_Success() {
    // Arrange
    CreateLedgerRequest request = new CreateLedgerRequest();
    request.setName("Test Ledger");
    request.setLedgerType("Personal");
    request.setBaseCurrency("USD");
    request.setShareStartDate(LocalDate.now());
    ArgumentCaptor<Ledger> ledgerCaptor = ArgumentCaptor.forClass(Ledger.class);
    ArgumentCaptor<LedgerMember> memberCaptor = ArgumentCaptor.forClass(LedgerMember.class);

    // Mock the behavior of the inherited `save` method
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

    // Verify
    verify(ledgerService).save(any(Ledger.class));
    verify(ledgerMemberMapper).insert(any(LedgerMember.class));
  }

  @Test
  @DisplayName("createLedger() should throw exception if user is not authenticated")
  void testCreateLedger_NoAuth() {
    // Arrange
    CurrentUserContext.clear(); // Ensure no user is in context
    CreateLedgerRequest request = new CreateLedgerRequest();

    // Act & Assert
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> ledgerService.createLedger(request));

    assertEquals("AUTH_REQUIRED", exception.getMessage());
  }

  @Test
  @DisplayName("getMyLedgers() should return ledgers for an authenticated user")
  void testGetMyLedgers_Success() {
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
  void testGetMyLedgers_NoMemberships() {
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
  void testGetMyLedgers_NoAuth() {
    // Arrange
    CurrentUserContext.clear();

    // Act & Assert
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> ledgerService.getMyLedgers());

    assertEquals("AUTH_REQUIRED", exception.getMessage());
  }
}
