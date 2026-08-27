// FactValue.java
package com.palmlawyer.dto;

import lombok.Data;
import java.util.List;

@Data
public class FactValue {
    private Object value;
    private List<EvidenceRef> evidenceRefs;
    private String confidence;  // HIGH / MEDIUM / LOW
    private String source;      // USER_INPUT / AGENT_INFERRED / EVIDENCE_EXTRACTED
}
