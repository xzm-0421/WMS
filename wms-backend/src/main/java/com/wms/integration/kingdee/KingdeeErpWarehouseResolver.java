package com.wms.integration.kingdee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.base.entity.BaseWarehouse;
import com.wms.base.mapper.BaseWarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 将 WMS 仓库编码解析为金蝶仓库编码（FStockId），或反向解析。
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
            String mapped = lookupErpFromWms(wmsWarehouseCode.trim());
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

    /**
     * 金蝶仓库编码 → WMS 仓库编码（出库扣库存用）。
     * 若传入值本身已是 WMS 编码则原样返回。
     */
    public String resolveWmsCode(String erpOrWmsWarehouseCode) {
        if (!StringUtils.hasText(erpOrWmsWarehouseCode)) {
            return null;
        }
        String code = erpOrWmsWarehouseCode.trim();
        BaseWarehouse byWms = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, code)
                .eq(BaseWarehouse::getStatus, 1)
                .orderByAsc(BaseWarehouse::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (byWms != null) {
            return byWms.getWarehouseCode();
        }
        BaseWarehouse byErp = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getErpWarehouseCode, code)
                .eq(BaseWarehouse::getStatus, 1)
                .orderByAsc(BaseWarehouse::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (byErp != null && StringUtils.hasText(byErp.getWarehouseCode())) {
            return byErp.getWarehouseCode().trim();
        }
        Map<String, String> mappings = properties.getWarehouseMappings();
        if (mappings != null) {
            for (Map.Entry<String, String> e : mappings.entrySet()) {
                if (e.getValue() != null && code.equalsIgnoreCase(e.getValue().trim())
                        && StringUtils.hasText(e.getKey())) {
                    return e.getKey().trim();
                }
            }
        }
        return code;
    }

    private String lookupErpFromWms(String wmsWarehouseCode) {
        BaseWarehouse warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, wmsWarehouseCode)
                .eq(BaseWarehouse::getStatus, 1)
                .orderByAsc(BaseWarehouse::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (warehouse == null || !StringUtils.hasText(warehouse.getErpWarehouseCode())) {
            return null;
        }
        return warehouse.getErpWarehouseCode().trim();
    }
}
