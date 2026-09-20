package com.wms.auth.security;

import com.wms.system.entity.SysRole;
import com.wms.system.entity.SysUser;
import com.wms.system.mapper.SysPermissionMapper;
import com.wms.system.mapper.SysRoleMapper;
import com.wms.system.mapper.SysUserMapper;
import com.wms.system.service.UserScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final UserScopeResolver userScopeResolver;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setPassword(user.getPassword());
        loginUser.setRealName(user.getRealName());
        loginUser.setEnabled(user.getStatus() != null && user.getStatus() == 1);
        List<SysRole> assignedRoles = roleMapper.selectRolesByUserId(user.getId());
        if (assignedRoles == null) {
            assignedRoles = List.of();
        }
        loginUser.setRoles(assignedRoles.stream().map(SysRole::getRoleCode).toList());
        loginUser.setRoleNames(assignedRoles.stream()
                .map(SysRole::getRoleName)
                .filter(StringUtils::hasText)
                .toList());
        loginUser.setPermissions(permissionMapper.selectPermissionCodesByUserId(user.getId()));
        loginUser.setWarehouseScope(userMapper.selectWarehouseScope(user.getId()));
        userScopeResolver.enrichLoginUser(loginUser);
        return loginUser;
    }
}
