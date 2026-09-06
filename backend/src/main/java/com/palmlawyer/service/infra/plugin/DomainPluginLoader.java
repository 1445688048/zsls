package com.palmlawyer.service.infra.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import jakarta.annotation.PostConstruct;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领域插件加载器
 *
 * <p>启动时从 classpath:plugins/*.yaml 加载所有领域插件。
 * 使用 PathMatchingResourcePatternResolver 兼容 jar 包和 IDE 运行环境。
 * <p>插件 Map 为 ConcurrentHashMap：热重载（请求线程写）与查询（其他请求线程读）并发安全。
 */
@Slf4j
@Component
public class DomainPluginLoader {

    private final Map<String, DomainPlugin> plugins = new ConcurrentHashMap<>();
    
    private final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    @PostConstruct
    public void load() {
        Yaml yaml = new Yaml();
        try {
            Resource[] resources = resourceResolver.getResources("classpath:plugins/*.yaml");
            log.info("找到 {} 个领域插件文件", resources.length);
            
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = yaml.load(is);
                    DomainPlugin plugin = convertToPlugin(data);
                    if (plugin != null && plugin.getDomain() != null) {
                        plugins.put(plugin.getDomain().getId(), plugin);
                        log.info("已加载插件: {} ({})", plugin.getDomain().getId(), plugin.getDomain().getName());
                    }
                } catch (Exception e) {
                    log.error("加载插件失败: {}", resource.getFilename(), e);
                }
            }
        } catch (Exception e) {
            log.error("加载插件目录失败", e);
        }
    }

    public DomainPlugin getPlugin(String id) {
        return plugins.get(id);
    }

    public Map<String, DomainPlugin> getAllPlugins() {
        return new HashMap<>(plugins);
    }

    /**
     * 热加载插件
     */
    public DomainPlugin reloadPlugin(String domainId) {
        Yaml yaml = new Yaml();
        try {
            Resource[] resources = resourceResolver.getResources("classpath:plugins/*.yaml");
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = yaml.load(is);
                    DomainPlugin plugin = convertToPlugin(data);
                    if (plugin != null && plugin.getDomain() != null && plugin.getDomain().getId().equals(domainId)) {
                        plugins.put(domainId, plugin);
                        log.info("已热加载插件: {}", domainId);
                        return plugin;
                    }
                }
            }
        } catch (Exception e) {
            log.error("热加载插件失败: {}", domainId, e);
        }
        return null;
    }

    /**
     * 将 YAML 数据转换为 DomainPlugin 对象
     */
    @SuppressWarnings("unchecked")
    private DomainPlugin convertToPlugin(Map<String, Object> data) {
        DomainPlugin plugin = new DomainPlugin();
        
        // domain
        Object domainObj = data.get("domain");
        if (domainObj instanceof Map) {
            plugin.setDomain(convertDomain((Map<String, Object>) domainObj));
        }
        
        // factSchema
        Object factSchemaObj = data.get("factSchema");
        if (factSchemaObj instanceof List) {
            plugin.setFactSchema(convertFactSchema((List<Object>) factSchemaObj));
        }
        
        // evidenceRules
        Object evidenceObj = data.get("evidenceRules");
        if (evidenceObj instanceof Map) {
            plugin.setEvidenceRules(convertEvidenceRules((Map<String, Object>) evidenceObj));
        }
        
        // solutionTemplates
        Object solutionsObj = data.get("solutionTemplates");
        if (solutionsObj instanceof Map) {
            plugin.setSolutionTemplates((Map<String, Object>) solutionsObj);
        }
        
        // lawMapping
        Object lawObj = data.get("lawMapping");
        if (lawObj instanceof Map) {
            plugin.setLawMapping((Map<String, Object>) lawObj);
        }
        
        // processTemplate
        Object processObj = data.get("processTemplate");
        if (processObj instanceof Map) {
            plugin.setProcessTemplate(convertProcessTemplate((Map<String, Object>) processObj));
        }
        
        // docTemplates
        Object docObj = data.get("docTemplates");
        if (docObj instanceof List) {
            plugin.setDocTemplates(convertDocTemplates((List<Object>) docObj));
        }
        
        return plugin;
    }

    private DomainPlugin.Domain convertDomain(Map<String, Object> data) {
        DomainPlugin.Domain d = new DomainPlugin.Domain();
        d.setId((String) data.get("id"));
        d.setName((String) data.get("name"));
        d.setVersion((String) data.get("version"));
        d.setDescription((String) data.get("description"));
        return d;
    }

    @SuppressWarnings("unchecked")
    private List<DomainPlugin.FactField> convertFactSchema(List<Object> list) {
        List<DomainPlugin.FactField> result = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            DomainPlugin.FactField f = new DomainPlugin.FactField();
            f.setKey((String) m.get("key"));
            f.setLabel((String) m.get("label"));
            f.setType((String) m.get("type"));
            f.setRequired(Boolean.TRUE.equals(m.get("required")));
            f.setAskPrompt((String) m.get("askPrompt"));
            f.setApplicableWhen((String) m.get("applicableWhen"));
            
            if (m.get("options") instanceof List) {
                f.setOptions(new ArrayList<>((List<String>) m.get("options")));
            }
            
            if (m.get("followUpIfTrue") instanceof List) {
                f.setFollowUpIfTrue(convertFactSchema((List<Object>) m.get("followUpIfTrue")));
            }
            if (m.get("followUpIfFalse") instanceof List) {
                f.setFollowUpIfFalse(convertFactSchema((List<Object>) m.get("followUpIfFalse")));
            }
            
            result.add(f);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private DomainPlugin.EvidenceRules convertEvidenceRules(Map<String, Object> data) {
        DomainPlugin.EvidenceRules rules = new DomainPlugin.EvidenceRules();
        
        if (data.get("necessary") instanceof List) {
            rules.setNecessary(convertEvidenceItems((List<Object>) data.get("necessary")));
        }
        if (data.get("enhancing") instanceof List) {
            rules.setEnhancing(convertEvidenceItems((List<Object>) data.get("enhancing")));
        }
        if (data.get("earlyWarning") instanceof List) {
            rules.setEarlyWarning(convertEarlyWarnings((List<Object>) data.get("earlyWarning")));
        }
        
        return rules;
    }

    @SuppressWarnings("unchecked")
    private List<DomainPlugin.EvidenceItem> convertEvidenceItems(List<Object> list) {
        List<DomainPlugin.EvidenceItem> result = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            DomainPlugin.EvidenceItem item = new DomainPlugin.EvidenceItem();
            item.setId((String) m.get("id"));
            item.setName((String) m.get("name"));
            item.setDescription((String) m.get("description"));
            item.setApplicableWhen((String) m.get("applicableWhen"));
            result.add(item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<DomainPlugin.EarlyWarning> convertEarlyWarnings(List<Object> list) {
        List<DomainPlugin.EarlyWarning> result = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            DomainPlugin.EarlyWarning w = new DomainPlugin.EarlyWarning();
            if (m.get("triggerKeywords") instanceof List) {
                w.setTriggerKeywords(new ArrayList<>((List<String>) m.get("triggerKeywords")));
            }
            if (m.get("evidenceToCollect") instanceof List) {
                w.setEvidenceToCollect(new ArrayList<>((List<String>) m.get("evidenceToCollect")));
            }
            w.setWarningText((String) m.get("warningText"));
            result.add(w);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private DomainPlugin.ProcessTemplate convertProcessTemplate(Map<String, Object> data) {
        DomainPlugin.ProcessTemplate template = new DomainPlugin.ProcessTemplate();
        
        if (data.get("steps") instanceof List) {
            List<DomainPlugin.ProcessStep> steps = new ArrayList<>();
            for (Object obj : (List<Object>) data.get("steps")) {
                if (!(obj instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) obj;
                DomainPlugin.ProcessStep step = new DomainPlugin.ProcessStep();
                step.setId((String) m.get("id"));
                step.setName((String) m.get("name"));
                step.setDescription((String) m.get("description"));
                step.setDeadlineRule((String) m.get("deadlineRule"));
                step.setTips((String) m.get("tips"));
                if (m.get("requiredDocs") instanceof List) {
                    step.setRequiredDocs(new ArrayList<>((List<String>) m.get("requiredDocs")));
                }
                steps.add(step);
            }
            template.setSteps(steps);
        }
        
        template.setEvidenceCopyNote((String) data.get("evidenceCopyNote"));
        return template;
    }

    @SuppressWarnings("unchecked")
    private List<DomainPlugin.DocumentTemplate> convertDocTemplates(List<Object> list) {
        List<DomainPlugin.DocumentTemplate> result = new ArrayList<>();
        for (Object obj : list) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) obj;
            DomainPlugin.DocumentTemplate dt = new DomainPlugin.DocumentTemplate();
            dt.setId((String) m.get("id"));
            dt.setName((String) m.get("name"));
            if (m.get("fields") instanceof List) {
                List<DomainPlugin.FieldDef> fields = new ArrayList<>();
                for (Object fObj : (List<Object>) m.get("fields")) {
                    if (!(fObj instanceof Map)) continue;
                    Map<String, Object> fm = (Map<String, Object>) fObj;
                    DomainPlugin.FieldDef fd = new DomainPlugin.FieldDef();
                    fd.setLabel((String) fm.get("label"));
                    fd.setPlaceholder((String) fm.get("placeholder"));
                    fields.add(fd);
                }
                dt.setFields(fields);
            }
            result.add(dt);
        }
        return result;
    }
}