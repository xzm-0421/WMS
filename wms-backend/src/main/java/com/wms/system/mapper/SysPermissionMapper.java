package com.wms.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT DISTINCT p.permission_code FROM sys_permission p
            INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
            INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
            WHERE ur.user_id = #{userId} AND p.deleted = 0 AND p.status = 1
            """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM sys_permission WHERE permission_code = #{code} AND deleted = 0
            """)
    SysPermission selectByCode(@Param("code") String code);

    @Select("SELECT * FROM sys_permission WHERE deleted = 0 AND status = 1 ORDER BY module, id")
    List<SysPermission> selectAllActive();

    @Select("""
            SELECT p.permission_code FROM sys_permission p
            INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
            WHERE rp.role_id = #{roleId} AND p.deleted = 0
            ORDER BY p.module, p.id
            """)
    List<String> selectPermissionCodesByRoleId(@Param("roleId") Long roleId);
}
