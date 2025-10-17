package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.CategoryResponse;
import dev.coms4156.project.groupproject.dto.CreateCategoryRequest;
import dev.coms4156.project.groupproject.dto.ListCategoriesResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.Category;
import dev.coms4156.project.groupproject.entity.LedgerMember;
import dev.coms4156.project.groupproject.mapper.CategoryMapper;
import dev.coms4156.project.groupproject.mapper.LedgerMemberMapper;
import dev.coms4156.project.groupproject.service.CategoryService;
import dev.coms4156.project.groupproject.utils.AuthUtils;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final LedgerMemberMapper ledgerMemberMapper;

    @Autowired
    public CategoryServiceImpl(LedgerMemberMapper ledgerMemberMapper) {
        this.ledgerMemberMapper = ledgerMemberMapper;
    }

    @Override
    public CategoryResponse createCategory(Long ledgerId, CreateCategoryRequest req) {
        UserView currentUser = CurrentUserContext.get();
        if (currentUser == null) {
            throw new RuntimeException("AUTH_REQUIRED");
        }

        LedgerMember member = getLedgerMember(ledgerId, currentUser.getId());
        AuthUtils.A(member, "OWNER", "ADMIN", "EDITOR");

        long count = count(new LambdaQueryWrapper<Category>()
                .eq(Category::getLedgerId, ledgerId)
                .eq(Category::getName, req.getName()));
        if (count > 0) {
            throw new RuntimeException("409 CONFLICT");
        }

        Category category = new Category();
        category.setLedgerId(ledgerId);
        category.setName(req.getName());
        category.setKind(req.getKind());
        category.setSortOrder(req.getSortOrder());
        category.setIsActive(true);
        save(category);

        return new CategoryResponse(category.getId());
    }

    @Override
    public ListCategoriesResponse listCategories(Long ledgerId, Boolean active) {
        UserView currentUser = CurrentUserContext.get();
        if (currentUser == null) {
            throw new RuntimeException("AUTH_REQUIRED");
        }

        AuthUtils.B(isMember(ledgerId, currentUser.getId()));

        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getLedgerId, ledgerId);
        if (active != null) {
            queryWrapper.eq(Category::getIsActive, active);
        }

        List<Category> categories = list(queryWrapper);

        List<ListCategoriesResponse.CategoryItem> items = categories.stream().map(c ->
                new ListCategoriesResponse.CategoryItem(c.getId(), c.getName(), c.getKind(), c.getIsActive(), c.getSortOrder()))
                .collect(Collectors.toList());

        return new ListCategoriesResponse(items);
    }

    private LedgerMember getLedgerMember(Long ledgerId, Long userId) {
        return ledgerMemberMapper.selectOne(
                new LambdaQueryWrapper<LedgerMember>()
                        .eq(LedgerMember::getLedgerId, ledgerId)
                        .eq(LedgerMember::getUserId, userId));
    }

    private boolean isMember(Long ledgerId, Long userId) {
        return getLedgerMember(ledgerId, userId) != null;
    }
}
