package com.wms.barcode.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.dto.BarcodeGenerateRequest;
import com.wms.barcode.dto.BarcodeGenerateResult;
import com.wms.barcode.entity.BarcodeArchive;
import com.wms.barcode.entity.BarcodeInstance;
import com.wms.barcode.entity.BarcodeRule;
import com.wms.barcode.entity.BarcodeTraceLink;
import com.wms.barcode.mapper.BarcodeInstanceMapper;
import com.wms.barcode.mapper.BarcodeRuleMapper;
import com.wms.barcode.mapper.BarcodeTraceLinkMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.integration.kingdee.KingdeeBarcodeSourceService;
import com.wms.integration.kingdee.dto.KingdeeMaterialBarcodeVo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BarcodeGenerateService {

    private final BarcodeRuleMapper ruleMapper;
    private final BarcodeInstanceMapper instanceMapper;
    private final BarcodeTraceLinkMapper traceLinkMapper;
    private final BarcodeSerialService serialService;
    private final KingdeeBarcodeSourceService kingdeeBarcodeSourceService;
    private final BarcodeArchiveService archiveService;

    @Transactional
    public BarcodeGenerateResult generate(BarcodeGenerateRequest request) {
        BarcodeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<BarcodeRule>()
                .eq(BarcodeRule::getRuleCode, request.getRuleCode()));
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "条码规则不存在");
        }
        if (rule.getStatus() != null && rule.getStatus() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码规则已禁用，无法生成");
        }

        Map<String, String> ctx = buildContext(request);
        boolean needsSerial = containsSerialSegment(rule);

        if (needsSerial) {
            if (Boolean.TRUE.equals(request.getAutoSerial()) || !StringUtils.hasText(ctx.get("SERIAL_NO"))) {
                String serial = serialService.allocateSerial(null,
                        ctx.get("MATERIAL_CODE"), ctx.get("BATCH_NO"), null);
                ctx.put("SERIAL_NO", serial);
            } else if (serialService.exists(ctx.get("SERIAL_NO"))) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "序列号「" + ctx.get("SERIAL_NO") + "」已存在", "SERIAL_DUPLICATE");
            }
        }

        String content = assemble(rule, ctx);
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码拼接结果为空，请检查分段配置与来源数据");
        }

        BarcodeInstance instance = new BarcodeInstance();
        instance.setBarcodeContent(content);
        instance.setRuleCode(rule.getRuleCode());
        instance.setVersionNo(rule.getVersionNo());
        instance.setMaterialCode(ctx.get("MATERIAL_CODE"));
        instance.setBatchNo(ctx.get("BATCH_NO"));
        instance.setSerialNo(ctx.get("SERIAL_NO"));
        instance.setPackBarcode(ctx.get("PACK_BARCODE"));
        instance.setBarcodeType(StringUtils.hasText(rule.getBarcodeType()) ? rule.getBarcodeType() : "QR");
        instance.setSourceType("GENERATED");
        instance.setStatus(1);
        try {
            instanceMapper.insert(instance);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "条码「" + content + "」已存在", "BARCODE_DUPLICATE");
        }

        if (needsSerial && StringUtils.hasText(ctx.get("SERIAL_NO"))) {
            if (Boolean.TRUE.equals(request.getAutoSerial()) || !StringUtils.hasText(request.getSerialNo())) {
                serialService.bindInstance(ctx.get("SERIAL_NO"), instance.getId());
            } else {
                serialService.allocateSerial(ctx.get("SERIAL_NO"),
                        ctx.get("MATERIAL_CODE"), ctx.get("BATCH_NO"), instance.getId());
            }
        }

        if (StringUtils.hasText(request.getRefType()) && StringUtils.hasText(request.getRefNo())) {
            linkTrace(content, instance.getId(), request.getRefType(), request.getRefNo(),
                    null, ctx.get("MATERIAL_CODE"), ctx.get("BATCH_NO"), ctx.get("SERIAL_NO"),
                    "条码生成关联");
        }

        BarcodeArchive archive = archiveService.archive(archiveService.buildSaveCommandFromGenerate(
                rule, instance.getId(), content,
                ctx.get("MATERIAL_CODE"), ctx.get("BATCH_NO"), ctx.get("SERIAL_NO"), ctx.get("PACK_BARCODE"),
                request.getLabelWidthMm(), request.getLabelHeightMm(), request.getLabelFormat(),
                request.getExtraFields()));

        return BarcodeGenerateResult.builder()
                .barcodeContent(content)
                .ruleCode(rule.getRuleCode())
                .versionNo(rule.getVersionNo())
                .materialCode(ctx.get("MATERIAL_CODE"))
                .batchNo(ctx.get("BATCH_NO"))
                .serialNo(ctx.get("SERIAL_NO"))
                .packBarcode(ctx.get("PACK_BARCODE"))
                .barcodeType(StringUtils.hasText(rule.getBarcodeType()) ? rule.getBarcodeType() : "QR")
                .instanceId(instance.getId())
                .archiveNo(archive.getArchiveNo())
                .build();
    }

    public String preview(BarcodeRule rule, Map<String, String> ctx) {
        return assemble(rule, ctx);
    }

    private Map<String, String> buildContext(BarcodeGenerateRequest request) {
        Map<String, String> ctx = new HashMap<>();
        if (request.getExtraFields() != null) {
            ctx.putAll(request.getExtraFields());
        }
        if (StringUtils.hasText(request.getMaterialCode())) {
            ctx.put("MATERIAL_CODE", request.getMaterialCode().trim());
            KingdeeMaterialBarcodeVo material = kingdeeBarcodeSourceService.getMaterial(request.getMaterialCode());
            if (material != null) {
                if (!StringUtils.hasText(ctx.get("PACK_BARCODE"))) {
                    ctx.put("PACK_BARCODE", firstNonBlank(material.getPackBarCode(), material.getBarCode()));
                }
            }
        }
        if (StringUtils.hasText(request.getBatchNo())) {
            ctx.put("BATCH_NO", request.getBatchNo().trim());
        }
        if (StringUtils.hasText(request.getPackBarcode())) {
            ctx.put("PACK_BARCODE", request.getPackBarcode().trim());
        }
        if (StringUtils.hasText(request.getSerialNo())) {
            ctx.put("SERIAL_NO", request.getSerialNo().trim());
        } else if (Boolean.TRUE.equals(request.getAutoSerial())) {
            ctx.put("SERIAL_NO", "");
        }
        return ctx;
    }

    private String assemble(BarcodeRule rule, Map<String, String> ctx) {
        if (!StringUtils.hasText(rule.getSegmentsJson())) {
            return ctx.getOrDefault("MATERIAL_CODE", "");
        }
        JSONArray segments = JSONUtil.parseArray(rule.getSegmentsJson());
        StringBuilder sb = new StringBuilder();
        for (Object item : segments) {
            JSONObject seg = (JSONObject) item;
            String type = seg.getStr("type", "FIELD");
            if ("SEPARATOR".equalsIgnoreCase(type)) {
                sb.append(seg.getStr("value", rule.getSeparator() != null ? rule.getSeparator() : ""));
            } else if ("FIXED".equalsIgnoreCase(type)) {
                sb.append(seg.getStr("value", ""));
            } else if ("DATE".equalsIgnoreCase(type)) {
                sb.append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            } else {
                String source = seg.getStr("source", seg.getStr("field", ""));
                String val = resolveSource(source, ctx);
                if (val != null) {
                    sb.append(val);
                }
            }
        }
        return sb.toString();
    }

    private String resolveSource(String source, Map<String, String> ctx) {
        if (!StringUtils.hasText(source)) {
            return "";
        }
        return switch (source.toUpperCase()) {
            case "MATERIAL_CODE", "MATERIAL" -> ctx.get("MATERIAL_CODE");
            case "BATCH_NO", "BATCH" -> ctx.get("BATCH_NO");
            case "SERIAL_NO", "SERIAL" -> ctx.get("SERIAL_NO");
            case "PACK_BARCODE", "PACK" -> ctx.get("PACK_BARCODE");
            default -> ctx.get(source);
        };
    }

    public void linkTrace(String barcodeContent, Long instanceId, String refType, String refNo,
                          String transactionNo, String materialCode, String batchNo, String serialNo, String remark) {
        BarcodeTraceLink link = new BarcodeTraceLink();
        link.setBarcodeContent(barcodeContent);
        link.setBarcodeInstanceId(instanceId);
        link.setRefType(refType);
        link.setRefNo(refNo);
        link.setTransactionNo(transactionNo);
        link.setMaterialCode(materialCode);
        link.setBatchNo(batchNo);
        link.setSerialNo(serialNo);
        link.setRemark(remark);
        traceLinkMapper.insert(link);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private boolean containsSerialSegment(BarcodeRule rule) {
        if (!StringUtils.hasText(rule.getSegmentsJson())) {
            return false;
        }
        JSONArray segments = JSONUtil.parseArray(rule.getSegmentsJson());
        for (Object item : segments) {
            JSONObject seg = (JSONObject) item;
            String source = seg.getStr("source", seg.getStr("field", ""));
            if ("SERIAL_NO".equalsIgnoreCase(source) || "SERIAL".equalsIgnoreCase(source)) {
                return true;
            }
        }
        return false;
    }
}
