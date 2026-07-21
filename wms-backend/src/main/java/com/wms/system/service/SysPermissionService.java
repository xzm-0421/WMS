package com.wms.system.service;

import com.wms.system.dto.PermissionModuleGroup;
import com.wms.system.dto.SysPermissionDto;
import com.wms.system.entity.SysPermission;
import com.wms.system.mapper.SysPermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysPermissionService {

    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("dashboard", "工作台"),
            Map.entry("system", "系统管理"),
            Map.entry("base", "基础数据"),
            Map.entry("inbound", "入库管理"),
            Map.entry("outbound", "出库管理"),
            Map.entry("inventory", "库存管理"),
            Map.entry("stocktake", "盘点管理"),
            Map.entry("qc", "质检管理"),
            Map.entry("production", "生产对接"),
            Map.entry("barcode", "条码管理"),
            Map.entry("report", "报表分析")
    );

    private final SysPermissionMapper permissionMapper;

    public List<PermissionModuleGroup> listGrouped() {
        List<SysPermission> permissions = permissionMapper.selectAllActive();
        Map<String, PermissionModuleGroup> groups = new LinkedHashMap<>();
        for (SysPermission permission : permissions) {
            String module = permission.getModule() != null ? permission.getModule() : "other";
            PermissionModuleGroup group = groups.computeIfAbsent(module, key -> {
                PermissionModuleGroup g = new PermissionModuleGroup();
                g.setModule(key);
                g.setModuleName(MODULE_NAMES.getOrDefault(key, key));
                g.setPermissions(new ArrayList<>());
                return g;
            });
            SysPermissionDto dto = new SysPermissionDto();
            dto.setId(permission.getId());
            dto.setPermissionCode(permission.getPermissionCode());
            dto.setPermissionName(permission.getPermissionName());
            dto.setModule(permission.getModule());
            group.getPermissions().add(dto);
        }
        return new ArrayList<>(groups.values());
    }
}
