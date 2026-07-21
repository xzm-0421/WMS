package com.wms.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    SysUser selectByUsername(@Param("username") String username);

    @Select("""
            SELECT COALESCE(u.warehouse_scope_json, '[]') FROM sys_user u WHERE u.id = #{userId}
            """)
    String selectWarehouseScopeJson(@Param("userId") Long userId);

    default List<String> selectWarehouseScope(Long userId) {
        String json = selectWarehouseScopeJson(userId);
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        json = json.replace("[", "").replace("]", "").replace("\"", "");
        if (json.isBlank()) {
            return List.of();
        }
        return List.of(json.split(","));
    }
}
