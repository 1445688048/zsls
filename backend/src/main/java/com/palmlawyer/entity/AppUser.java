// AppUser.java
// 用户实体类，映射 app_user 表
package com.palmlawyer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 *
 * <p>对应数据库表：app_user
 * <p>字段说明：
 * <ul>
 *   <li>user_id - 主键，自增</li>
 *   <li>openid - 微信 openid，唯一索引</li>
 *   <li>nickname - 用户昵称</li>
 *   <li>created_at - 创建时间</li>
 * </ul>
 */
@Data
@TableName("app_user")
public class AppUser {

    /** 用户 ID，自增主键 */
    @TableId(type = IdType.AUTO)
    private Long userId;

    /** 微信 OpenID，唯一标识用户 */
    @TableField("openid")
    private String openid;

    /** 用户昵称 */
    @TableField("nickname")
    private String nickname;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
