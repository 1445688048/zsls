// ExportController.java
// 导出控制器，提供 PDF 导出接口（案件属主校验）
package com.palmlawyer.controller;

import com.palmlawyer.service.CaseGuard;
import com.palmlawyer.service.ExportService;
import com.palmlawyer.util.AuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 导出控制器
 *
 * <p>提供法律意见书的 PDF 导出接口。
 */
@RestController
@RequestMapping("/cases/{caseId}/export")
public class ExportController {

    private final ExportService exportService;
    private final CaseGuard caseGuard;

    public ExportController(ExportService exportService, CaseGuard caseGuard) {
        this.exportService = exportService;
        this.caseGuard = caseGuard;
    }

    /**
     * 生成法律意见书 PDF
     *
     * @param caseId 案件 ID
     * @param redact 是否脱敏
     * @return 导出结果
     */
    @PostMapping("/pdf")
    public Map<String, Object> exportPdf(@PathVariable Long caseId, @RequestParam(defaultValue = "false") boolean redact) {
        caseGuard.assertOwner(caseId, AuthContext.userId());
        return exportService.generatePdf(caseId, redact);
    }
}
