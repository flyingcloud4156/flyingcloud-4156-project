package dev.coms4156.project.groupproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.coms4156.project.groupproject.entity.Budget;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Mapper for the Budget entity. */
@Mapper
public interface BudgetMapper extends BaseMapper<Budget> {

  /**
   * Find all budgets for a given ledger, year, and month.
   *
   * @param ledgerId ledger ID
   * @param year year
   * @param month month (1-12)
   * @return list of budgets
   */
  @Select(
      "SELECT * FROM budgets "
          + "WHERE ledger_id = #{ledgerId} "
          + "  AND year = #{year} "
          + "  AND month = #{month}")
  List<Budget> findByLedgerAndYearMonth(
      @Param("ledgerId") Long ledgerId, @Param("year") Integer year, @Param("month") Integer month);
}
