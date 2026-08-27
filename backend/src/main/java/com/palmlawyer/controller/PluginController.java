// PluginController.java
// 领域插件控制器，提供插件查询和热重载接口
package com.palmlawyer.controller;

import com.palmlawyer.service.infra.plugin.DomainPlugin;
import com.palmlawyer.service.infra.plugin.DomainPluginLoader;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 插件控制器
 *
 * <p>提供领域插件的查询和热重载接口。
 */
@RestController
@RequestMapping("/plugins")
public class PluginController {

    private final DomainPluginLoader loader;

    public PluginController(DomainPluginLoader loader) {
        this.loader = loader;
    }

    /**
     * 查询所有已加载的插件
     */
    @GetMapping
    public Map<String, Object> listPlugins() {
        Map<String, Object> result = new HashMap<>();
        Map<String, DomainPlugin> plugins = loader.getAllPlugins();
        List<Map<String, String>> list = new ArrayList<>();
        plugins.forEach((id, plugin) -> {
            Map<String, String> info = new HashMap<>();
            info.put("id", plugin.getDomain().getId());
            info.put("name", plugin.getDomain().getName());
            info.put("version", plugin.getDomain().getVersion());
            list.add(info);
        });
        result.put("plugins", list);
        return result;
    }

    /**
     * 热重载指定插件
     */
    @PostMapping("/reload")
    public Map<String, Object> reload(@RequestBody Map<String, String> body) {
        String domainId = body.getOrDefault("domainId", "LABOR");
        DomainPlugin plugin = loader.reloadPlugin(domainId);
        Map<String, Object> result = new HashMap<>();
        if (plugin != null) {
            result.put("msg", "reloaded: " + plugin.getDomain().getName());
        } else {
            result.put("msg", "plugin not found: " + domainId);
            result.put("loadedPlugins", loader.getAllPlugins().keySet());
        }
        return result;
    }
}
