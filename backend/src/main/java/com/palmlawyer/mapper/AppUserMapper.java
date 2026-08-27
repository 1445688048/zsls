// AppUserMapper.java
// 用户 Mapper 接口，继承 MyBatis-Plus BaseMapper
package com.palmlawyer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.palmlawyer.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 用户 Mapper 接口
 *
 * <p>提供基础 CRUD 操作，以及自定义查询方法。
 * <p>MyBatis-Plus 已内置常用方法，无需手写 SQL。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * 根据 openid 查询用户
     *
     * @param openid 微信 openid
     * @return 用户对象，不存在返回 null
     */
    @Select("SELECT * FROM app_user WHERE openid = #{openid}")
    Optional<AppUser> findByOpenid(@Param("openid") String openid);
}
