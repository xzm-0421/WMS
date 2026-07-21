package com.wms.base.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.base.dto.ZoneCreateRequest;
import com.wms.base.entity.BaseLocation;
import com.wms.base.entity.BaseWarehouse;
import com.wms.base.mapper.BaseLocationMapper;
import com.wms.base.mapper.BaseWarehouseMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BaseLocationService {

    private final BaseLocationMapper locationMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final InventoryMapper inventoryMapper;
    private final JdbcTemplate jdbcTemplate;

    public PageResult<BaseLocation> page(String locationCode, String warehouseCode,
                                         String zoneCode, Integer status,
                                         long current, long size) {
        LambdaQueryWrapper<BaseLocation> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(locationCode), BaseLocation::getLocationCode, locationCode)
                .eq(StringUtils.hasText(warehouseCode), BaseLocation::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(zoneCode), BaseLocation::getZoneCode, zoneCode)
                .eq(status != null, BaseLocation::getStatus, status)
                .orderByDesc(BaseLocation::getCreateTime);
        Page<BaseLocation> page = locationMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public BaseLocation getByCode(String locationCode) {
        BaseLocation location = locationMapper.selectOne(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getLocationCode, locationCode));
        if (location == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "库位不存在", "LOCATION_NOT_FOUND");
        }
        return location;
    }

    public void create(BaseLocation location) {
        if (!StringUtils.hasText(location.getLocationCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库位编码不能为空");
        }
        if (!StringUtils.hasText(location.getWarehouseCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所属仓库不能为空");
        }
        location.setLocationCode(location.getLocationCode().trim());
        location.setWarehouseCode(location.getWarehouseCode().trim());
        if (!StringUtils.hasText(location.getZoneCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所属区域不能为空");
        }
        location.setZoneCode(location.getZoneCode().trim());
        assertZoneExists(location.getWarehouseCode(), location.getZoneCode());
        if (!StringUtils.hasText(location.getLocationName())) {
            location.setLocationName(location.getLocationCode());
        }
        if (!StringUtils.hasText(location.getLocationType())) {
            location.setLocationType("STORAGE");
        }
        if (location.getStatus() == null) {
            location.setStatus(1);
        }
        if (!StringUtils.hasText(location.getOccupyStatus())) {
            location.setOccupyStatus("IDLE");
        }
        if (location.getMixedMaterial() == null) {
            location.setMixedMaterial(0);
        }
        if (location.getMixedBatch() == null) {
            location.setMixedBatch(1);
        }
        Long count = locationMapper.selectCount(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getLocationCode, location.getLocationCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "库位编码已存在", "LOCATION_CODE_EXISTS");
        }
        locationMapper.insert(location);
    }

    public void createZone(ZoneCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getWarehouseCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所属仓库不能为空");
        }
        if (!StringUtils.hasText(request.getZoneCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "区域编码不能为空");
        }
        String warehouseCode = request.getWarehouseCode().trim();
        String zoneCode = request.getZoneCode().trim();
        String zoneName = StringUtils.hasText(request.getZoneName())
                ? request.getZoneName().trim() : zoneCode;

        BaseWarehouse warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                .eq(BaseWarehouse::getWarehouseCode, warehouseCode)
                .eq(BaseWarehouse::getDeleted, 0));
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在", "WAREHOUSE_NOT_FOUND");
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id, deleted FROM base_warehouse_zone WHERE warehouse_code = ? AND zone_code = ?",
                warehouseCode, zoneCode);
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.get(0);
            int deleted = row.get("deleted") instanceof Number n ? n.intValue() : 0;
            if (deleted == 0) {
                throw new BusinessException(ErrorCode.CONFLICT, "该区域编码已存在", "ZONE_CODE_EXISTS");
            }
            Long id = row.get("id") instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(row.get("id")));
            jdbcTemplate.update(
                    "UPDATE base_warehouse_zone SET zone_name = ?, status = 1, deleted = 0 WHERE id = ?",
                    zoneName, id);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO base_warehouse_zone (zone_code, zone_name, warehouse_code, status, deleted) VALUES (?, ?, ?, 1, 0)",
                zoneCode, zoneName, warehouseCode);
    }

    private void assertZoneExists(String warehouseCode, String zoneCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM base_warehouse_zone WHERE warehouse_code = ? AND zone_code = ? AND deleted = 0",
                Integer.class, warehouseCode, zoneCode);
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "所属区域不存在，请先在仓库下创建区域");
        }
    }

    public void update(String locationCode, BaseLocation location) {
        BaseLocation existing = getByCode(locationCode);
        location.setId(existing.getId());
        location.setLocationCode(locationCode);
        locationMapper.updateById(location);
    }

    public void delete(String locationCode) {
        BaseLocation existing = getByCode(locationCode);
        locationMapper.deleteById(existing.getId());
    }

    /** 库位树：仓库 > 区域 > 库位（含无库位的仓库/区域节点） */
    public List<Map<String, Object>> tree(String warehouseCode) {
        LambdaQueryWrapper<BaseWarehouse> whWrapper = new LambdaQueryWrapper<>();
        whWrapper.eq(StringUtils.hasText(warehouseCode), BaseWarehouse::getWarehouseCode, warehouseCode)
                .eq(BaseWarehouse::getDeleted, 0)
                .orderByAsc(BaseWarehouse::getWarehouseCode);
        List<BaseWarehouse> warehouses = warehouseMapper.selectList(whWrapper);

        String zoneSql = "SELECT zone_code, zone_name, warehouse_code FROM base_warehouse_zone WHERE deleted = 0";
        List<Map<String, Object>> zoneRows = StringUtils.hasText(warehouseCode)
                ? jdbcTemplate.queryForList(zoneSql + " AND warehouse_code = ?", warehouseCode)
                : jdbcTemplate.queryForList(zoneSql);

        LambdaQueryWrapper<BaseLocation> locWrapper = new LambdaQueryWrapper<>();
        locWrapper.eq(StringUtils.hasText(warehouseCode), BaseLocation::getWarehouseCode, warehouseCode)
                .eq(BaseLocation::getDeleted, 0)
                .orderByAsc(BaseLocation::getWarehouseCode, BaseLocation::getZoneCode, BaseLocation::getLocationCode);
        List<BaseLocation> allLocations = locationMapper.selectList(locWrapper);
        syncOccupyStatus(allLocations);

        Map<String, Map<String, Object>> warehouseNodes = new LinkedHashMap<>();
        for (BaseWarehouse wh : warehouses) {
            warehouseNodes.put(wh.getWarehouseCode(), buildWarehouseNode(wh));
        }

        Set<String> activeWarehouseCodes = warehouseNodes.keySet();

        for (Map<String, Object> zoneRow : zoneRows) {
            String wh = jdbcString(zoneRow, "warehouse_code");
            if (!StringUtils.hasText(wh) || !activeWarehouseCodes.contains(wh)) {
                continue;
            }
            Map<String, Object> whNode = warehouseNodes.get(wh);
            String zoneKey = jdbcString(zoneRow, "zone_code");
            String zoneName = jdbcString(zoneRow, "zone_name");
            findOrCreateZoneNode(whNode, wh, zoneKey, zoneName != null ? zoneName : zoneKey);
        }

        for (BaseLocation loc : allLocations) {
            String wh = loc.getWarehouseCode();
            if (!StringUtils.hasText(wh) || !activeWarehouseCodes.contains(wh)) {
                continue;
            }
            Map<String, Object> whNode = warehouseNodes.get(wh);
            String zoneKey = StringUtils.hasText(loc.getZoneCode()) ? loc.getZoneCode() : "DEFAULT";
            Map<String, Object> zoneNode = findOrCreateZoneNode(whNode, wh, zoneKey, zoneKey);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> locs = (List<Map<String, Object>>) zoneNode.get("children");
            Map<String, Object> locNode = new LinkedHashMap<>();
            locNode.put("id", loc.getLocationCode());
            locNode.put("label", loc.getLocationName() != null ? loc.getLocationName() : loc.getLocationCode());
            locNode.put("type", "location");
            locNode.put("warehouseCode", wh);
            locNode.put("zoneCode", zoneKey);
            locNode.put("occupyStatus", loc.getOccupyStatus());
            locNode.put("locationType", loc.getLocationType());
            locNode.put("rowNo", loc.getRowNo());
            locNode.put("colNo", loc.getColNo());
            locNode.put("layerNo", loc.getLayerNo());
            locs.add(locNode);
        }
        return new ArrayList<>(warehouseNodes.values());
    }

    private Map<String, Object> findOrCreateZoneNode(Map<String, Object> whNode, String warehouseCode,
                                                     String zoneKey, String zoneLabel) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> zones = (List<Map<String, Object>>) whNode.get("children");
        return zones.stream()
                .filter(z -> zoneKey.equals(z.get("id")))
                .findFirst()
                .orElseGet(() -> {
                    Map<String, Object> zn = new LinkedHashMap<>();
                    zn.put("id", zoneKey);
                    zn.put("label", formatZoneLabel(zoneKey, zoneLabel));
                    zn.put("type", "zone");
                    zn.put("warehouseCode", warehouseCode);
                    zn.put("zoneCode", zoneKey);
                    zn.put("zoneName", zoneLabel);
                    zn.put("children", new ArrayList<Map<String, Object>>());
                    zones.add(zn);
                    return zn;
                });
    }

    private Map<String, Object> buildWarehouseNode(BaseWarehouse wh) {
        Map<String, Object> whNode = new LinkedHashMap<>();
        whNode.put("id", "WH-" + wh.getWarehouseCode());
        whNode.put("label", formatWarehouseLabel(wh.getWarehouseCode(), wh.getWarehouseName()));
        whNode.put("type", "warehouse");
        whNode.put("warehouseCode", wh.getWarehouseCode());
        whNode.put("warehouseName", StringUtils.hasText(wh.getWarehouseName()) ? wh.getWarehouseName() : wh.getWarehouseCode());
        whNode.put("children", new ArrayList<Map<String, Object>>());
        return whNode;
    }

    private String formatWarehouseLabel(String code, String name) {
        String displayName = StringUtils.hasText(name) ? name : code;
        return "【仓库】" + displayName + " (" + code + ")";
    }

    private String formatZoneLabel(String zoneKey, String zoneLabel) {
        String displayName = StringUtils.hasText(zoneLabel) ? zoneLabel : zoneKey;
        return "【区域】" + displayName + " (" + zoneKey + ")";
    }

    private String jdbcString(Map<String, Object> row, String column) {
        if (row == null || column == null) {
            return null;
        }
        Object value = row.get(column);
        if (value == null) {
            value = row.get(column.toUpperCase());
        }
        if (value == null) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) {
                    value = entry.getValue();
                    break;
                }
            }
        }
        return value != null ? String.valueOf(value) : null;
    }

    public String generateQrContent(String locationCode) {
        BaseLocation loc = getByCode(locationCode);
        if (!StringUtils.hasText(loc.getQrCode())) {
            loc.setQrCode("LOC:" + locationCode);
            locationMapper.updateById(loc);
        }
        return loc.getQrCode();
    }

    private void syncOccupyStatus(List<BaseLocation> locations) {
        if (locations.isEmpty()) return;
        Set<String> occupied = inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                        .gt(Inventory::getStockQty, BigDecimal.ZERO))
                .stream().map(Inventory::getLocationCode).filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        for (BaseLocation loc : locations) {
            String status = occupied.contains(loc.getLocationCode()) ? "OCCUPIED" : "IDLE";
            if (!status.equals(loc.getOccupyStatus())) {
                loc.setOccupyStatus(status);
                locationMapper.updateById(loc);
            }
        }
    }
}
