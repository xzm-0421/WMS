package com.wms.integration.kingdee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.base.entity.BaseWarehouse;
import com.wms.base.mapper.BaseWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 将 WMS 仓库编码解析为金蝶仓库编码（FStockId）。
 * 优先级：收料分录仓库 &gt; 基础资料映射 &gt; 配置映射 &gt; 默认仓库。
 */
@Component
@RequiredArgsConstructor
public class KingdeeErpWarehouseResolver {

    private final BaseWarehouseMapper warehouseMapper;
    private final KingdeeCloudProperties properties;

    public String resolve(String wmsWarehouseCode, String receiveLineErpStockCode) {
        if (StringUtils.hasText(receiveLineErpStockCode)) {
            return receiveLineErpStockCode.trim();
        }
        if (StringUtils.hasText(wmsWarehouseCode)) {
            String mapped = lookupFromBaseWarehouse(wmsWarehouseCode.trim());
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
            String configured = properties.getWarehouseMappings().get(wmsWarehouseCode.trim());
            if (StringUtils.hasText(configured)) {
                return configured.trim();
            }
        }
        return properties.getStockInDefaultWarehouseNumber();
    }

    private String lookupFromBaseWarehouse(String wmsWarehouseCode) {
        BaseWarehouse warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, wmsWarehouseCode)
                .eq(BaseWarehouse::getStatus, 1)
                .orderByAsc(BaseWarehouse::getId));
        if (warehouse == null || !StringUtils.hasText(warehouse.getErpWarehouseCode())) {
            return null;
        }
        return warehouse.getErpWarehouseCode().trim();
    }
}
