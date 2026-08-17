package com.wms.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.dto.BarcodeRecognizeResult;
import com.wms.barcode.service.BarcodeRecognizeService;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.integration.kingdee.KingdeeReceiveBillService;
import com.wms.integration.kingdee.KingdeeStockCountService;
import com.wms.print.entity.LabelPrintJob;
import com.wms.print.mapper.LabelPrintJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDA 条码校验：先扫单据条码加载明细，再扫物料标签二维码匹配校验。
 */
@Service
@RequiredArgsConstructor
public class MobilePanelService {

    private final LabelPrintJobMapper labelPrintJobMapper;
    private final BarcodeRecognizeService barcodeRecognizeService;
    private final KingdeeReceiveBillService kingdeeReceiveBillService;
    private final KingdeeStockCountService kingdeeStockCountService;

    /**
     * 解析单据条码，得到单据编号。
     */
    public String resolveBillNo(String barcode) {
        if (!StringUtils.hasText(barcode)) {
            return "";
        }
        String raw = barcode.trim();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(raw);
            if (node.has("billNo")) {
                return node.get("billNo").asText("").trim();
            }
            if (node.has("sourceBillNo")) {
                return node.get("sourceBillNo").asText("").trim();
            }
            if (node.has("orderNo")) {
                return node.get("orderNo").asText("").trim();
            }
        } catch (Exception ignored) {
            // not json
        }
        String fromReceive = kingdeeReceiveBillService.parseBillNo(raw);
        if (StringUtils.hasText(fromReceive) && !fromReceive.equals(raw)) {
            return fromReceive.trim();
        }
        String fromStockCount = kingdeeStockCountService.parseBillNo(raw);
        if (StringUtils.hasText(fromStockCount) && !fromStockCount.equals(raw)) {
            return fromStockCount.trim();
        }
        var matcher = java.util.regex.Pattern.compile("(?i)(SLD\\d{6,}|RN\\d{6,}|PD\\d{6,}|WLPD\\d{6,})")
                .matcher(raw);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        if (raw.matches("(?i)(SLD|RN|PD|WLPD)\\d{6,}")) {
            return raw.toUpperCase();
        }
        return raw.trim();
    }

    /**
     * 按单据号加载待校验物料明细（来源：标签打印任务）。
     */
    public Map<String, Object> getBillDetail(String billNo) {
        String no = billNo != null ? billNo.trim() : "";
        if (!StringUtils.hasText(no)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单据号不能为空");
        }
        List<LabelPrintJob> jobs = labelPrintJobMapper.selectList(new LambdaQueryWrapper<LabelPrintJob>()
                .eq(LabelPrintJob::getSourceBillNo, no)
                .orderByAsc(LabelPrintJob::getMaterialCode)
                .orderByAsc(LabelPrintJob::getBatchNo));
        if (jobs.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到单据明细: " + no);
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        for (LabelPrintJob job : jobs) {
            lines.add(toLineMap(job, false));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("billNo", no);
        data.put("totalLines", lines.size());
        data.put("verifiedLines", 0);
        data.put("lines", lines);
        return data;
    }

    /**
     * 扫描物料条码，匹配单据明细并返回校验结果。
     */
    public Map<String, Object> verifyMaterial(String billNo, String barcodeContent) {
        String no = billNo != null ? billNo.trim() : "";
        if (!StringUtils.hasText(no)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先扫描单据条码");
        }
        if (!StringUtils.hasText(barcodeContent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "物料条码不能为空");
        }
        String raw = barcodeContent.trim();

        List<LabelPrintJob> jobs = labelPrintJobMapper.selectList(new LambdaQueryWrapper<LabelPrintJob>()
                .eq(LabelPrintJob::getSourceBillNo, no)
                .orderByAsc(LabelPrintJob::getMaterialCode));
        if (jobs.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到单据明细: " + no);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("billNo", no);
        result.put("barcodeContent", raw);
        result.put("verifyTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        LabelPrintJob matched = matchByExactBarcode(jobs, raw);
        BarcodeRecognizeResult recognized = null;
        if (matched == null) {
            try {
                recognized = barcodeRecognizeService.recognize(raw);
            } catch (BusinessException ex) {
                result.put("valid", false);
                result.put("result", "FAIL");
                result.put("message", "条码无法识别: " + ex.getMessage());
                return result;
            } catch (Exception e) {
                result.put("valid", false);
                result.put("result", "FAIL");
                result.put("message", "条码识别失败");
                return result;
            }
            matched = matchByRecognized(jobs, recognized, raw);
        }

        if (matched == null) {
            result.put("valid", false);
            result.put("result", "FAIL");
            result.put("parsedMaterialCode", recognized != null ? recognized.getMaterialCode() : null);
            result.put("parsedBatchNo", recognized != null ? recognized.getBatchNo() : null);
            result.put("message", buildNoMatchMessage(no, recognized));
            return result;
        }

        result.put("valid", true);
        result.put("result", "PASS");
        result.put("message", "校验通过，已匹配物料 " + matched.getMaterialCode());
        result.put("matchedLine", toLineMap(matched, true));
        if (recognized != null) {
            result.put("parsedMaterialCode", recognized.getMaterialCode());
            result.put("parsedBatchNo", recognized.getBatchNo());
            result.put("parsedQuantity", recognized.getQuantity());
        }
        return result;
    }

    private LabelPrintJob matchByExactBarcode(List<LabelPrintJob> jobs, String raw) {
        for (LabelPrintJob job : jobs) {
            if (StringUtils.hasText(job.getBarcodeContent()) && raw.equals(job.getBarcodeContent().trim())) {
                return job;
            }
        }
        return null;
    }

    private LabelPrintJob matchByRecognized(List<LabelPrintJob> jobs, BarcodeRecognizeResult recognized, String raw) {
        if (recognized == null) {
            return null;
        }
        String materialCode = StringUtils.hasText(recognized.getMaterialCode())
                ? recognized.getMaterialCode().trim() : null;
        String batchNo = StringUtils.hasText(recognized.getBatchNo())
                ? recognized.getBatchNo().trim() : null;

        // 物料+批次同时识别到时，必须双字段命中；禁止回退为「仅物料唯一」以免错批通过
        if (StringUtils.hasText(materialCode) && StringUtils.hasText(batchNo)) {
            return jobs.stream()
                    .filter(j -> materialCode.equalsIgnoreCase(j.getMaterialCode())
                            && batchNo.equalsIgnoreCase(nullToEmpty(j.getBatchNo())))
                    .findFirst()
                    .orElse(null);
        }

        if (StringUtils.hasText(materialCode)) {
            List<LabelPrintJob> byMat = jobs.stream()
                    .filter(j -> materialCode.equalsIgnoreCase(j.getMaterialCode()))
                    .toList();
            if (byMat.size() == 1) {
                return byMat.get(0);
            }
            return null;
        }

        return jobs.stream()
                .filter(j -> StringUtils.hasText(j.getMaterialCode())
                        && raw.contains(j.getMaterialCode()))
                .min(Comparator.comparing(LabelPrintJob::getMaterialCode))
                .orElse(null);
    }

    private String buildNoMatchMessage(String billNo, BarcodeRecognizeResult recognized) {
        StringBuilder sb = new StringBuilder("未匹配到单据 ").append(billNo).append(" 的物料明细");
        if (recognized != null && StringUtils.hasText(recognized.getMaterialCode())) {
            sb.append("，识别物料 ").append(recognized.getMaterialCode());
        }
        if (recognized != null && StringUtils.hasText(recognized.getBatchNo())) {
            sb.append("，批次 ").append(recognized.getBatchNo());
        }
        return sb.toString();
    }

    private Map<String, Object> toLineMap(LabelPrintJob job, boolean verified) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobId", job.getJobId());
        row.put("materialCode", job.getMaterialCode());
        row.put("materialName", job.getMaterialName());
        row.put("specification", job.getSpecification());
        row.put("batchNo", job.getBatchNo());
        row.put("quantity", job.getQuantity());
        row.put("unitCode", job.getUnitCode());
        row.put("barcodeContent", job.getBarcodeContent());
        row.put("verified", verified);
        return row;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v.trim();
    }
}
