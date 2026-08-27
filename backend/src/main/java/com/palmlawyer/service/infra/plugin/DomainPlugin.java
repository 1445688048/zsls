package com.palmlawyer.service.infra.plugin;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DomainPlugin {
    private Domain domain;
    private List<FactField> factSchema;
    private EvidenceRules evidenceRules;
    private Map<String, Object> solutionTemplates;
    private Map<String, Object> lawMapping;
    private ProcessTemplate processTemplate;
    private List<DocumentTemplate> docTemplates;

    @Data
    public static class Domain {
        private String id;
        private String name;
        private String version;
        private String description;
    }

    @Data
    public static class FactField {
        private String key;
        private String label;
        private String type;
        private boolean required;
        private String askPrompt;
        private List<String> options;
        private List<FactField> followUpIfTrue;
        private List<FactField> followUpIfFalse;
        private String applicableWhen;
    }

    @Data
    public static class EvidenceRules {
        private List<EvidenceItem> necessary;
        private List<EvidenceItem> enhancing;
        private List<EarlyWarning> earlyWarning;
    }

    @Data
    public static class EvidenceItem {
        private String id;
        private String name;
        private String description;
        private String applicableWhen;
    }

    @Data
    public static class EarlyWarning {
        private List<String> triggerKeywords;
        private List<String> evidenceToCollect;
        private String warningText;
    }

    @Data
    public static class ProcessTemplate {
        private List<ProcessStep> steps;
        private String evidenceCopyNote;
    }

    @Data
    public static class ProcessStep {
        private String id;
        private String name;
        private String description;
        private List<String> requiredDocs;
        private String deadlineRule;
        private String tips;
    }

    @Data
    public static class DocumentTemplate {
        private String id;
        private String name;
        private List<FieldDef> fields;
    }

    @Data
    public static class FieldDef {
        private String label;
        private String placeholder;
    }
}