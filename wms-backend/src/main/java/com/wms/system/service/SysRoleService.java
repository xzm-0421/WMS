package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.system.constant.DataScopeType;
import com.wms.system.dto.RolePermissionsRequest;
import com.wms.system.dto.SysRoleDto;
import com.wms.system.entity.SysPermission;
import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysRolePermission;
import com.wms.system.mapper.SysPermissionMapper;
import com.wms.system.mapper.SysRoleMapper;
import com.wms.system.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final ObjectMapper objectMapper;

    public PageResult<SysRoleDto> page(String roleCode, String roleName, Integer status,
                                       long current, long size) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(roleCode), SysRole::getRoleCode, roleCode)
                .like(StringUtils.hasText(roleName), SysRole::getRoleName, roleName)
                .eq(status != null, SysRole::getStatus, status)
                .orderByDesc(SysRole::getCreateTime);
        Page<SysRole> page = roleMapper.selectPage(new Page<>(current, size), wrapper);
        List<SysRoleDto> records = page.getRecords().stream().map(this::toDto).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public SysRoleDto getById(Long id) {
        return toDto(getRole(id));
    }

    public void create(SysRole role) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "角色编码已存在", "ROLE_CODE_EXISTS");
        }
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        if (SecurityUtils.isSuperAdmin()) {
            if (role.getDataScope() == null) {
                role.setDataScope(DataScopeType.CUSTOM_WAREHOUSE);
            }
        } else {
            role.setDataScope(DataScopeType.CUSTOM_WAREHOUSE);
            role.setWarehouseScopeJson(null);
        }
        roleMapper.insert(role);
    }

    public void update(Long id, SysRole role) {
        SysRole existing = getRole(id);
        assertNotSuperAdminRole(existing);
        role.setId(existing.getId());
        role.setRoleCode(existing.getRoleCode());
        if (!SecurityUtils.isSuperAdmin()) {
            role.setDataScope(existing.getDataScope());
            role.setWarehouseScopeJson(existing.getWarehouseScopeJson());
        }
        roleMapper.updateById(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = getRole(id);
        assertNotSuperAdminRole(role);
        rolePermissionMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePermissions(Long id, RolePermissionsRequest request) {
        SecurityUtils.requireSuperAdmin();
        SysRole role = getRole(id);
        assertNotSuperAdminRole(role);

        if (request.getDataScope() != null) {
            role.setDataScope(request.getDataScope());
            if (request.getDataScope() == DataScopeType.CUSTOM_WAREHOUSE) {
                role.setWarehouseScopeJson(toWarehouseJson(request.getWarehouseScope()));
            } else {
                role.setWarehouseScopeJson(null);
            }
            roleMapper.updateById(role);
        }

        rolePermissionMapper.deleteByRoleId(id);
        List<Long> permissionIds = resolvePermissionIds(request);
        for (Long permissionId : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(permissionId);
            rolePermissionMapper.insert(rp);
        }
    }

    private List<Long> resolvePermissionIds(RolePermissionsRequest request) {
        List<Long> ids = new ArrayList<>();
        if (request.getPermissionIds() != null) {
            ids.addAll(request.getPermissionIds());
        }
        if (request.getPermissionCodes() != null) {
            for (String code : request.getPermissionCodes()) {
                SysPermission perm = permissionMapper.selectByCode(code);
                if (perm != null && !ids.contains(perm.getId())) {
                    ids.add(perm.getId());
                }
            }
        }
        return ids;
    }

    private SysRole getRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在", "ROLE_NOT_FOUND");
        }
        return role;
    }

    private void assertNotSuperAdminRole(SysRole role) {
        if (SecurityUtils.SUPER_ADMIN_ROLE.equals(role.getRoleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "超级管理员角色不可修改");
        }
    }

    private SysRoleDto toDto(SysRole role) {
        SysRoleDto dto = new SysRoleDto();
        BeanUtils.copyProperties(role, dto);
        dto.setPermissionIds(rolePermissionMapper.selectPermissionIdsByRoleId(role.getId()));
        dto.setPermissionCodes(permissionMapper.selectPermissionCodesByRoleId(role.getId()));
        dto.setWarehouseScope(parseWarehouseCodes(role.getWarehouseScopeJson()));
        return dto;
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

    private String toWarehouseJson(List<String> warehouses) {
        if (warehouses == null || warehouses.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(warehouses);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库范围格式错误");
        }
    }
}
