package dev.coms4156.project.groupproject.controller;

import dev.coms4156.project.groupproject.dto.*;
import dev.coms4156.project.groupproject.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledgers/{ledgerId}/categories")
@Tag(name = "Category APIs")
@SecurityRequirement(name = "X-Auth-Token")
public class CategoryController {

  private final CategoryService categoryService;

  @Autowired
  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a new category")
  public Result<CategoryResponse> createCategory(
      @PathVariable Long ledgerId, @Valid @RequestBody CreateCategoryRequest req) {
    return Result.ok(categoryService.createCategory(ledgerId, req));
  }

  @GetMapping
  @Operation(summary = "List categories")
  public Result<ListCategoriesResponse> listCategories(
      @PathVariable Long ledgerId, @RequestParam(required = false) Boolean active) {
    return Result.ok(categoryService.listCategories(ledgerId, active));
  }
}
