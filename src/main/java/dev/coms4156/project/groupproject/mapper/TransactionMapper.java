package dev.coms4156.project.groupproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.coms4156.project.groupproject.entity.Transaction;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for Transaction entity. Provides database operations for transactions. */
@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {

  /**
   * Find transactions by ledger ID with pagination and filtering.
   *
   * @param page pagination parameters
   * @param ledgerId ledger ID
   * @param fromDate start date filter (inclusive)
   * @param toDate end date filter (exclusive)
   * @param type transaction type filter
   * @param createdBy created by user ID filter
   * @param categoryId category ID filter
   * @param currentUserId current user ID for visibility filtering
   * @return paginated transactions
   */
  IPage<Transaction> findTransactionsByLedger(
      Page<Transaction> page,
      @Param("ledgerId") Long ledgerId,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      @Param("type") String type,
      @Param("createdBy") Long createdBy,
      @Param("categoryId") Long categoryId,
      @Param("currentUserId") Long currentUserId);

  /**
   * Find transaction by ID with visibility check.
   *
   * @param transactionId transaction ID
   * @param currentUserId current user ID for visibility filtering
   * @return transaction if found and visible, null otherwise
   */
  Transaction findTransactionByIdWithVisibility(
      @Param("transactionId") Long transactionId, @Param("currentUserId") Long currentUserId);

  /**
   * Sum expense amounts in a given time range, optionally filtered by category.
   *
   * @param ledgerId ledger ID
   * @param categoryId category ID (null for all categories)
   * @param start start time (inclusive)
   * @param end end time (exclusive)
   * @return total expense amount
   */
  java.math.BigDecimal sumExpenseByTimeRange(
      @Param("ledgerId") Long ledgerId,
      @Param("categoryId") Long categoryId,
      @Param("start") java.time.LocalDateTime start,
      @Param("end") java.time.LocalDateTime end);
}
