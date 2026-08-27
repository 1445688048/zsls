// TimelineService.java
// 时间轴服务层：独立 timeline_event 表存储（替代 timeline_json 整条读写，消除并发覆盖）
package com.palmlawyer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.palmlawyer.entity.TimelineEvent;
import com.palmlawyer.mapper.TimelineEventMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 时间轴服务层
 *
 * <p>事件存独立表 timeline_event，事件 ID 使用 UUID（弃用毫秒时间戳，避免同毫秒冲突）。
 * <p>对外 JSON 字段名与旧实现保持一致：eventId/eventTime/description/type/isDeadline/reminderText。
 */
@Slf4j
@Service
public class TimelineService {

    private final TimelineEventMapper eventMapper;

    public TimelineService(TimelineEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    /**
     * 添加时间轴事件
     */
    public TimelineEvent addEvent(Long caseId, Map<String, Object> event) {
        TimelineEvent e = new TimelineEvent();
        e.setEventId(UUID.randomUUID().toString());
        e.setCaseId(caseId);
        e.setEventTime(str(event.get("eventTime")));
        e.setDescription(str(event.get("description")));
        e.setType(str(event.get("type")) == null ? "USER_ACTION" : str(event.get("type")));
        e.setIsDeadline(Boolean.TRUE.equals(event.get("isDeadline")));
        e.setReminderText(str(event.get("reminderText")));
        eventMapper.insert(e);
        log.info("添加时间轴事件: caseId={}, eventId={}", caseId, e.getEventId());
        return e;
    }

    /**
     * 查询案件时间轴（按事件时间升序）
     */
    public List<TimelineEvent> list(Long caseId) {
        return eventMapper.selectList(new LambdaQueryWrapper<TimelineEvent>()
                .eq(TimelineEvent::getCaseId, caseId)
                .orderByAsc(TimelineEvent::getEventTime));
    }

    /**
     * 更新时间轴事件
     */
    public TimelineEvent update(Long caseId, String eventId, Map<String, Object> event) {
        TimelineEvent e = getOwned(caseId, eventId);
        if (event.containsKey("eventTime")) e.setEventTime(str(event.get("eventTime")));
        if (event.containsKey("description")) e.setDescription(str(event.get("description")));
        if (event.containsKey("type")) e.setType(str(event.get("type")));
        if (event.containsKey("isDeadline")) e.setIsDeadline(Boolean.TRUE.equals(event.get("isDeadline")));
        if (event.containsKey("reminderText")) e.setReminderText(str(event.get("reminderText")));
        eventMapper.updateById(e);
        return e;
    }

    /**
     * 删除时间轴事件
     */
    public void delete(Long caseId, String eventId) {
        getOwned(caseId, eventId);
        eventMapper.deleteById(eventId);
        log.info("删除时间轴事件: caseId={}, eventId={}", caseId, eventId);
    }

    private TimelineEvent getOwned(Long caseId, String eventId) {
        TimelineEvent e = eventMapper.selectById(eventId);
        if (e == null || !e.getCaseId().equals(caseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "事件不存在");
        }
        return e;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}