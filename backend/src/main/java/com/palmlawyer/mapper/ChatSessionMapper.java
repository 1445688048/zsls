// ChatSessionMapper.java
package com.palmlawyer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.palmlawyer.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper 接口
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
