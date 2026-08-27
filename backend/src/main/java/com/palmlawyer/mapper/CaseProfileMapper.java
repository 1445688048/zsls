// CaseProfileMapper.java
// 案件档案 Mapper 接口
package com.palmlawyer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.palmlawyer.entity.CaseProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 案件档案 Mapper 接口
 *
 * <p>继承 BaseMapper，自动获得 CRUD 方法：
 * <ul>
 *   <li>insert - 插入</li>
 *   <li>deleteById - 按 ID 删除</li>
 *   <li>updateById - 按 ID 更新</li>
 *   <li>selectById - 按 ID 查询</li>
 *   <li>selectList - 条件查询</li>
 * </ul>
 */
@Mapper
public interface CaseProfileMapper extends BaseMapper<CaseProfile> {
}
