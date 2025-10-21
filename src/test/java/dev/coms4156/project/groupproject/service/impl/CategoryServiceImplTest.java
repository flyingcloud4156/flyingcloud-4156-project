package dev.coms4156.project.groupproject.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.ListCategoriesResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Category;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.mapper.CategoryMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for CategoryServiceImpl.
 *
 * <p>Tests the CategoryService implementation with proper isolation using test doubles. Covers both
 * createCategory and listCategories methods with various scenarios including authentication,
 * authorization, validation, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryServiceImpl Unit Tests")
class CategoryServiceImplTest {

  @Mock private CategoryMapper categoryMapper;
  @Mock private LedgerMemberMapper ledgerMemberMapper;

  private CategoryServiceImpl categoryService;

  private static final Long TEST_LEDGER_ID = 1L;
  private static final Long TEST_USER_ID = 1L;
  private static final String TEST_CATEGORY_NAME = "Food";
  private static final String TEST_CATEGORY_KIND = "EXPENSE";
  private static final Integer TEST_SORT_ORDER = 1;

  @BeforeEach
  void setUp() {
    // Clear any existing user context
    CurrentUserContext.clear();

    // Create CategoryServiceImpl instance manually and spy on it
    categoryService = spy(new CategoryServiceImpl(ledgerMemberMapper));

    // Use reflection to set the baseMapper for MyBatis-Plus
    try {
      java.lang.reflect.Field baseMapperField =
          com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField(
              "baseMapper");
      baseMapperField.setAccessible(true);
      baseMapperField.set(categoryService, categoryMapper);
    } catch (Exception e) {
      // If reflection fails, we'll test without the baseMapper
      // This is acceptable for unit testing business logic
    }
  }

  @AfterEach
  void tearDown() {
    // Clean up user context after each test
    CurrentUserContext.clear();
  }

  @Nested
  @DisplayName("createCategory method tests")
  class CreateCategoryTests {

    @Test
    @DisplayName("Should create category successfully with valid input")
    void shouldCreateCategorySuccessfully() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      LedgerMember member = createTestLedgerMember("OWNER");
      CreateCategoryRequest request = createTestCreateCategoryRequest();

      when(ledgerMemberMapper.selectOne(any())).thenReturn(member);
      doReturn(0L).when(categoryService).count(any());
      doAnswer(
              invocation -> {
                Category category = invocation.getArgument(0);
                category.setId(1L); // Set ID after save
                return true;
              })
          .when(categoryService)
          .save(any(Category.class));

