package dev.coms4156.project.groupproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** User entity mapping table 'users'. */
@Data
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String passwordHash;
    private String timezone;
    private String mainCurrency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
