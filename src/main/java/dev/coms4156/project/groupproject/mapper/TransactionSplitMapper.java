package dev.coms4156.project.groupproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.coms4156.project.groupproject.entity.TransactionSplit;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for TransactionSplit entity. Provides database operations for transaction splits.
 */
@Mapper
public interface TransactionSplitMapper extends BaseMapper<TransactionSplit> {

  /**
   * Find splits by transaction ID.
   *
   * @param transactionId transaction ID
   * @return list of splits for the transaction
   */
  List<TransactionSplit> findByTransactionId(@Param("transactionId") Long transactionId);

  /**
   * Delete splits by transaction ID.
   *
   * @param transactionId transaction ID
   * @return number of deleted splits
   */
  int deleteByTransactionId(@Param("transactionId") Long transactionId);

  /**
   * Insert multiple splits in batch.
   *
   * @param splits list of splits to insert
   * @return number of inserted splits
   */
  int insertBatch(@Param("splits") List<TransactionSplit> splits);
}
