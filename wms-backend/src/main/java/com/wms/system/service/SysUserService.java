package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.service.AuthService;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.system.dto.*;
import com.wms.system.entity.SysUser;
import com.wms.system.entity.SysUserRole;
import com.wms.system.mapper.SysUserMapper;
import com.wms.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<SysUserDto> page(String username, String realName, Integer status,
                                       long current, long size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(realName), SysUser::getRealName, realName)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        List<SysUserDto> records = page.getRecords().stream().map(this::toDto).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public SysUserDto getById(Long id) {
        SysUser user = getUser(id);
        return toDto(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void create(SysUserCreateRequest request) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在", "USERNAME_EXISTS");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(AuthService.sha256Hex(request.getPassword())));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setDeptId(request.getDeptId());
        user.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        user.setWarehouseScopeJson(request.getWarehouseScopeJson());
        userMapper.insert(user);
        saveUserRoles(user.getId(), request.getRoleIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SysUserUpdateRequest request) {
        SysUser user = getUser(id);
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setDeptId(request.getDeptId());
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getWarehouseScopeJson() != null) {
            user.setWarehouseScopeJson(request.getWarehouseScopeJson());
        }
        userMapper.updateById(user);
        if (request.getRoleIds() != null) {
            userRoleMapper.deleteByUserId(id);
            saveUserRoles(id, request.getRoleIds());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getUser(id);
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
    }

    public void resetPassword(Long id, ResetPasswordRequest request) {
        SysUser user = getUser(id);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(AuthService.sha256Hex(request.getNewPassword())));
        userMapper.updateById(update);
    }

    public void updateStatus(Long id, UpdateStatusRequest request) {
        SysUser user = getUser(id);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setStatus(request.getStatus());
        userMapper.updateById(update);
    }

    private SysUser getUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在", "USER_NOT_FOUND");
        }
        return user;
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private SysUserDto toDto(SysUser user) {
        SysUserDto dto = new SysUserDto();
        BeanUtils.copyProperties(user, dto);
        dto.setRoleIds(userRoleMapper.selectRoleIdsByUserId(user.getId()));
        return dto;
    }
}
