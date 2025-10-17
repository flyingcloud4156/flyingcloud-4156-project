package dev.coms4156.project.groupproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListCategoriesResponse {
    private List<CategoryItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryItem {
        private Long categoryId;
        private String name;
        private String kind;
        private Boolean isActive;
        private Integer sortOrder;
    }
}
