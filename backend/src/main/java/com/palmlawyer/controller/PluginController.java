// PluginController.java
// 领域插件控制器，提供插件查询和热重载接口
package com.palmlawyer.controller;

import com.palmlawyer.service.infra.plugin.DomainPlugin;
import com.palmlawyer.service.infra.plugin.DomainPluginLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * 插件控制器
 *
 * <p>提供领域插件的查询和热重载接口。
 * <p>热重载属管理操作：需配置 palmlawyer.plugin.admin-token，并以 X-Admin-Token 头携带；
 * 未配置时一律拒绝（避免任意登录用户触发共享状态变更）。
 */
@RestController
@RequestMapping("/plugins")
public class PluginController {

    private final DomainPluginLoader loader;

    @Value("${palmlawyer.plugin.admin-token:}")
    private String adminToken;

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
     * 热重载指定插件（管理操作，需 admin token）
     */
    @PostMapping("/reload")
    public Map<String, Object> reload(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestBody Map<String, String> body) {
        if (adminToken == null || adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "未配置 PALMLAWYER_PLUGIN_ADMIN_TOKEN，插件热重载已禁用");
        }
        if (!adminToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "admin token 无效");
        }
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
