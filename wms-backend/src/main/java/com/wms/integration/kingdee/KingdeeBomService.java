package com.wms.integration.kingdee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.dto.KingdeeMasterDataSyncResult;
import com.wms.production.dto.BomVo;
import com.wms.production.entity.BomDetail;
import com.wms.production.entity.BomHeader;
import com.wms.production.mapper.BomDetailMapper;
import com.wms.production.mapper.BomHeaderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从金蝶云星空查询 BOM；未启用金蝶时回退本地 bom 表（仅开发联调）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeBomService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;
    private final BomHeaderMapper bomHeaderMapper;
    private final BomDetailMapper bomDetailMapper;

    public PageResult<BomHeader> pageBom(String bomCode, String productCode, long current, long size) {
        if (kingdeeCloudService.isEnabled()) {
            return pageFromKingdee(bomCode, productCode, current, size);
        }
        log.debug("Kingdee disabled, BOM list from local DB");
        return pageFromLocal(bomCode, productCode, current, size);
    }

    public BomVo getBom(String bomCode) {
        if (!StringUtils.hasText(bomCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "BOM编码不能为空");
        }
        if (kingdeeCloudService.isEnabled()) {
            return getFromKingdeeByCode(bomCode);
        }
        return getFromLocal(bomCode);
    }

    /** 取产品最新已审核、未禁用的 BOM（生产领料展开用） */
    public BomVo getEffectiveBomByProductCode(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "产品编码不能为空");
        }
        if (kingdeeCloudService.isEnabled()) {
            BomVo vo = getFromKingdeeByProduct(productCode);
            if (vo == null || vo.getDetails() == null || vo.getDetails().isEmpty()) {
                throw new BusinessException(ErrorCode.NOT_FOUND,
                        "金蝶未找到产品「" + productCode + "」的有效 BOM（已审核且未禁用）");
            }
            return vo;
        }
        log.debug("Kingdee disabled, effective BOM from local DB for product {}", productCode);
        BomHeader header = bomHeaderMapper.selectOne(new LambdaQueryWrapper<BomHeader>()
                .eq(BomHeader::getProductCode, productCode)
                .eq(BomHeader::getStatus, 1)
                .orderByDesc(BomHeader::getCreateTime)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (header == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "未找到产品「" + productCode + "」的有效 BOM，请在金蝶云星空维护或启用金蝶对接");
        }
        return getFromLocal(header.getBomCode());
    }

    public PageResult<BomHeader> pageLocal(String bomCode, String productCode, long current, long size) {
        return pageFromLocal(bomCode, productCode, current, size);
    }

    public BomVo getLocal(String bomCode) {
        return getFromLocal(bomCode);
    }

    /**
     * 通过 executeBillQuery 拉取 ENG_BOM 并写入本地 bom_header / bom_detail。
     */
    public KingdeeMasterDataSyncResult syncAllToLocal() {
        if (!kingdeeCloudService.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金蝶对接未启用");
        }
        int pageSize = Math.max(1, properties.getMasterDataQueryLimit());
        int start = 0;
        int fetched = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        Set<String> seen = new LinkedHashSet<>();
        while (true) {
            List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                    properties.getBomFormId(),
                    properties.getBomListFieldKeys(),
                    "FDocumentStatus='C' and FForbidStatus='A'",
                    "FNumber",
                    start,
                    pageSize);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (List<String> row : rows) {
                fetched++;
                BomHeader listed = mapListRow(row);
                if (listed == null || !StringUtils.hasText(listed.getBomCode()) || !seen.add(listed.getBomCode())) {
                    skipped++;
                    continue;
                }
                try {
                    BomVo vo = getFromKingdeeByCode(listed.getBomCode());
                    if ("INSERTED".equals(upsertLocal(vo))) {
                        inserted++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    log.warn("BOM sync skip code={} msg={}", listed.getBomCode(), e.getMessage());
                    skipped++;
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }
        return KingdeeMasterDataSyncResult.builder()
                .totalFetched(fetched)
                .inserted(inserted)
                .updated(updated)
                .skipped(skipped)
                .message(String.format("BOM同步完成：拉取 %d，新增 %d，更新 %d，跳过 %d",
                        fetched, inserted, updated, skipped))
                .build();
    }

    private String upsertLocal(BomVo vo) {
        BomHeader header = vo.getHeader();
        if (!StringUtils.hasText(header.getProductCode())) {
            header.setProductCode("-");
        }
        if (!StringUtils.hasText(header.getVersionNo())) {
            header.setVersionNo("V1");
        }
        BomHeader existing = bomHeaderMapper.selectOne(new LambdaQueryWrapper<BomHeader>()
                .eq(BomHeader::getBomCode, header.getBomCode()));
        if (existing == null) {
            if (header.getCreateTime() == null) {
                header.setCreateTime(java.time.LocalDateTime.now());
            }
            bomHeaderMapper.insert(header);
        } else {
            existing.setProductCode(header.getProductCode());
            existing.setVersionNo(header.getVersionNo());
            existing.setStatus(header.getStatus() == null ? 1 : header.getStatus());
            bomHeaderMapper.updateById(existing);
        }
        bomDetailMapper.delete(new LambdaQueryWrapper<BomDetail>().eq(BomDetail::getBomCode, header.getBomCode()));
        if (vo.getDetails() != null) {
            for (BomDetail detail : vo.getDetails()) {
                detail.setId(null);
                detail.setBomCode(header.getBomCode());
                if (!StringUtils.hasText(detail.getUnitCode())) {
                    detail.setUnitCode("-");
                }
                if (detail.getQtyPer() == null) {
                    detail.setQtyPer(java.math.BigDecimal.ONE);
                }
                bomDetailMapper.insert(detail);
            }
        }
        return existing == null ? "INSERTED" : "UPDATED";
    }

    private PageResult<BomHeader> pageFromKingdee(String bomCode, String productCode, long current, long size) {
        StringBuilder filter = new StringBuilder("FDocumentStatus='C' and FForbidStatus='A'");
        if (StringUtils.hasText(bomCode)) {
            filter.append(" and FNumber like '%").append(escapeFilter(bomCode)).append("%'");
        }
        if (StringUtils.hasText(productCode)) {
            filter.append(" and FMaterialId.FNumber='").append(escapeFilter(productCode)).append("'");
        }
        int start = (int) Math.max(0, (current - 1) * size);
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getBomFormId(),
                properties.getBomListFieldKeys(),
                filter.toString(),
                "FNumber desc",
                start,
                (int) size);
        List<BomHeader> records = new ArrayList<>();
        for (List<String> row : rows) {
            BomHeader h = mapListRow(row);
            if (h != null) {
                records.add(h);
            }
        }
        long total = records.size() < size ? start + records.size() : start + size + 1;
        return PageResult.of(records, total, current, size);
    }

    private BomVo getFromKingdeeByCode(String bomCode) {
        String filter = "FNumber='" + escapeFilter(bomCode) + "' and FDocumentStatus='C' and FForbidStatus='A'";
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getBomFormId(),
                properties.getBomDetailFieldKeys(),
                filter,
                "FTreeEntity_FSEQ asc",
                0,
                properties.getBomQueryLimit());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "金蝶 BOM 不存在: " + bomCode);
        }
        return mapDetailRows(rows);
    }

    private BomVo getFromKingdeeByProduct(String productCode) {
        String filter = "FMaterialId.FNumber='" + escapeFilter(productCode)
                + "' and FDocumentStatus='C' and FForbidStatus='A'";
        List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                properties.getBomFormId(),
                properties.getBomDetailFieldKeys(),
                filter,
                "FNumber desc,FTreeEntity_FSEQ asc",
                0,
                properties.getBomQueryLimit());
        if (rows.isEmpty()) {
            return null;
        }
        return mapDetailRows(rows);
    }

    private BomVo mapDetailRows(List<List<String>> rows) {
        List<String> first = rows.get(0);
        String[] listKeys = properties.getBomDetailFieldKeys().split(",");
        Map<String, Integer> idx = indexOf(listKeys);

        BomHeader header = new BomHeader();
        header.setBomCode(val(first, idx, "FNumber", "FBillNo"));
        header.setProductCode(val(first, idx, "FMaterialId.FNumber", "FMaterialID.FNumber"));
        header.setVersionNo(val(first, idx, "FBOMVERSION", "FBOMID", "FVersion"));
        header.setStatus(1);

        String bomCode = header.getBomCode();
        List<BomDetail> details = new ArrayList<>();
        int lineNo = 1;
        for (List<String> row : rows) {
            if (!bomCode.equals(val(row, idx, "FNumber"))) {
                continue;
            }
            String childCode = val(row, idx,
                    "FTreeEntity_FMaterialIdChild.FNumber",
                    "SubHeadEntity.FMaterialID2.FNumber",
                    "SubHeadEntity_FMaterialID2.FNumber");
            if (!StringUtils.hasText(childCode)) {
                continue;
            }
            BomDetail d = new BomDetail();
            d.setBomCode(bomCode);
            d.setLineNo(parseInt(val(row, idx, "FTreeEntity_FSEQ", "SubHeadEntity.FSeq", "SubHeadEntity_FSeq"), lineNo));
            d.setMaterialCode(childCode);
            d.setMaterialName(val(row, idx,
                    "FTreeEntity_FMaterialIdChild.FName",
                    "SubHeadEntity.FMaterialID2.FName",
                    "SubHeadEntity_FMaterialID2.FName"));
            d.setUnitCode(val(row, idx, "FTreeEntity_FUnitID.FNumber"));
            d.setQtyPer(calcQtyPer(
                    val(row, idx, "FTreeEntity_FNumerator", "SubHeadEntity.FNumerator", "SubHeadEntity_FNumerator"),
                    val(row, idx, "FTreeEntity_FDenominator", "SubHeadEntity.FDenominator", "SubHeadEntity_FDenominator")));
            details.add(d);
            lineNo++;
        }
        if (details.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "金蝶 BOM 无明细: " + bomCode);
        }
        BomVo vo = new BomVo();
        vo.setHeader(header);
        vo.setDetails(details);
        return vo;
    }

    private BomHeader mapListRow(List<String> row) {
        String[] listKeys = properties.getBomListFieldKeys().split(",");
        Map<String, Integer> idx = indexOf(listKeys);
        String bomCode = val(row, idx, "FNumber");
        if (!StringUtils.hasText(bomCode)) {
            return null;
        }
        BomHeader h = new BomHeader();
        h.setBomCode(bomCode);
        h.setProductCode(val(row, idx, "FMaterialId.FNumber", "FMaterialID.FNumber"));
        h.setVersionNo(val(row, idx, "FBOMVERSION", "FBOMID", "FVersion"));
        h.setStatus("A".equals(val(row, idx, "FForbidStatus")) ? 1 : 0);
        return h;
    }

    private PageResult<BomHeader> pageFromLocal(String bomCode, String productCode, long current, long size) {
        LambdaQueryWrapper<BomHeader> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(bomCode), BomHeader::getBomCode, bomCode)
                .eq(StringUtils.hasText(productCode), BomHeader::getProductCode, productCode)
                .orderByDesc(BomHeader::getCreateTime);
        Page<BomHeader> page = bomHeaderMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private BomVo getFromLocal(String bomCode) {
        BomHeader header = bomHeaderMapper.selectOne(new LambdaQueryWrapper<BomHeader>()
                .eq(BomHeader::getBomCode, bomCode));
        if (header == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "BOM不存在: " + bomCode);
        }
        BomVo vo = new BomVo();
        vo.setHeader(header);
        vo.setDetails(bomDetailMapper.selectList(new LambdaQueryWrapper<BomDetail>()
                .eq(BomDetail::getBomCode, bomCode)
                .orderByAsc(BomDetail::getLineNo)));
        return vo;
    }

    private static Map<String, Integer> indexOf(String[] keys) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i].trim(), i);
        }
        return map;
    }

    private static String val(List<String> row, Map<String, Integer> idx, String... keys) {
        if (keys == null) {
            return "";
        }
        for (String key : keys) {
            Integer i = findIndex(idx, key);
            if (i == null || i >= row.size()) {
                continue;
            }
            String v = row.get(i);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static Integer findIndex(Map<String, Integer> idx, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        Integer i = idx.get(key);
        if (i != null) {
            return i;
        }
        for (Map.Entry<String, Integer> entry : idx.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static BigDecimal calcQtyPer(String numerator, String denominator) {
        try {
            BigDecimal num = new BigDecimal(StringUtils.hasText(numerator) ? numerator : "1");
            BigDecimal den = new BigDecimal(StringUtils.hasText(denominator) ? denominator : "1");
            if (den.compareTo(BigDecimal.ZERO) == 0) {
                return num;
            }
            return num.divide(den, 8, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return BigDecimal.ONE;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String escapeFilter(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
