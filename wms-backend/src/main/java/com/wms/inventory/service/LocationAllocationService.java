package com.wms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.base.entity.BaseLocation;
import com.wms.base.mapper.BaseLocationMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocationAllocationService {

    private final InventoryMapper inventoryMapper;
    private final BaseLocationMapper locationMapper;

    /**
     * 推荐入库库位：同物料归位 → 空库位 → 仓库首个可用库位
     */
    public Map<String, Object> recommendInboundLocation(String warehouseCode, String materialCode, String batchNo) {
        if (!StringUtils.hasText(warehouseCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库编码不能为空");
        }

        if (StringUtils.hasText(materialCode)) {
            List<Inventory> sameMaterial = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                    .eq(Inventory::getWarehouseCode, warehouseCode)
                    .eq(Inventory::getMaterialCode, materialCode)
                    .gt(Inventory::getStockQty, BigDecimal.ZERO)
                    .orderByDesc(Inventory::getStockQty));
            if (!sameMaterial.isEmpty()) {
                Inventory best = sameMaterial.get(0);
                return buildResult(best.getLocationCode(), best.getLocationCode(), "CONSOLIDATE",
                        "同物料归位至现有库位");
            }
        }

        List<BaseLocation> locations = locationMapper.selectList(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseCode, warehouseCode)
                .eq(BaseLocation::getStatus, 1)
                .eq(BaseLocation::getDeleted, 0)
                .orderByAsc(BaseLocation::getLocationCode));

        if (locations.isEmpty()) {
            return buildResult("", "", "WAREHOUSE_ONLY", "仅仓库维度，无需库位");
        }

        Set<String> occupied = loadOccupiedLocations(warehouseCode);
        for (BaseLocation loc : locations) {
            if (!occupied.contains(loc.getLocationCode())) {
                return buildResult(loc.getLocationCode(), loc.getLocationName(), "EMPTY", "分配空库位");
            }
        }

        BaseLocation first = locations.get(0);
        return buildResult(first.getLocationCode(), first.getLocationName(), "DEFAULT", "使用默认库位");
    }

    /** 解析最终库位：手动优先，否则自动分配 */
    public String resolveInboundLocation(String warehouseCode, String materialCode,
                                         String batchNo, String manualLocation) {
        if (StringUtils.hasText(manualLocation)) {
            validateLocation(warehouseCode, manualLocation.trim());
            return manualLocation.trim();
        }
        Map<String, Object> rec = recommendInboundLocation(warehouseCode, materialCode, batchNo);
        return (String) rec.get("locationCode");
    }

    public List<BaseLocation> listByWarehouse(String warehouseCode) {
        return locationMapper.selectList(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseCode, warehouseCode)
                .eq(BaseLocation::getStatus, 1)
                .eq(BaseLocation::getDeleted, 0)
                .orderByAsc(BaseLocation::getLocationCode));
    }

    private Set<String> loadOccupiedLocations(String warehouseCode) {
        List<Inventory> stocks = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouseCode)
                .gt(Inventory::getStockQty, BigDecimal.ZERO));
        Set<String> set = new HashSet<>();
        for (Inventory inv : stocks) {
            if (StringUtils.hasText(inv.getLocationCode())) {
                set.add(inv.getLocationCode());
            }
        }
        return set;
    }

    private void validateLocation(String warehouseCode, String locationCode) {
        BaseLocation loc = locationMapper.selectOne(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getLocationCode, locationCode)
                .eq(BaseLocation::getDeleted, 0));
        if (loc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "库位不存在: " + locationCode);
        }
        if (!warehouseCode.equals(loc.getWarehouseCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库位不属于当前仓库");
        }
        if (loc.getStatus() != null && loc.getStatus() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "库位已停用");
        }
    }

    private Map<String, Object> buildResult(String code, String name, String strategy, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("locationCode", code);
        map.put("locationName", name != null ? name : code);
        map.put("strategy", strategy);
        map.put("message", message);
        return map;
    }
}
