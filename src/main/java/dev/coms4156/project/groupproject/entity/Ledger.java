package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ledgers")
public class Ledger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long ownerId;
    private String ledgerType;
    private String baseCurrency;
    private LocalDate shareStartDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
