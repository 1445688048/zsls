// TimelineController.java
// 时间轴控制器（案件属主校验）
package com.palmlawyer.controller;

import com.palmlawyer.entity.TimelineEvent;
import com.palmlawyer.service.CaseGuard;
import com.palmlawyer.service.TimelineService;
import com.palmlawyer.util.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 时间轴控制器
 *
 * <p>提供时间轴事件的 CRUD 接口。
 */
@RestController
@RequestMapping("/cases/{caseId}/timeline")
public class TimelineController {

    private final TimelineService timelineService;
    private final CaseGuard caseGuard;

    public TimelineController(TimelineService timelineService, CaseGuard caseGuard) {
        this.timelineService = timelineService;
        this.caseGuard = caseGuard;
    }

    /**
     * 添加事件
     */
    @PostMapping
    public TimelineEvent addEvent(@PathVariable Long caseId, @RequestBody Map<String, Object> event) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        return timelineService.addEvent(caseId, event);
    }

    /**
     * 查询事件列表
     */
    @GetMapping
    public List<TimelineEvent> list(@PathVariable Long caseId) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        return timelineService.list(caseId);
    }

    /**
     * 更新事件
     */
    @PutMapping("/{eventId}")
    public TimelineEvent update(@PathVariable Long caseId, @PathVariable String eventId, @RequestBody Map<String, Object> event) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        return timelineService.update(caseId, eventId, event);
    }

    /**
     * 删除事件
     */
    @DeleteMapping("/{eventId}")
    public Map<String, Object> delete(@PathVariable Long caseId, @PathVariable String eventId) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        timelineService.delete(caseId, eventId);
        return Map.of("msg", "deleted", "eventId", eventId);
    }
}
