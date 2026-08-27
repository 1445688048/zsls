// CaseGuard.java
// 案件属主校验：防止越权访问他人案件（IDOR 防护）
package com.palmlawyer.service;

import com.palmlawyer.entity.CaseProfile;
import com.palmlawyer.mapper.CaseProfileMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CaseGuard {

    private final CaseProfileMapper caseMapper;

    public CaseGuard(CaseProfileMapper caseMapper) {
        this.caseMapper = caseMapper;
    }

    /**
     * 断言案件存在且属于当前用户；否则抛 404（不暴露案件是否存在）
     */
    public void assertOwner(Long caseId, Long userId) {
        CaseProfile c = caseMapper.selectById(caseId);
        if (c == null || !c.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "案件不存在");
        }
    }
}
