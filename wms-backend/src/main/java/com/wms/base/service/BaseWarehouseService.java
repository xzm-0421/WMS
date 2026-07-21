package com.wms.base.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.base.entity.BaseWarehouse;
import com.wms.base.mapper.BaseWarehouseMapper;
import com.wms.base.mapper.BaseLocationMapper;
import com.wms.base.entity.BaseLocation;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.CodeAutoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BaseWarehouseService {

    private final BaseWarehouseMapper warehouseMapper;
    private final BaseLocationMapper locationMapper;
    private final InventoryMapper inventoryMapper;
    private final JdbcTemplate jdbcTemplate;

    public PageResult<BaseWarehouse> page(String warehouseCode, String warehouseName,
                                            String warehouseType, Integer status,
                                            long current, long size) {
        LambdaQueryWrapper<BaseWarehouse> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(warehouseCode), BaseWarehouse::getWarehouseCode, warehouseCode)
                .like(StringUtils.hasText(warehouseName), BaseWarehouse::getWarehouseName, warehouseName)
                .eq(StringUtils.hasText(warehouseType), BaseWarehouse::getWarehouseType, warehouseType)
                .eq(status != null, BaseWarehouse::getStatus, status)
                .orderByDesc(BaseWarehouse::getCreateTime);
        Page<BaseWarehouse> page = warehouseMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public BaseWarehouse getByCode(String warehouseCode) {
        BaseWarehouse warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, warehouseCode));
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在", "WAREHOUSE_NOT_FOUND");
        }
        return warehouse;
    }

    @Transactional
    public void create(BaseWarehouse warehouse) {
        warehouse.setWarehouseCode(CodeAutoGenerator.ensureOrGenerate(warehouse.getWarehouseCode(), "WH"));
        if (!StringUtils.hasText(warehouse.getWarehouseName())) {
            warehouse.setWarehouseName(warehouse.getWarehouseCode());
        } else {
            warehouse.setWarehouseName(warehouse.getWarehouseName().trim());
        }
        if (!StringUtils.hasText(warehouse.getWarehouseType())) {
            warehouse.setWarehouseType("RAW");
        }
        if (warehouse.getStatus() == null) {
            warehouse.setStatus(1);
        }

        BaseWarehouse existing = findByCodeIncludeDeleted(warehouse.getWarehouseCode());
        if (existing != null) {
            if (existing.getDeleted() == null || existing.getDeleted() == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "仓库编码已存在", "WAREHOUSE_CODE_EXISTS");
            }
            restoreWarehouse(existing.getId(), warehouse);
            return;
        }
        warehouseMapper.insert(warehouse);
    }

    private BaseWarehouse findByCodeIncludeDeleted(String warehouseCode) {
        List<BaseWarehouse> rows = jdbcTemplate.query(
                """
                        SELECT TOP 1 id, warehouse_code, warehouse_name, warehouse_type, factory_code,
                               address, phone, status, remark, deleted
                        FROM base_warehouse WHERE warehouse_code = ?
                        """,
                (rs, rowNum) -> {
                    BaseWarehouse w = new BaseWarehouse();
                    w.setId(rs.getLong("id"));
                    w.setWarehouseCode(rs.getString("warehouse_code"));
                    w.setWarehouseName(rs.getString("warehouse_name"));
                    w.setWarehouseType(rs.getString("warehouse_type"));
                    w.setFactoryCode(rs.getString("factory_code"));
                    w.setAddress(rs.getString("address"));
                    w.setPhone(rs.getString("phone"));
                    w.setStatus(rs.getInt("status"));
                    w.setRemark(rs.getString("remark"));
                    w.setDeleted(rs.getInt("deleted"));
                    return w;
                },
                warehouseCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void restoreWarehouse(Long id, BaseWarehouse warehouse) {
        jdbcTemplate.update(
                """
                        UPDATE base_warehouse
                        SET warehouse_name = ?, warehouse_type = ?, factory_code = ?, address = ?,
                            phone = ?, status = ?, remark = ?, erp_warehouse_code = ?, deleted = 0, update_time = ?
                        WHERE id = ?
                        """,
                warehouse.getWarehouseName(),
                warehouse.getWarehouseType(),
                warehouse.getFactoryCode(),
                warehouse.getAddress(),
                warehouse.getPhone(),
                warehouse.getStatus(),
                warehouse.getRemark(),
                warehouse.getErpWarehouseCode(),
                LocalDateTime.now(),
                id);
    }

    /**
     * 从金蝶 BD_STOCK upsert 仓库。匹配到已有记录时用金蝶数据全量覆盖名称、类型、ERP 编码与状态。
     *
     * @return INSERTED / UPDATED / SKIPPED
     */
    public String upsertFromKingdee(String erpWarehouseCode, String warehouseName,
                                    String stockProperty, boolean active) {
        if (!StringUtils.hasText(erpWarehouseCode)) {
            return "SKIPPED";
        }
        String erpCode = erpWarehouseCode.trim();
        BaseWarehouse existing = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, erpCode));
        if (existing == null) {
            existing = findByErpCode(erpCode);
        }
        if (existing == null) {
            BaseWarehouse warehouse = new BaseWarehouse();
            warehouse.setWarehouseCode(erpCode);
            warehouse.setWarehouseName(defaultWarehouseName(warehouseName, erpCode));
            warehouse.setErpWarehouseCode(erpCode);
            warehouse.setWarehouseType(mapStockProperty(stockProperty));
            warehouse.setStatus(active ? 1 : 0);
            warehouseMapper.insert(warehouse);
            return "INSERTED";
        }
        existing.setWarehouseName(defaultWarehouseName(warehouseName, existing.getWarehouseCode()));
        existing.setErpWarehouseCode(erpCode);
        existing.setWarehouseType(mapStockProperty(stockProperty));
        existing.setStatus(active ? 1 : 0);
        existing.setUpdateTime(LocalDateTime.now());
        warehouseMapper.updateById(existing);
        return "UPDATED";
    }

    /**
     * 全量同步后，禁用金蝶中已不存在、但本地仍关联 ERP 编码的仓库。
     */
    public int disableStaleKingdeeWarehouses(java.util.Set<String> activeErpCodes) {
        if (activeErpCodes == null || activeErpCodes.isEmpty()) {
            return 0;
        }
        List<BaseWarehouse> linked = warehouseMapper.selectList(new LambdaQueryWrapper<BaseWarehouse>()
                .isNotNull(BaseWarehouse::getErpWarehouseCode)
                .ne(BaseWarehouse::getErpWarehouseCode, "")
                .eq(BaseWarehouse::getStatus, 1));
        int disabled = 0;
        for (BaseWarehouse warehouse : linked) {
            if (!activeErpCodes.contains(warehouse.getErpWarehouseCode().trim())) {
                warehouse.setStatus(0);
                warehouse.setUpdateTime(LocalDateTime.now());
                warehouseMapper.updateById(warehouse);
                disabled++;
            }
        }
        return disabled;
    }

    private BaseWarehouse findByErpCode(String erpCode) {
        return warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getErpWarehouseCode, erpCode));
    }

    private static String defaultWarehouseName(String name, String code) {
        return StringUtils.hasText(name) ? name.trim() : code;
    }

    private static String mapStockProperty(String stockProperty) {
        if (!StringUtils.hasText(stockProperty)) {
            return "RAW";
        }
        String value = stockProperty.trim().toUpperCase();
        if (value.contains("成品") || value.contains("FINISH")) {
            return "FINISHED";
        }
        if (value.contains("辅料") || value.contains("AUX")) {
            return "AUX";
        }
        return "RAW";
    }

    public void update(String warehouseCode, BaseWarehouse warehouse) {
        BaseWarehouse existing = getByCode(warehouseCode);
        warehouse.setId(existing.getId());
        warehouse.setWarehouseCode(warehouseCode);
        warehouseMapper.updateById(warehouse);
    }

    @Transactional
    public void delete(String warehouseCode) {
        BaseWarehouse existing = getByCode(warehouseCode);
        Long stockCount = inventoryMapper.selectCount(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouseCode)
                .gt(Inventory::getStockQty, java.math.BigDecimal.ZERO));
        if (stockCount != null && stockCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仓库仍有库存，无法删除");
        }
        List<BaseLocation> locations = locationMapper.selectList(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseCode, warehouseCode)
                .eq(BaseLocation::getDeleted, 0));
        for (BaseLocation loc : locations) {
            locationMapper.deleteById(loc.getId());
        }
        jdbcTemplate.update(
                "UPDATE base_warehouse_zone SET deleted = 1 WHERE warehouse_code = ? AND deleted = 0",
                warehouseCode);
        warehouseMapper.deleteById(existing.getId());
    }

    public Map<String, Object> utilization(String warehouseCode) {
        BaseWarehouse wh = getByCode(warehouseCode);
        long totalLocations = locationMapper.selectCount(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getWarehouseCode, warehouseCode)
                .eq(BaseLocation::getDeleted, 0));
        List<Inventory> stocks = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseCode, warehouseCode)
                .gt(Inventory::getStockQty, java.math.BigDecimal.ZERO));
        long occupiedLocations = stocks.stream().map(Inventory::getLocationCode)
                .filter(org.springframework.util.StringUtils::hasText).distinct().count();
        java.math.BigDecimal currentStock = stocks.stream()
                .map(Inventory::getStockQty)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal rated = wh.getRatedCapacity() != null ? wh.getRatedCapacity() : java.math.BigDecimal.ZERO;
        double usagePercent = totalLocations > 0 ? occupiedLocations * 100.0 / totalLocations : 0;
        double capacityPercent = rated.signum() > 0
                ? currentStock.multiply(java.math.BigDecimal.valueOf(100)).divide(rated, 2, java.math.RoundingMode.HALF_UP).doubleValue()
                : 0;
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("warehouseCode", warehouseCode);
        data.put("warehouseName", wh.getWarehouseName());
        data.put("areaSqm", wh.getAreaSqm());
        data.put("ratedCapacity", rated);
        data.put("currentStock", currentStock);
        data.put("totalLocations", totalLocations);
        data.put("occupiedLocations", occupiedLocations);
        data.put("locationUsagePercent", usagePercent);
        data.put("capacityUsagePercent", capacityPercent);
        return data;
    }
}
