package dev.coms4156.project.groupproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.coms4156.project.groupproject.dto.CategoryResponse;
import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.ListCategoriesResponse;
import dev.coms4156.project.groupproject.entity.Category;

public interface CategoryService extends IService<Category> {
  CategoryResponse createCategory(Long ledgerId, CreateCategoryRequest req);

  ListCategoriesResponse listCategories(Long ledgerId, Boolean active);
}
