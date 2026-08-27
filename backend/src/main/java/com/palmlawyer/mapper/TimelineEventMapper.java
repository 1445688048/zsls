// TimelineEventMapper.java
package com.palmlawyer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.palmlawyer.entity.TimelineEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TimelineEventMapper extends BaseMapper<TimelineEvent> {
}