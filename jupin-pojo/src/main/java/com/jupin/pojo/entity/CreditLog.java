package com.jupin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信用分变更日志表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("credit_log")
public class CreditLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;            // 用户 ID
    private Integer change;         // 变动值（正加负减，如 -20、+5）
    private Integer balance;        // 变动后余额
    private String reason;          // 原因：跳车扣分/完成加分/好评加分/差评扣分

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
