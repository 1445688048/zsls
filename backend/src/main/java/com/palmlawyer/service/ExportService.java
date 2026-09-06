// ExportService.java
// 导出服务：openhtmltopdf 生成法律意见书 PDF（含脱敏、中文渲染）
package com.palmlawyer.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.entity.TimelineEvent;
import com.palmlawyer.mapper.CaseProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExportService {

    private static final String FONT_FAMILY = "ExportCJK";
    private static final String FONT_TEMP_PREFIX = "export-font-";

    private final CaseProfileMapper caseMapper;
    private final TimelineService timelineService;

    @Value("${palmlawyer.storage.local-path}")
    private String storagePath;

    @Value("${palmlawyer.storage.url-prefix}")
    private String urlPrefix;

    /** 可选外部字体路径（如不便放入 classpath 时配置） */
    @Value("${palmlawyer.storage.font-path:}")
    private String fontPath;

    public ExportService(CaseProfileMapper caseMapper, TimelineService timelineService) {
        this.caseMapper = caseMapper;
        this.timelineService = timelineService;
    }

    public Map<String, Object> generatePdf(Long caseId, boolean redact) {
        CaseProfile caseProfile = caseMapper.selectById(caseId);
        if (caseProfile == null) {
            throw new IllegalArgumentException("案件不存在: " + caseId);
        }

        String html = renderHtml(caseProfile, redact);
        String fileName = "opinion-" + System.currentTimeMillis() + ".pdf";
        Path dir = Paths.get(storagePath, String.valueOf(caseId)).toAbsolutePath().normalize();
        Path fontTemp = null;
        try {
            Files.createDirectories(dir);
            Path out = dir.resolve(fileName);
            try (OutputStream os = Files.newOutputStream(out)) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                fontTemp = registerFont(builder);
                builder.withHtmlContent(html, null);
                builder.toStream(os);
                builder.run();
            }
            log.info("PDF 已生成: caseId={}, file={}, size={}", caseId, fileName, Files.size(out));
        } catch (Exception e) {
            log.error("PDF 生成失败: caseId={}", caseId, e);
            throw new IllegalStateException("PDF 生成失败: " + e.getMessage(), e);
        } finally {
            // classpath 字体被复制到临时文件使用，渲染结束后立即清理，避免每次导出泄漏一个文件
            if (fontTemp != null) {
                try {
                    Files.deleteIfExists(fontTemp);
                } catch (Exception cleanupError) {
                    log.debug("字体临时文件清理失败: {}", fontTemp, cleanupError);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", caseId);
        result.put("title", caseProfile.getTitle());
        result.put("redact", redact);
        result.put("downloadUrl", urlPrefix + "/" + caseId + "/" + fileName);
        result.put("message", "PDF 已生成");
        return result;
    }

    /**
     * 注册中文字体，返回本次注册使用的字体文件路径（若为本次生成的临时文件，调用方负责删除）。
     * 优先级：
     * 1) 配置的外部字体路径（palmlawyer.storage.font-path）；
     * 2) classpath:/fonts/ 下的第一个 ttf（复制到临时文件）；
     * 3) Windows 系统 msyh.ttc（仅本机开发可用，生产请放置字体文件）。
     */
    private Path registerFont(PdfRendererBuilder builder) throws Exception {
        Path external = fontPath == null || fontPath.isBlank() ? null : Paths.get(fontPath);
        if (external != null && Files.exists(external)) {
            useFont(builder, external);
            return null;
        }
        try {
            var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
            var res = resolver.getResources("classpath:fonts/*.ttf");
            if (res.length > 0) {
                Path tmp = Files.createTempFile(FONT_TEMP_PREFIX, ".ttf");
                Files.copy(res[0].getInputStream(), tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                useFont(builder, tmp);
                return tmp;
            }
        } catch (Exception e) {
            log.debug("classpath fonts 扫描失败: {}", e.getMessage());
        }
        // 本机开发兜底：Windows 自带 Arial Unicode MS（含 CJK，TTF 格式，仅本机使用，不随项目分发）
        Path arialuni = Paths.get("C:/Windows/Fonts/arialuni.ttf");
        if (Files.exists(arialuni)) {
            useFont(builder, arialuni);
            return null;
        }
        throw new IllegalStateException("未找到可用的中文字体：请将 TTF 放入 backend/src/main/resources/fonts/ 或配置 palmlawyer.storage.font-path");
    }

    private void useFont(PdfRendererBuilder builder, Path fontFile) {
        builder.useFont(() -> {
            try {
                return Files.newInputStream(fontFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, FONT_FAMILY);
        log.info("PDF 使用字体: {}", fontFile);
    }

    /** 渲染 HTML（转义 + 可选脱敏） */
    private String renderHtml(CaseProfile c, boolean redact) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"utf-8\"/><style>")
          .append("body{font-family:'").append(FONT_FAMILY).append("',sans-serif;font-size:13px;line-height:1.7;color:#222;margin:32px;}")
          .append("h1{font-size:20px;text-align:center;} h2{font-size:15px;border-bottom:1px solid #ccc;padding-bottom:4px;}")
          .append("table{border-collapse:collapse;width:100%;margin:8px 0;} td,th{border:1px solid #ddd;padding:6px 8px;font-size:12px;}")
          .append(".muted{color:#888;font-size:11px;}")
          .append("</style></head><body>");

        sb.append("<h1>法律意见书</h1>");
        sb.append("<p class=\"muted\">案件编号：").append(esc(String.valueOf(c.getCaseId()))).append("　生成时间：").append(java.time.LocalDateTime.now()).append("</p>");
        sb.append("<h2>案件信息</h2>");
        sb.append("<table><tr><th>标题</th><td>").append(esc(c.getTitle())).append("</td></tr>")
          .append("<tr><th>领域</th><td>").append(esc(c.getDomainType())).append("</td></tr>")
          .append("<tr><th>状态</th><td>").append(esc(c.getStatus())).append("</td></tr></table>");

        sb.append("<h2>案件事实</h2>");
        Map<String, Object> facts = c.getFactsJson();
        if (facts == null || facts.isEmpty()) {
            sb.append("<p>暂无事实信息</p>");
        } else {
            sb.append("<table>");
            facts.forEach((k, v) -> {
                String val = v instanceof Map ? String.valueOf(((Map<?, ?>) v).get("value")) : String.valueOf(v);
                sb.append("<tr><th>").append(esc(k)).append("</th><td>").append(esc(redact ? maskSensitive(val) : val)).append("</td></tr>");
            });
            sb.append("</table>");
        }

        sb.append("<h2>时间轴</h2>");
        // 时间轴事件存独立 timeline_event 表（timelineJson 字段已废弃），从 TimelineService 读取
        List<TimelineEvent> timeline = timelineService.list(c.getCaseId());
        if (timeline == null || timeline.isEmpty()) {
            sb.append("<p>暂无时间轴事件</p>");
        } else {
            sb.append("<table><tr><th>时间</th><th>事件</th></tr>");
            for (TimelineEvent ev : timeline) {
                sb.append("<tr><td>").append(esc(String.valueOf(ev.getEventTime() == null ? "" : ev.getEventTime())))
                  .append("</td><td>").append(esc(String.valueOf(ev.getDescription() == null ? "" : ev.getDescription()))).append("</td></tr>");
            }
            sb.append("</table>");
        }

        sb.append("<p class=\"muted\">⚠️ 本文件由 AI 自动生成，仅供参考，不构成法律意见。</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    /** 脱敏：手机号与身份证 */
    private String maskSensitive(String s) {
        if (s == null) return "";
        return s.replaceAll("1\\d{10}", "138****0000")
                .replaceAll("\\d{17}[0-9Xx]", "***************X");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}