      // Act
      var result = categoryService.createCategory(TEST_LEDGER_ID, request);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getCategoryId());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).count(any());
      verify(categoryService).save(any(Category.class));
    }

    @Test
    @DisplayName("Should throw exception when user is not authenticated")
    void shouldThrowExceptionWhenUserNotAuthenticated() {
      // Arrange
      CurrentUserContext.clear(); // No user set
      CreateCategoryRequest request = createTestCreateCategoryRequest();

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> categoryService.createCategory(TEST_LEDGER_ID, request));

      assertEquals("AUTH_REQUIRED", exception.getMessage());

      // Verify no database interactions
      verifyNoInteractions(ledgerMemberMapper);
      verifyNoInteractions(categoryMapper);
    }

    @Test
    @DisplayName("Should throw exception when user is not a member")
    void shouldThrowExceptionWhenUserNotMember() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      CreateCategoryRequest request = createTestCreateCategoryRequest();

      when(ledgerMemberMapper.selectOne(any())).thenReturn(null);

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> categoryService.createCategory(TEST_LEDGER_ID, request));

      assertEquals("FORBIDDEN: You are not a member of this ledger", exception.getMessage());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verifyNoInteractions(categoryMapper);
    }

    @Test
    @DisplayName("Should throw exception when user has insufficient role")
    void shouldThrowExceptionWhenUserHasInsufficientRole() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      LedgerMember member = createTestLedgerMember("VIEWER"); // Insufficient role
      CreateCategoryRequest request = createTestCreateCategoryRequest();

      when(ledgerMemberMapper.selectOne(any())).thenReturn(member);

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> categoryService.createCategory(TEST_LEDGER_ID, request));

      assertEquals("ROLE_INSUFFICIENT: You do not have the required role", exception.getMessage());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verifyNoInteractions(categoryMapper);
    }

    @Test
    @DisplayName("Should throw exception when category name already exists")
    void shouldThrowExceptionWhenCategoryNameExists() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      LedgerMember member = createTestLedgerMember("OWNER");
      CreateCategoryRequest request = createTestCreateCategoryRequest();

      when(ledgerMemberMapper.selectOne(any())).thenReturn(member);
      doReturn(1L).when(categoryService).count(any()); // Name exists

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class,
              () -> categoryService.createCategory(TEST_LEDGER_ID, request));

      assertEquals("409 CONFLICT", exception.getMessage());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).count(any());
      verify(categoryService, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("Should allow ADMIN role to create category")
    void shouldAllowAdminRoleToCreateCategory() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      LedgerMember member = createTestLedgerMember("ADMIN");
      CreateCategoryRequest request = createTestCreateCategoryRequest();

      when(ledgerMemberMapper.selectOne(any())).thenReturn(member);
      doReturn(0L).when(categoryService).count(any());
      doAnswer(
              invocation -> {
                Category category = invocation.getArgument(0);
                category.setId(1L); // Set ID after save
                return true;
              })
          .when(categoryService)
          .save(any(Category.class));

      // Act
      var result = categoryService.createCategory(TEST_LEDGER_ID, request);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getCategoryId());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).count(any());
      verify(categoryService).save(any(Category.class));
    }

    @Test
    @DisplayName("Should allow EDITOR role to create category")
    void shouldAllowEditorRoleToCreateCategory() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      LedgerMember member = createTestLedgerMember("EDITOR");
      CreateCategoryRequest request = createTestCreateCategoryRequest();

      when(ledgerMemberMapper.selectOne(any())).thenReturn(member);
      doReturn(0L).when(categoryService).count(any());
      doAnswer(
              invocation -> {
                Category category = invocation.getArgument(0);
                category.setId(1L); // Set ID after save
                return true;
              })
          .when(categoryService)
          .save(any(Category.class));

      // Act
      var result = categoryService.createCategory(TEST_LEDGER_ID, request);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getCategoryId());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).count(any());
      verify(categoryService).save(any(Category.class));
    }
  }

  @Nested
  @DisplayName("listCategories method tests")
  class ListCategoriesTests {

    @Test
    @DisplayName("Should list all categories when active filter is null")
    void shouldListAllCategoriesWhenActiveFilterIsNull() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      List<Category> categories =
          Arrays.asList(
              createTestCategory(1L, "Food", "EXPENSE", true, 1),
              createTestCategory(2L, "Transport", "EXPENSE", false, 2));

      when(ledgerMemberMapper.selectOne(any())).thenReturn(createTestLedgerMember("OWNER"));
      doReturn(categories)
          .when(categoryService)
          .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

      // Act
      ListCategoriesResponse result = categoryService.listCategories(TEST_LEDGER_ID, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getItems());
      assertEquals(2, result.getItems().size());

      // Verify first category
      var firstItem = result.getItems().get(0);
      assertEquals(1L, firstItem.getCategoryId());
      assertEquals("Food", firstItem.getName());
      assertEquals("EXPENSE", firstItem.getKind());
      assertTrue(firstItem.getIsActive());
      assertEquals(1, firstItem.getSortOrder());

      // Verify second category
      var secondItem = result.getItems().get(1);
      assertEquals(2L, secondItem.getCategoryId());
      assertEquals("Transport", secondItem.getName());
      assertEquals("EXPENSE", secondItem.getKind());
      assertFalse(secondItem.getIsActive());
      assertEquals(2, secondItem.getSortOrder());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("Should list only active categories when active filter is true")
    void shouldListOnlyActiveCategoriesWhenActiveFilterIsTrue() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      List<Category> categories = Arrays.asList(createTestCategory(1L, "Food", "EXPENSE", true, 1));

      when(ledgerMemberMapper.selectOne(any())).thenReturn(createTestLedgerMember("OWNER"));
      doReturn(categories)
          .when(categoryService)
          .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

      // Act
      ListCategoriesResponse result = categoryService.listCategories(TEST_LEDGER_ID, true);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getItems());
      assertEquals(1, result.getItems().size());

      var item = result.getItems().get(0);
      assertEquals(1L, item.getCategoryId());
      assertEquals("Food", item.getName());
      assertTrue(item.getIsActive());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("Should list only inactive categories when active filter is false")
    void shouldListOnlyInactiveCategoriesWhenActiveFilterIsFalse() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      List<Category> categories =
          Arrays.asList(createTestCategory(2L, "Transport", "EXPENSE", false, 2));

      when(ledgerMemberMapper.selectOne(any())).thenReturn(createTestLedgerMember("OWNER"));
      doReturn(categories)
          .when(categoryService)
          .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

      // Act
      ListCategoriesResponse result = categoryService.listCategories(TEST_LEDGER_ID, false);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getItems());
      assertEquals(1, result.getItems().size());

      var item = result.getItems().get(0);
      assertEquals(2L, item.getCategoryId());
      assertEquals("Transport", item.getName());
      assertFalse(item.getIsActive());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("Should return empty list when no categories exist")
    void shouldReturnEmptyListWhenNoCategoriesExist() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      when(ledgerMemberMapper.selectOne(any())).thenReturn(createTestLedgerMember("OWNER"));
      doReturn(Collections.emptyList())
          .when(categoryService)
          .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

      // Act
      ListCategoriesResponse result = categoryService.listCategories(TEST_LEDGER_ID, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getItems());
      assertTrue(result.getItems().isEmpty());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("Should throw exception when user is not authenticated")
    void shouldThrowExceptionWhenUserNotAuthenticated() {
      // Arrange
      CurrentUserContext.clear(); // No user set

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> categoryService.listCategories(TEST_LEDGER_ID, null));

      assertEquals("AUTH_REQUIRED", exception.getMessage());

      // Verify no database interactions
      verifyNoInteractions(ledgerMemberMapper);
      verifyNoInteractions(categoryMapper);
    }

    @Test
    @DisplayName("Should throw exception when user is not a member")
    void shouldThrowExceptionWhenUserNotMember() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      when(ledgerMemberMapper.selectOne(any())).thenReturn(null);

      // Act & Assert
      RuntimeException exception =
          assertThrows(
              RuntimeException.class, () -> categoryService.listCategories(TEST_LEDGER_ID, null));

      assertEquals("FORBIDDEN: You are not a member of this ledger", exception.getMessage());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verifyNoInteractions(categoryMapper);
    }

    @Test
    @DisplayName("Should allow any member role to list categories")
    void shouldAllowAnyMemberRoleToListCategories() {
      // Arrange
      UserView currentUser = createTestUserView();
      CurrentUserContext.set(currentUser);

      List<Category> categories = Arrays.asList(createTestCategory(1L, "Food", "EXPENSE", true, 1));

      when(ledgerMemberMapper.selectOne(any())).thenReturn(createTestLedgerMember("VIEWER"));
      doReturn(categories)
          .when(categoryService)
          .list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));

      // Act
      ListCategoriesResponse result = categoryService.listCategories(TEST_LEDGER_ID, null);

      // Assert
      assertNotNull(result);
      assertNotNull(result.getItems());
      assertEquals(1, result.getItems().size());

      // Verify interactions
      verify(ledgerMemberMapper).selectOne(any());
      verify(categoryService).list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
  }

  // Test data factory methods
  private UserView createTestUserView() {
    UserView userView = new UserView();
    userView.setId(TEST_USER_ID);
    userView.setName("Test User");
    return userView;
  }

  private LedgerMember createTestLedgerMember(String role) {
    LedgerMember member = new LedgerMember();
    member.setLedgerId(TEST_LEDGER_ID);
    member.setUserId(TEST_USER_ID);
    member.setRole(role);
    return member;
  }

  private CreateCategoryRequest createTestCreateCategoryRequest() {
    CreateCategoryRequest request = new CreateCategoryRequest();
    request.setName(TEST_CATEGORY_NAME);
    request.setKind(TEST_CATEGORY_KIND);
    request.setSortOrder(TEST_SORT_ORDER);
    return request;
  }

  private Category createTestCategory(
      Long id, String name, String kind, Boolean isActive, Integer sortOrder) {
    Category category = new Category();
    category.setId(id);
    category.setLedgerId(TEST_LEDGER_ID);
    category.setName(name);
    category.setKind(kind);
    category.setIsActive(isActive);
    category.setSortOrder(sortOrder);
    return category;
  }
}
