package com.wms.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.base.entity.BaseMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BaseMaterialMapper extends BaseMapper<BaseMaterial> {

    /**
     * 按编码查询（含逻辑删除行），用于同步时复活已删除的物料，避免 uk_material_code 冲突。
     * 自定义 SQL 不受逻辑删除拦截，会返回 deleted=1 的行。
     */
    @Select("SELECT * FROM base_material WHERE material_code = #{materialCode} "
            + "ORDER BY deleted ASC, id DESC OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY")
    BaseMaterial selectAnyByCode(@Param("materialCode") String materialCode);

    /**
     * 复活逻辑删除行（绕过 @TableLogic 的 deleted=0 条件）。
     */
    @Update("UPDATE base_material SET deleted = 0 WHERE id = #{id}")
    int reviveById(@Param("id") Long id);
}

