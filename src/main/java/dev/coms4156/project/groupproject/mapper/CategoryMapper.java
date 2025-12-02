package dev.coms4156.project.groupproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.coms4156.project.groupproject.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis mapper interface for Category entity operations. Provides CRUD operations and basic
 * database access for categories.
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {}
