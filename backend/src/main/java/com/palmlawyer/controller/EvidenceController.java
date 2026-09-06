// EvidenceController.java
// 证据管理：文件上传落盘 + evidenceRefs 回写（属主校验）
package com.palmlawyer.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.mapper.CaseProfileMapper;
import com.palmlawyer.service.CaseGuard;
import com.palmlawyer.service.CaseStatusService;
import com.palmlawyer.util.AuthContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 证据控制器
 *
 * <p>文件上传到存储目录/{caseId}/，元信息追加写入 case_profile.evidence_refs。
 */
@Slf4j
@RestController
@RequestMapping("/cases/{caseId}/evidence")
public class EvidenceController {

    private final CaseProfileMapper caseMapper;
    private final CaseGuard caseGuard;
    private final CaseStatusService caseStatusService;

    /** 只更新 evidence_refs 列时需显式指定 TypeHandler（实体注解仅在整实体模式生效） */
    private static final String JSON_TYPE_HANDLER =
            "typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler";

    @Value("${palmlawyer.storage.local-path}")
    private String storagePath;

    @Value("${palmlawyer.storage.url-prefix}")
    private String urlPrefix;

    public EvidenceController(CaseProfileMapper caseMapper, CaseGuard caseGuard, CaseStatusService caseStatusService) {
        this.caseMapper = caseMapper;
        this.caseGuard = caseGuard;
        this.caseStatusService = caseStatusService;
    }

    /**
     * 上传证据文件
     */
    @PostMapping(consumes = "multipart/form-data")
    public Map<String, Object> upload(@PathVariable Long caseId, @RequestParam("file") MultipartFile file) {
        Long userId = AuthContext.userId();
        caseGuard.assertOwner(caseId, userId);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }

        String refId = UUID.randomUUID().toString();
        String safeName = sanitize(file.getOriginalFilename());
        String storedName = refId + "_" + safeName;
        try {
            Path dir = Paths.get(storagePath, String.valueOf(caseId)).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(storedName).toFile());
        } catch (IOException e) {
            log.error("证据文件保存失败: caseId={}", caseId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败");
        }

        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("refId", refId);
        ref.put("name", safeName);
        ref.put("fileType", extensionOf(safeName));
        ref.put("size", file.getSize());
        ref.put("collected", false);
        ref.put("uploadedAt", LocalDateTime.now().toString());
        ref.put("url", urlPrefix + "/" + caseId + "/" + storedName);

        CaseProfile c = caseMapper.selectById(caseId);
        List<Map<String, Object>> refs = new ArrayList<>(c.getEvidenceRefs() != null ? c.getEvidenceRefs() : new ArrayList<>());
        refs.add(ref);
        saveRefs(caseId, refs);
        caseStatusService.reevaluate(caseId);
        log.info("证据上传: caseId={}, refId={}, file={}", caseId, refId, safeName);
        return ref;
    }

    /**
     * 更新证据（collected 等字段；refId 不存在则按名称创建，兼容前端预设清单项）
     */
    @PutMapping("/{refId}")
    public Map<String, Object> update(@PathVariable Long caseId, @PathVariable String refId, @RequestBody Map<String, Object> body) {
        Long userId = AuthContext.userId();
        caseGuard.assertOwner(caseId, userId);
        CaseProfile c = caseMapper.selectById(caseId);
        List<Map<String, Object>> refs = new ArrayList<>(c.getEvidenceRefs() != null ? c.getEvidenceRefs() : new ArrayList<>());

        Map<String, Object> target = null;
        for (Map<String, Object> r : refs) {
            if (refId.equals(r.get("refId"))) {
                target = r;
                break;
            }
        }
        boolean isNew = target == null;
        if (isNew) {
            target = new LinkedHashMap<>();
            target.put("refId", refId);
            target.put("name", String.valueOf(body.getOrDefault("name", refId)));
            target.put("fileType", "DOCUMENT");
            target.put("size", 0);
            target.put("uploadedAt", LocalDateTime.now().toString());
            refs.add(target);
        }
        if (body.containsKey("collected")) target.put("collected", Boolean.TRUE.equals(body.get("collected")));
        if (body.containsKey("name")) target.put("name", String.valueOf(body.get("name")));

        saveRefs(caseId, refs);
        caseStatusService.reevaluate(caseId);
        return target;
    }

    /**
     * 删除证据（同时从 refs 移除）
     */
    @DeleteMapping("/{refId}")
    public Map<String, Object> delete(@PathVariable Long caseId, @PathVariable String refId) {
        Long userId = AuthContext.userId();
        caseGuard.assertOwner(caseId, userId);
        CaseProfile c = caseMapper.selectById(caseId);
        List<Map<String, Object>> refs = new ArrayList<>(c.getEvidenceRefs() != null ? c.getEvidenceRefs() : new ArrayList<>());
        refs.removeIf(r -> refId.equals(r.get("refId")));
        saveRefs(caseId, refs);
        caseStatusService.reevaluate(caseId);
        log.info("证据删除: caseId={}, refId={}", caseId, refId);
        return Map.of("msg", "deleted", "refId", refId);
    }

    /** 只更新 evidence_refs 与 updated_at 两列，避免整实体回写覆盖 facts/laws 等并发写入的列 */
    private void saveRefs(Long caseId, List<Map<String, Object>> refs) {
        caseMapper.update(null, new LambdaUpdateWrapper<CaseProfile>()
                .eq(CaseProfile::getCaseId, caseId)
                .set(CaseProfile::getEvidenceRefs, refs, JSON_TYPE_HANDLER)
                .set(CaseProfile::getUpdatedAt, LocalDateTime.now()));
    }

    /** 清洗文件名：仅保留文件名，禁止路径分隔符与 .. */
    private String sanitize(String filename) {
        if (filename == null) return "file";
        String name = filename.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) name = name.substring(idx + 1);
        name = name.replaceAll("[^\\w.-]", "_").replaceAll("[.]{2,}", "_");
        return name.isBlank() ? "file" : name;
    }

    private String extensionOf(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : "unknown";
    }
}
