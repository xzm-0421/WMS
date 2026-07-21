package com.wms.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.auth.security.LoginUser;
import com.wms.common.security.SecurityUtils;
import com.wms.system.constant.DataScopeType;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUser;
import com.wms.system.mapper.SysRoleMapper;
import com.wms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserScopeResolver {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public void enrichLoginUser(LoginUser loginUser) {
        List<SysRole> roles = roleMapper.selectRolesByUserId(loginUser.getUserId());
        boolean superAdmin = roles.stream()
                .anyMatch(r -> SecurityUtils.SUPER_ADMIN_ROLE.equals(r.getRoleCode()));
        loginUser.setSuperAdmin(superAdmin);

        if (superAdmin) {
            loginUser.setDataScope(DataScopeType.ALL);
            loginUser.setWarehouseScope(List.of());
            return;
        }

        int effectiveScope = DataScopeType.ALL;
        Set<String> warehouseCodes = new LinkedHashSet<>();
        for (SysRole role : roles) {
            Integer scope = role.getDataScope();
            if (scope == null) {
                continue;
            }
            if (scope == DataScopeType.ALL) {
                effectiveScope = DataScopeType.ALL;
                warehouseCodes.clear();
                break;
            }
            if (scope == DataScopeType.CUSTOM_WAREHOUSE) {
                effectiveScope = DataScopeType.CUSTOM_WAREHOUSE;
                warehouseCodes.addAll(parseWarehouseCodes(role.getWarehouseScopeJson()));
            } else if (scope == DataScopeType.DEPT || scope == DataScopeType.DEPT_AND_CHILD) {
                effectiveScope = Math.min(effectiveScope, scope);
            }
        }

        SysUser user = userMapper.selectById(loginUser.getUserId());
        List<String> userWarehouses = parseWarehouseCodes(user != null ? user.getWarehouseScopeJson() : null);
        if (!userWarehouses.isEmpty()) {
            if (effectiveScope == DataScopeType.ALL) {
                effectiveScope = DataScopeType.CUSTOM_WAREHOUSE;
                warehouseCodes.addAll(userWarehouses);
            } else if (effectiveScope == DataScopeType.CUSTOM_WAREHOUSE) {
                warehouseCodes.retainAll(userWarehouses);
            }
        }

        loginUser.setDataScope(effectiveScope);
        if (effectiveScope == DataScopeType.ALL) {
            loginUser.setWarehouseScope(List.of());
        } else if (effectiveScope == DataScopeType.CUSTOM_WAREHOUSE) {
            loginUser.setWarehouseScope(new ArrayList<>(warehouseCodes));
        } else if (loginUser.getWarehouseScope() == null) {
            loginUser.setWarehouseScope(List.of());
        }
    }

    private List<String> parseWarehouseCodes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
