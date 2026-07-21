package com.wms.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.inventory.dto.InventorySummaryDto;
import com.wms.inventory.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryQueryMapper extends BaseMapper<Inventory> {

    @Select("""
            <script>
            SELECT warehouse_code AS warehouseCode, material_code AS materialCode,
                   SUM(stock_qty) AS totalStockQty, SUM(available_qty) AS totalAvailableQty,
                   SUM(frozen_qty) AS totalFrozenQty
            FROM inventory
            WHERE stock_qty > 0
            <if test="warehouseCode != null and warehouseCode != ''">
              AND warehouse_code = #{warehouseCode}
            </if>
            <if test="materialCode != null and materialCode != ''">
              AND material_code = #{materialCode}
            </if>
            GROUP BY warehouse_code, material_code
            ORDER BY warehouse_code, material_code
            </script>
            """)
    List<InventorySummaryDto> selectSummary(@Param("warehouseCode") String warehouseCode,
                                            @Param("materialCode") String materialCode);
}
