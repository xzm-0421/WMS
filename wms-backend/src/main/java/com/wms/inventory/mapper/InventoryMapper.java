package com.wms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("""
            SELECT * FROM inventory
            WHERE warehouse_code = #{warehouseCode}
              AND location_code = #{locationCode}
              AND material_code = #{materialCode}
              AND batch_no = #{batchNo}
            """)
    Inventory selectByKey(@Param("warehouseCode") String warehouseCode,
                          @Param("locationCode") String locationCode,
                          @Param("materialCode") String materialCode,
                          @Param("batchNo") String batchNo);
}
