package com.wms.integration.kingdee;

import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.dto.KingdeeBatchVo;
import com.wms.integration.kingdee.dto.KingdeeMaterialBarcodeVo;
import com.wms.integration.kingdee.dto.KingdeeSerialVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 从金蝶云星空抓取物料条码、批号、包装条码、序列号主档数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeBarcodeSourceService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;

    public PageResult<KingdeeMaterialBarcodeVo> pageMaterials(String keyword, long current, long size) {
        if (kingdeeCloudService.isEnabled()) {
            return pageMaterialsFromKingdee(keyword, current, size);
        }
        return pageMaterialsMock(keyword, current, size);
    }

    public PageResult<KingdeeBatchVo> pageBatches(String materialCode, String keyword, long current, long size) {
        if (kingdeeCloudService.isEnabled()) {
            return pageBatchesFromKingdee(materialCode, keyword, current, size);
        }
        return pageBatchesMock(materialCode, keyword, current, size);
    }

    public PageResult<KingdeeSerialVo> pageSerials(String materialCode, String batchNo, String keyword,
                                                    long current, long size) {
        if (kingdeeCloudService.isEnabled()) {
            return pageSerialsFromKingdee(materialCode, batchNo, keyword, current, size);
        }
        return pageSerialsMock(materialCode, batchNo, keyword, current, size);
    }

    public KingdeeMaterialBarcodeVo getMaterial(String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            return null;
        }
        PageResult<KingdeeMaterialBarcodeVo> page = pageMaterials(materialCode, 1, 20);
        return page.getRecords().stream()
                .filter(m -> materialCode.equalsIgnoreCase(m.getMaterialCode()))
                .findFirst()
                .orElse(page.getRecords().isEmpty() ? null : page.getRecords().get(0));
    }

    private PageResult<KingdeeMaterialBarcodeVo> pageMaterialsFromKingdee(String keyword, long current, long size) {
        String filter = buildLikeFilter("FNumber", keyword);
        if (StringUtils.hasText(keyword)) {
            filter = filter + " or FName like '%" + escapeFilter(keyword) + "%'";
        }
        int start = (int) Math.max(0, (current - 1) * size);
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getMaterialFormId(),
                properties.getMaterialFieldKeys(),
                filter,
                "FNumber",
                start,
                (int) size);
        List<KingdeeMaterialBarcodeVo> list = new ArrayList<>();
        for (List<String> row : rows) {
            if (row.size() < 2) {
                continue;
            }
            list.add(KingdeeMaterialBarcodeVo.builder()
                    .materialCode(row.get(0))
                    .materialName(row.size() > 1 ? row.get(1) : "")
                    .barCode(row.size() > 2 ? row.get(2) : "")
                    .packBarCode(row.size() > 2 ? row.get(2) : "")
                    .batchManaged(row.size() > 3 && isTrue(row.get(3)))
                    .serialManaged(row.size() > 4 && isTrue(row.get(4)))
                    .build());
        }
        return PageResult.of(list, list.size(), current, size);
    }

    private PageResult<KingdeeBatchVo> pageBatchesFromKingdee(String materialCode, String keyword,
                                                               long current, long size) {
        StringBuilder filter = new StringBuilder();
        if (StringUtils.hasText(materialCode)) {
            filter.append("FMaterialId.FNumber='").append(escapeFilter(materialCode)).append("'");
        }
        if (StringUtils.hasText(keyword)) {
            if (!filter.isEmpty()) {
                filter.append(" and ");
            }
            filter.append("FNumber like '%").append(escapeFilter(keyword)).append("%'");
        }
        int start = (int) Math.max(0, (current - 1) * size);
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getBatchFormId(),
                properties.getBatchFieldKeys(),
                filter.toString(),
                "FNumber",
                start,
                (int) size);
        List<KingdeeBatchVo> list = new ArrayList<>();
        for (List<String> row : rows) {
            list.add(KingdeeBatchVo.builder()
                    .batchNo(row.get(0))
                    .materialCode(row.size() > 1 ? row.get(1) : materialCode)
                    .materialName(row.size() > 2 ? row.get(2) : "")
                    .status("ACTIVE")
                    .build());
        }
        return PageResult.of(list, list.size(), current, size);
    }

    private PageResult<KingdeeSerialVo> pageSerialsFromKingdee(String materialCode, String batchNo, String keyword,
                                                                long current, long size) {
        StringBuilder filter = new StringBuilder();
        if (StringUtils.hasText(materialCode)) {
            filter.append("FMaterialId.FNumber='").append(escapeFilter(materialCode)).append("'");
        }
        if (StringUtils.hasText(batchNo)) {
            if (!filter.isEmpty()) {
                filter.append(" and ");
            }
            filter.append("FLot.FNumber='").append(escapeFilter(batchNo)).append("'");
        }
        if (StringUtils.hasText(keyword)) {
            if (!filter.isEmpty()) {
                filter.append(" and ");
            }
            filter.append("FNumber like '%").append(escapeFilter(keyword)).append("%'");
        }
        int start = (int) Math.max(0, (current - 1) * size);
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getSerialFormId(),
                properties.getSerialFieldKeys(),
                filter.toString(),
                "FNumber",
                start,
                (int) size);
        List<KingdeeSerialVo> list = new ArrayList<>();
        for (List<String> row : rows) {
            list.add(KingdeeSerialVo.builder()
                    .serialNo(row.get(0))
                    .materialCode(row.size() > 1 ? row.get(1) : materialCode)
                    .batchNo(row.size() > 2 ? row.get(2) : batchNo)
                    .status("AVAILABLE")
                    .build());
        }
        return PageResult.of(list, list.size(), current, size);
    }

    private PageResult<KingdeeMaterialBarcodeVo> pageMaterialsMock(String keyword, long current, long size) {
        List<KingdeeMaterialBarcodeVo> all = List.of(
                KingdeeMaterialBarcodeVo.builder()
                        .materialCode("MAT001").materialName("示例原材料A")
                        .barCode("6901234567890").packBarCode("PKG-MAT001")
                        .batchManaged(true).serialManaged(false).build(),
                KingdeeMaterialBarcodeVo.builder()
                        .materialCode("MAT002").materialName("示例成品B")
                        .barCode("6909876543210").packBarCode("PKG-MAT002")
                        .batchManaged(true).serialManaged(true).build()
        );
        List<KingdeeMaterialBarcodeVo> filtered = all.stream()
                .filter(m -> !StringUtils.hasText(keyword)
                        || m.getMaterialCode().contains(keyword)
                        || (m.getMaterialName() != null && m.getMaterialName().contains(keyword)))
                .toList();
        return PageResult.of(filtered, filtered.size(), current, size);
    }

    private PageResult<KingdeeBatchVo> pageBatchesMock(String materialCode, String keyword,
                                                        long current, long size) {
        String mat = StringUtils.hasText(materialCode) ? materialCode : "MAT001";
        List<KingdeeBatchVo> all = List.of(
                KingdeeBatchVo.builder().batchNo("B20260101").materialCode(mat).materialName("批次A").status("ACTIVE").build(),
                KingdeeBatchVo.builder().batchNo("B20260102").materialCode(mat).materialName("批次B").status("ACTIVE").build()
        );
        List<KingdeeBatchVo> filtered = all.stream()
                .filter(b -> !StringUtils.hasText(keyword) || b.getBatchNo().contains(keyword))
                .toList();
        return PageResult.of(filtered, filtered.size(), current, size);
    }

    private PageResult<KingdeeSerialVo> pageSerialsMock(String materialCode, String batchNo, String keyword,
                                                         long current, long size) {
        String mat = StringUtils.hasText(materialCode) ? materialCode : "MAT002";
        String batch = StringUtils.hasText(batchNo) ? batchNo : "B20260101";
        List<KingdeeSerialVo> all = List.of(
                KingdeeSerialVo.builder().serialNo("SN00000001").materialCode(mat).batchNo(batch).status("AVAILABLE").build(),
                KingdeeSerialVo.builder().serialNo("SN00000002").materialCode(mat).batchNo(batch).status("AVAILABLE").build()
        );
        List<KingdeeSerialVo> filtered = all.stream()
                .filter(s -> !StringUtils.hasText(keyword) || s.getSerialNo().contains(keyword))
                .toList();
        return PageResult.of(filtered, filtered.size(), current, size);
    }

    private static String buildLikeFilter(String field, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return "";
        }
        return field + " like '%" + escapeFilter(keyword) + "%'";
    }

    private static String escapeFilter(String value) {
        return value.replace("'", "''");
    }

    private static boolean isTrue(String val) {
        return "1".equals(val) || "true".equalsIgnoreCase(val);
    }
}
