package com.wms.barcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.dto.BarcodeRecognizeResult;
import com.wms.barcode.entity.BarcodeInstance;
import com.wms.barcode.entity.BarcodeRule;
import com.wms.barcode.mapper.BarcodeInstanceMapper;
import com.wms.barcode.mapper.BarcodeRuleMapper;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.print.entity.LabelPrintJob;
import com.wms.print.mapper.LabelPrintJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 运行时条码识别：条码实例 → 物料绑定规则 → 全量启用规则 → 启发式兜底。
 */
@Service
@RequiredArgsConstructor
public class BarcodeRecognizeService {

    private final BarcodeInstanceMapper instanceMapper;
    private final BarcodeRuleMapper ruleMapper;
    private final BarcodeService barcodeService;
    private final BaseMaterialMapper materialMapper;
    private final LabelPrintJobMapper labelPrintJobMapper;

    public BarcodeRecognizeResult recognize(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码不能为空");
        }
        String raw = content.trim();
        boolean strictQty = raw.contains("|") && raw.split("\\|").length >= 3;

        if (raw.matches("(?i)^WH\\d{2}.*")) {
            return BarcodeRecognizeResult.builder()
                    .raw(raw)
                    .locationCode(raw)
                    .parseMode("LOCATION")
                    .build();
        }

        BarcodeInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<BarcodeInstance>()
                .eq(BarcodeInstance::getBarcodeContent, raw)
                .eq(BarcodeInstance::getStatus, 1));
        if (instance != null) {
            BarcodeRecognizeResult result = BarcodeRecognizeResult.builder()
                    .raw(raw)
                    .materialCode(instance.getMaterialCode())
                    .batchNo(instance.getBatchNo())
                    .serialNo(instance.getSerialNo())
                    .packBarcode(instance.getPackBarcode())
                    .ruleCode(instance.getRuleCode())
                    .versionNo(instance.getVersionNo())
                    .instanceId(instance.getId())
                    .parseMode("INSTANCE")
                    .build();
            enrichQuantity(raw, result, strictQty);
            return result;
        }

        BarcodeRecognizeResult heuristic = parseHeuristic(raw);

        if (StringUtils.hasText(heuristic.getMaterialCode())) {
            BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, heuristic.getMaterialCode()));
            if (material != null && StringUtils.hasText(material.getBarcodeRule())) {
                BarcodeRecognizeResult fromRule = tryParseWithRuleCode(material.getBarcodeRule(), raw);
                if (fromRule != null) {
                    enrichQuantity(raw, fromRule, strictQty);
                    return fromRule;
                }
            }
        }

        List<BarcodeRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<BarcodeRule>()
                .eq(BarcodeRule::getStatus, 1)
                .orderByDesc(BarcodeRule::getId));
        for (BarcodeRule rule : rules) {
            BarcodeRecognizeResult fromRule = tryParseWithRule(rule, raw);
            if (fromRule != null && materialExists(fromRule.getMaterialCode())) {
                enrichQuantity(raw, fromRule, strictQty);
                return fromRule;
            }
        }

        heuristic.setParseMode("HEURISTIC");
        enrichQuantity(raw, heuristic, strictQty);
        return heuristic;
    }

    /**
     * 解析扫码领料数量：二维码内嵌数量 → 标签打印任务数量 → 默认 1。
     */
    public BigDecimal resolveScanQuantity(String content, BarcodeRecognizeResult recognized) {
        if (recognized != null && recognized.getQuantity() != null
                && recognized.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return recognized.getQuantity();
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码不能为空", "BARCODE_EMPTY");
        }
        String raw = content.trim();
        BarcodeQuantityParser.QuantityExtract extract =
                BarcodeQuantityParser.extract(raw, raw.contains("|") && raw.split("\\|").length >= 3);
        if (extract.hasQuantity()) {
            return extract.quantity();
        }
        BigDecimal fromJob = lookupLabelJobQuantity(raw,
                recognized != null ? recognized.getMaterialCode() : null,
                recognized != null ? recognized.getBatchNo() : null);
        if (fromJob != null) {
            return fromJob;
        }
        return BigDecimal.ONE;
    }

    private void enrichQuantity(String raw, BarcodeRecognizeResult result, boolean strictQty) {
        if (result.getQuantity() != null && result.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return;
        }
        BarcodeQuantityParser.QuantityExtract extract = BarcodeQuantityParser.extract(raw, strictQty);
        if (extract.hasQuantity()) {
            result.setQuantity(extract.quantity());
            result.setQuantitySource(extract.source());
            return;
        }
        BigDecimal fromJob = lookupLabelJobQuantity(raw, result.getMaterialCode(), result.getBatchNo());
        if (fromJob != null) {
            result.setQuantity(fromJob);
            result.setQuantitySource("LABEL_JOB");
        }
    }

    private BigDecimal lookupLabelJobQuantity(String raw, String materialCode, String batchNo) {
        LabelPrintJob job = labelPrintJobMapper.selectOne(new LambdaQueryWrapper<LabelPrintJob>()
                .eq(LabelPrintJob::getBarcodeContent, raw)
                .orderByDesc(LabelPrintJob::getCreateTime)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (job != null && isPositive(job.getQuantity())) {
            return job.getQuantity();
        }
        if (StringUtils.hasText(materialCode) && StringUtils.hasText(batchNo)) {
            String concat = materialCode.trim() + batchNo.trim();
            job = labelPrintJobMapper.selectOne(new LambdaQueryWrapper<LabelPrintJob>()
                    .eq(LabelPrintJob::getMaterialCode, materialCode.trim())
                    .eq(LabelPrintJob::getBatchNo, batchNo.trim())
                    .orderByDesc(LabelPrintJob::getCreateTime)
                    .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
            if (job != null && isPositive(job.getQuantity())) {
                return job.getQuantity();
            }
            job = labelPrintJobMapper.selectOne(new LambdaQueryWrapper<LabelPrintJob>()
                    .eq(LabelPrintJob::getBarcodeContent, concat)
                    .orderByDesc(LabelPrintJob::getCreateTime)
                    .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
            if (job != null && isPositive(job.getQuantity())) {
                return job.getQuantity();
            }
        }
        return null;
    }

    private boolean isPositive(BigDecimal qty) {
        return qty != null && qty.compareTo(BigDecimal.ZERO) > 0;
    }

    private BarcodeRecognizeResult tryParseWithRuleCode(String ruleCode, String raw) {
        BarcodeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<BarcodeRule>()
                .eq(BarcodeRule::getRuleCode, ruleCode)
                .eq(BarcodeRule::getStatus, 1));
        if (rule == null) {
            return null;
        }
        return tryParseWithRule(rule, raw);
    }

    private BarcodeRecognizeResult tryParseWithRule(BarcodeRule rule, String raw) {
        Map<String, String> segments = barcodeService.parseSegments(rule, raw);
        String materialCode = firstNonBlank(segments.get("MATERIAL_CODE"), segments.get("MATERIAL"));
        if (!StringUtils.hasText(materialCode)) {
            return null;
        }
        String qtyText = firstNonBlank(segments.get("QTY"), segments.get("QUANTITY"), segments.get("PACK_QTY"));
        BigDecimal quantity = StringUtils.hasText(qtyText) ? BarcodeQuantityParser.parseToken(qtyText, false) : null;
        return BarcodeRecognizeResult.builder()
                .raw(raw)
                .materialCode(materialCode)
                .batchNo(firstNonBlank(segments.get("BATCH_NO"), segments.get("BATCH")))
                .serialNo(firstNonBlank(segments.get("SERIAL_NO"), segments.get("SERIAL")))
                .packBarcode(firstNonBlank(segments.get("PACK_BARCODE"), segments.get("PACK")))
                .ruleCode(rule.getRuleCode())
                .versionNo(rule.getVersionNo())
                .parseMode("RULE")
                .quantity(quantity)
                .quantitySource(quantity != null ? "RULE" : null)
                .build();
    }

    private boolean materialExists(String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            return false;
        }
        return materialMapper.selectCount(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, materialCode)) > 0;
    }

    private BarcodeRecognizeResult parseHeuristic(String raw) {
        BarcodeRecognizeResult.BarcodeRecognizeResultBuilder builder = BarcodeRecognizeResult.builder().raw(raw);

        if (raw.startsWith("{") && raw.endsWith("}")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
                if (node.isObject()) {
                    applyJsonFields(node, builder);
                    BarcodeRecognizeResult built = builder.build();
                    if (StringUtils.hasText(built.getMaterialCode())
                            || StringUtils.hasText(built.getMaterialName())) {
                        return built;
                    }
                }
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ignored) {
                // fall through
            }
        }

        BarcodeQuantityParser.QuantityExtract suffixQty = BarcodeQuantityParser.extract(raw, false);
        String body = raw;
        if (suffixQty.source() != null && "SUFFIX".equals(suffixQty.source())) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(.+?)[*#@xX](\\d+(?:\\.\\d+)?)$").matcher(raw);
            if (m.matches()) {
                body = m.group(1);
            }
        }

        if (body.contains("|")) {
            String[] parts = body.split("\\|", -1);
            if (parts.length >= 6) {
                builder.materialCode(parts[0].trim());
                builder.materialName(parts[1].trim());
                builder.specification(parts[2].trim());
                builder.batchNo(parts[3].trim());
                builder.unitCode(parts[5].trim());
                builder.quantity(BarcodeQuantityParser.parseToken(parts[4].trim(), true));
                builder.quantitySource("PIPE");
                return builder.build();
            }
            if (parts.length == 5 && parts[4].trim().matches("\\d+(?:\\.\\d+)?")) {
                builder.materialCode(parts[0].trim());
                builder.materialName(parts[1].trim());
                builder.specification(parts[2].trim());
                builder.batchNo(parts[3].trim());
                builder.quantity(BarcodeQuantityParser.parseToken(parts[4].trim(), true));
                builder.quantitySource("PIPE");
                return builder.build();
            }
            builder.materialCode(parts[0].trim());
            if (parts.length == 2) {
                String second = parts[1].trim();
                if (second.matches("\\d+(?:\\.\\d+)?")) {
                    builder.quantity(BarcodeQuantityParser.parseToken(second, false));
                    builder.quantitySource("PIPE");
                } else {
                    builder.batchNo(second);
                }
            } else if (parts.length >= 3) {
                builder.batchNo(parts[1].trim());
                builder.quantity(BarcodeQuantityParser.parseToken(parts[parts.length - 1].trim(), true));
                builder.quantitySource("PIPE");
            }
            return builder.build();
        }
        // 金蝶/标签打印常见格式：MAT-10001B20260701（物料编码 + 批次号）
        java.util.regex.Matcher matBatch = java.util.regex.Pattern
                .compile("^(MAT-\\d{5})(B\\d{8})$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (matBatch.matches()) {
            return builder.materialCode(matBatch.group(1).toUpperCase())
                    .batchNo(matBatch.group(2).toUpperCase())
                    .quantity(suffixQty.hasQuantity() ? suffixQty.quantity() : null)
                    .quantitySource(suffixQty.source())
                    .build();
        }
        java.util.regex.Matcher generic = java.util.regex.Pattern
                .compile("^([A-Z]+-\\d+)(B\\d+)$", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (generic.matches()) {
            return builder.materialCode(generic.group(1).toUpperCase())
                    .batchNo(generic.group(2).toUpperCase())
                    .quantity(suffixQty.hasQuantity() ? suffixQty.quantity() : null)
                    .quantitySource(suffixQty.source())
                    .build();
        }
        if (body.length() > 11 && body.matches("^[A-Z0-9]{11}.+")) {
            builder.materialCode(body.substring(0, 11));
            builder.batchNo(body.substring(11));
        } else {
            builder.materialCode(body);
        }
        if (suffixQty.hasQuantity()) {
            builder.quantity(suffixQty.quantity());
            builder.quantitySource(suffixQty.source());
        }
        return builder.build();
    }

    private void applyJsonFields(com.fasterxml.jackson.databind.JsonNode node,
                                 BarcodeRecognizeResult.BarcodeRecognizeResultBuilder builder) {
        String materialCode = firstJsonText(node,
                "materialCode", "FNumber", "FMaterialId", "materialNo", "code", "物料编码");
        if (StringUtils.hasText(materialCode)) {
            builder.materialCode(materialCode);
        }
        String materialName = firstJsonText(node,
                "materialName", "FName", "name", "物料名称");
        if (StringUtils.hasText(materialName)) {
            builder.materialName(materialName);
        }
        String specification = firstJsonText(node,
                "specification", "FModel", "FSpecification", "spec", "model", "规格型号", "规格");
        if (StringUtils.hasText(specification)) {
            builder.specification(specification);
        }
        String batchNo = firstJsonText(node,
                "batchNo", "FLot", "batch", "lot", "批号", "批次号");
        if (StringUtils.hasText(batchNo)) {
            builder.batchNo(batchNo);
        }
        String unitCode = firstJsonText(node,
                "unitCode", "FUnitID", "FBaseUnit", "unit", "单位");
        if (StringUtils.hasText(unitCode)) {
            builder.unitCode(unitCode);
        }
        String qtyText = firstJsonText(node,
                "quantity", "qty", "FQty", "数量");
        if (StringUtils.hasText(qtyText)) {
            builder.quantity(BarcodeQuantityParser.parseToken(qtyText, true));
            builder.quantitySource("JSON");
        }
    }

    private static String firstJsonText(com.fasterxml.jackson.databind.JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String text = node.get(key).asText("").trim();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }
}
