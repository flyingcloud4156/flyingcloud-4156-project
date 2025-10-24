package dev.coms4156.project.groupproject.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.coms4156.project.groupproject.entity.DebtEdge;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** MyBatis mapper for DebtEdge entity. Provides database operations for debt edges. */
@Mapper
public interface DebtEdgeMapper extends BaseMapper<DebtEdge> {

  /**
   * Find debt edges by transaction ID.
   *
   * @param transactionId transaction ID
   * @return list of debt edges for the transaction
   */
  List<DebtEdge> findByTransactionId(@Param("transactionId") Long transactionId);

  /**
   * Delete debt edges by transaction ID.
   *
   * @param transactionId transaction ID
   * @return number of deleted edges
   */
  int deleteByTransactionId(@Param("transactionId") Long transactionId);

  /**
   * Insert multiple debt edges in batch.
   *
   * @param edges list of debt edges to insert
   * @return number of inserted edges
   */
  int insertBatch(@Param("edges") List<DebtEdge> edges);

  /**
   * Find debt edges by ledger ID.
   *
   * @param ledgerId ledger ID
   * @return list of debt edges for the ledger
   */
  List<DebtEdge> findByLedgerId(@Param("ledgerId") Long ledgerId);
}
