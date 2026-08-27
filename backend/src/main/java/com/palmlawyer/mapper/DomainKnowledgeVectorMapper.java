// DomainKnowledgeVectorMapper.java
package com.palmlawyer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.palmlawyer.entity.DomainKnowledgeVector;
import org.apache.ibatis.annotations.Mapper;

/**
 * 领域知识向量 Mapper 接口
 * <p>生产环境使用，MVP 阶段暂不启用
 */
@Mapper
public interface DomainKnowledgeVectorMapper extends BaseMapper<DomainKnowledgeVector> {
}
