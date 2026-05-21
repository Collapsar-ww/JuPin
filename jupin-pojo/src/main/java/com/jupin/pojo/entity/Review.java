package com.jupin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long poolId;
    private Long fromUserId;        // 评价人
    private Long targetId;          // type=0存shop_id, type=1存dm_user_id
    private Integer type;           // 0-评价店家 1-评价DM
    private Integer score;          // 1-5
    private String content;
    private String tags;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
