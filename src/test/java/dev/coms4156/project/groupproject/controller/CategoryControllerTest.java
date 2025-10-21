package dev.coms4156.project.groupproject.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.coms4156.project.groupproject.dto.CategoryResponse;
import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.ListCategoriesResponse;
import dev.coms4156.project.groupproject.service.CategoryService;
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
class CategoryControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @Mock private CategoryService categoryService;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(categoryService)).build();
  }

  @Test
  @DisplayName("POST /api/v1/ledgers/{ledgerId}/categories -> 201 and body")
  void createCategory_returnsCreated() throws Exception {
    Long ledgerId = 1L;
    CreateCategoryRequest req = new CreateCategoryRequest();
    req.setName("Food");
    req.setKind("EXPENSE");
    req.setSortOrder(1);

    given(categoryService.createCategory(eq(ledgerId), any(CreateCategoryRequest.class)))
        .willReturn(new CategoryResponse(100L));

    mockMvc
        .perform(
            post("/api/v1/ledgers/{ledgerId}/categories", ledgerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.categoryId").value(100));
  }

  @Test
  @DisplayName("GET /api/v1/ledgers/{ledgerId}/categories -> 200 and list")
  void listCategories_returnsOk() throws Exception {
    Long ledgerId = 1L;
    ListCategoriesResponse.CategoryItem item =
        new ListCategoriesResponse.CategoryItem(10L, "Food", "EXPENSE", true, 1);
    given(categoryService.listCategories(eq(ledgerId), eq(true)))
        .willReturn(new ListCategoriesResponse(List.of(item)));

    mockMvc
        .perform(get("/api/v1/ledgers/{ledgerId}/categories?active=true", ledgerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].categoryId").value(10))
        .andExpect(jsonPath("$.data.items[0].name").value("Food"));
  }
}
