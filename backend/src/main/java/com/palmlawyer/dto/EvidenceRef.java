// EvidenceRef.java
package com.palmlawyer.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EvidenceRef {
    private String evidenceId;
    private String fileName;
    private String type;  // CONTRACT / PAYSLIP / CHAT_SCREENSHOT / AUDIO_RECORDING / VIDEO / OTHER
    private String location;  // LOCAL_DEVICE / CLOUD_ENCRYPTED
    private String localPath;
    private String cloudUrl;
    private LocalDateTime uploadedAt;
}
