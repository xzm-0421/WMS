package com.wms.integration.kingdee;

import com.wms.base.service.BaseMaterialService;
import com.wms.base.service.BaseWarehouseService;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.integration.kingdee.dto.KingdeeMasterDataSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从金蝶云星空拉取物料、仓库主数据并写入 WMS 本地表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeMasterDataSyncService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;
    private final BaseMaterialService materialService;
    private final BaseWarehouseService warehouseService;

    public KingdeeMasterDataSyncResult syncMaterials(String keyword) {
        requireKingdeeEnabled();
        int pageSize = Math.max(1, properties.getMasterDataQueryLimit());
        String filter = buildMasterDataFilter(keyword);
        int start = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int fetched = 0;
        Set<String> syncedMaterialCodes = new HashSet<>();

        while (true) {
            List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                    properties.getMaterialFormId(),
                    properties.getMaterialSyncFieldKeys(),
                    filter,
                    "FNumber",
                    start,
                    pageSize);
            if (rows.isEmpty()) {
                break;
            }
            for (List<String> row : rows) {
                fetched++;
                String code = cell(row, 0);
                if (!StringUtils.hasText(code)) {
                    skipped++;
                    continue;
                }
                boolean active = isActiveRow(row, 6, 7);
                String erpCode = code.trim();
                syncedMaterialCodes.add(erpCode);
                String action = materialService.upsertFromKingdee(
                        erpCode,
                        cell(row, 1),
                        cell(row, 2),
                        cell(row, 3),
                        isTrue(cell(row, 4)),
                        isTrue(cell(row, 5)),
                        active);
                if ("INSERTED".equals(action)) {
                    inserted++;
                } else if ("UPDATED".equals(action)) {
                    updated++;
                } else {
                    skipped++;
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        int removed = 0;
        if (!StringUtils.hasText(keyword)) {
            removed = materialService.removeStaleLocalMaterials(syncedMaterialCodes);
        }

        String message = String.format("物料同步完成：新增 %d，更新 %d，跳过 %d", inserted, updated, skipped);
        if (removed > 0) {
            message += String.format("，清理旧物料 %d", removed);
        }
        return KingdeeMasterDataSyncResult.builder()
                .totalFetched(fetched)
                .inserted(inserted)
                .updated(updated)
                .skipped(skipped)
                .message(message)
                .build();
    }

    public KingdeeMasterDataSyncResult syncWarehouses(String keyword) {
        requireKingdeeEnabled();
        int pageSize = Math.max(1, properties.getMasterDataQueryLimit());
        String filter = buildMasterDataFilter(keyword);
        int start = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int fetched = 0;
        Set<String> syncedErpCodes = new HashSet<>();

        while (true) {
            List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                    properties.getWarehouseFormId(),
                    properties.getWarehouseFieldKeys(),
                    filter,
                    "FNumber",
                    start,
                    pageSize);
            if (rows.isEmpty()) {
                break;
            }
            for (List<String> row : rows) {
                fetched++;
                String code = cell(row, 0);
                if (!StringUtils.hasText(code)) {
                    skipped++;
                    continue;
                }
                String erpCode = code.trim();
                syncedErpCodes.add(erpCode);
                boolean active = isActiveRow(row, 3, 4);
                String action = warehouseService.upsertFromKingdee(
                        erpCode,
                        cell(row, 1),
                        cell(row, 2),
                        active);
                if ("INSERTED".equals(action)) {
                    inserted++;
                } else if ("UPDATED".equals(action)) {
                    updated++;
                } else {
                    skipped++;
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }

        int disabled = 0;
        if (!StringUtils.hasText(keyword)) {
            disabled = warehouseService.disableStaleKingdeeWarehouses(syncedErpCodes);
        }

        String message = String.format("仓库同步完成：新增 %d，覆盖更新 %d，跳过 %d", inserted, updated, skipped);
        if (disabled > 0) {
            message += String.format("，禁用 %d", disabled);
        }
        return KingdeeMasterDataSyncResult.builder()
                .totalFetched(fetched)
                .inserted(inserted)
                .updated(updated)
                .skipped(skipped)
                .message(message)
                .build();
    }

    public Map<String, Object> syncAll(String keyword) {
        KingdeeMasterDataSyncResult materials = syncMaterials(keyword);
        KingdeeMasterDataSyncResult warehouses = syncWarehouses(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("materials", materials);
        result.put("warehouses", warehouses);
        result.put("message", materials.getMessage() + "；" + warehouses.getMessage());
        return result;
    }

    private void requireKingdeeEnabled() {
        if (!kingdeeCloudService.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金蝶对接未启用，请在配置中开启 kingdee.cloud.enabled");
        }
    }

    private String buildMasterDataFilter(String keyword) {
        StringBuilder filter = new StringBuilder();
        if (properties.isMasterDataApprovedOnly()) {
            filter.append("FDocumentStatus='C' and FForbidStatus='A'");
        }
        if (StringUtils.hasText(keyword)) {
            if (!filter.isEmpty()) {
                filter.append(" and ");
            }
            String kw = escapeFilter(keyword.trim());
            filter.append("(FNumber like '%").append(kw).append("%' or FName like '%").append(kw).append("%')");
        }
        return filter.toString();
    }

    private boolean isActiveRow(List<String> row, int forbidIndex, int docStatusIndex) {
        if (!properties.isMasterDataApprovedOnly()) {
            return !"B".equalsIgnoreCase(cell(row, forbidIndex));
        }
        return "A".equalsIgnoreCase(cell(row, forbidIndex))
                && "C".equalsIgnoreCase(cell(row, docStatusIndex));
    }

    private static String cell(List<String> row, int index) {
        return row != null && index >= 0 && index < row.size() ? row.get(index) : "";
    }

    private static String escapeFilter(String value) {
        return value.replace("'", "''");
    }

    private static boolean isTrue(String val) {
        return "1".equals(val) || "true".equalsIgnoreCase(val);
    }
}
