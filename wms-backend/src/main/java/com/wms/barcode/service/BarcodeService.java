package com.wms.barcode.service;



import cn.hutool.json.JSONArray;

import cn.hutool.json.JSONObject;

import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.wms.barcode.constant.BarcodeTemplates;

import com.wms.barcode.dto.BarcodeParseRequest;

import com.wms.barcode.entity.BarcodeRule;

import com.wms.barcode.entity.BarcodeRuleVersion;

import com.wms.barcode.mapper.BarcodeRuleMapper;

import com.wms.barcode.mapper.BarcodeRuleVersionMapper;

import com.wms.common.constant.ErrorCode;

import com.wms.common.exception.BusinessException;

import com.wms.common.result.PageResult;

import com.wms.common.util.CodeAutoGenerator;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;



import java.time.LocalDateTime;

import java.util.HashMap;

import java.util.List;

import java.util.Map;



@Service

@RequiredArgsConstructor

public class BarcodeService {



    private final BarcodeRuleMapper ruleMapper;

    private final BarcodeRuleVersionMapper versionMapper;



    public PageResult<BarcodeRule> page(String ruleCode, String ruleName, String appliesTo, Integer status,

                                        long current, long size) {

        LambdaQueryWrapper<BarcodeRule> wrapper = new LambdaQueryWrapper<>();

        wrapper.like(StringUtils.hasText(ruleCode), BarcodeRule::getRuleCode, ruleCode)

                .like(StringUtils.hasText(ruleName), BarcodeRule::getRuleName, ruleName)

                .eq(StringUtils.hasText(appliesTo), BarcodeRule::getAppliesTo, appliesTo)

                .eq(status != null, BarcodeRule::getStatus, status)

                .orderByDesc(BarcodeRule::getId);

        Page<BarcodeRule> page = ruleMapper.selectPage(new Page<>(current, size), wrapper);

        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());

    }



    public BarcodeRule getByCode(String ruleCode) {

        BarcodeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<BarcodeRule>()

                .eq(BarcodeRule::getRuleCode, ruleCode));

        if (rule == null) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "条码规则不存在", "RULE_NOT_FOUND");

        }

        return rule;

    }



    public List<BarcodeRuleVersion> listVersions(String ruleCode) {

        getByCode(ruleCode);

        return versionMapper.selectList(new LambdaQueryWrapper<BarcodeRuleVersion>()

                .eq(BarcodeRuleVersion::getRuleCode, ruleCode)

                .orderByDesc(BarcodeRuleVersion::getVersionNo));

    }



    public BarcodeRuleVersion getVersion(String ruleCode, int versionNo) {

        BarcodeRuleVersion version = versionMapper.selectOne(new LambdaQueryWrapper<BarcodeRuleVersion>()

                .eq(BarcodeRuleVersion::getRuleCode, ruleCode)

                .eq(BarcodeRuleVersion::getVersionNo, versionNo));

        if (version == null) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "规则版本不存在");

        }

        return version;

    }



    @Transactional

    public void create(BarcodeRule rule) {

        rule.setRuleCode(CodeAutoGenerator.ensureOrGenerate(rule.getRuleCode(), "BC"));

        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<BarcodeRule>()

                .eq(BarcodeRule::getRuleCode, rule.getRuleCode()));

        if (count > 0) {

            throw new BusinessException(ErrorCode.CONFLICT, "规则编码已存在", "RULE_CODE_EXISTS");

        }

        applyTemplateDefaults(rule);

        if (rule.getStatus() == null) {

            rule.setStatus(1);

        }

        rule.setVersionNo(1);

        LocalDateTime now = LocalDateTime.now();

        rule.setCreateTime(now);

        rule.setUpdateTime(now);

        ruleMapper.insert(rule);

        saveVersionSnapshot(rule, "初始版本");

    }



    @Transactional

    public void update(String ruleCode, BarcodeRule rule, String changeLog) {

        BarcodeRule existing = getByCode(ruleCode);

        applyTemplateDefaults(rule);

        int nextVersion = (existing.getVersionNo() == null ? 1 : existing.getVersionNo()) + 1;

        rule.setId(existing.getId());

        rule.setRuleCode(ruleCode);

        rule.setVersionNo(nextVersion);

        rule.setCreateTime(existing.getCreateTime());

        rule.setUpdateTime(LocalDateTime.now());

        ruleMapper.updateById(rule);

        saveVersionSnapshot(rule, StringUtils.hasText(changeLog) ? changeLog : "规则更新 v" + nextVersion);

    }



    @Transactional

    public void updateStatus(String ruleCode, int status, String changeLog) {

        BarcodeRule existing = getByCode(ruleCode);

        existing.setStatus(status);

        existing.setUpdateTime(LocalDateTime.now());

        if (StringUtils.hasText(changeLog)) {

            existing.setRemark(changeLog);

        }

        ruleMapper.updateById(existing);

    }



    public void delete(String ruleCode) {

        BarcodeRule existing = getByCode(ruleCode);

        ruleMapper.deleteById(existing.getId());

    }



    public Map<String, Object> parse(BarcodeParseRequest request) {

        BarcodeRule rule = getByCode(request.getRuleCode());

        String content = request.getBarcodeContent();

        if (!StringUtils.hasText(content)) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码内容不能为空");

        }

        Map<String, Object> result = new HashMap<>();

        result.put("ruleCode", rule.getRuleCode());

        result.put("versionNo", rule.getVersionNo());

        result.put("barcodeContent", content);

        result.put("barcodeType", rule.getBarcodeType());

        if (!StringUtils.hasText(rule.getSegmentsJson())) {

            result.put("segments", Map.of("raw", content));

            return result;

        }

        JSONArray segments = JSONUtil.parseArray(rule.getSegmentsJson());

        Map<String, String> parsed = new HashMap<>();

        int offset = 0;

        for (Object item : segments) {

            JSONObject seg = (JSONObject) item;

            String type = seg.getStr("type", "FIELD");

            if ("SEPARATOR".equalsIgnoreCase(type) || "FIXED".equalsIgnoreCase(type)) {

                String sep = seg.getStr("value", "");

                if (StringUtils.hasText(sep) && content.startsWith(sep, offset)) {

                    offset += sep.length();

                }

                continue;

            }

            String field = seg.getStr("source", seg.getStr("field", "field"));

            int length = seg.getInt("length", 0);

            String separator = seg.getStr("separator", rule.getSeparator());

            if (length > 0 && offset + length <= content.length()) {

                parsed.put(normalizeField(field), content.substring(offset, offset + length));

                offset += length;

            } else if (StringUtils.hasText(separator)) {

                int idx = content.indexOf(separator, offset);

                if (idx > offset) {

                    parsed.put(normalizeField(field), content.substring(offset, idx));

                    offset = idx + separator.length();

                } else if (offset < content.length()) {

                    parsed.put(normalizeField(field), content.substring(offset));

                    offset = content.length();

                }

            } else if (offset < content.length()) {

                parsed.put(normalizeField(field), content.substring(offset));

                offset = content.length();

            }

        }

        if (parsed.isEmpty()) {

            parsed.put("raw", content);

        }

        result.put("segments", parsed);

        result.put("materialCode", parsed.get("MATERIAL_CODE"));

        result.put("batchNo", parsed.get("BATCH_NO"));

        result.put("serialNo", parsed.get("SERIAL_NO"));

        result.put("packBarcode", parsed.get("PACK_BARCODE"));

        return result;

    }



    public Map<String, String> templateOptions() {

        return BarcodeTemplates.LABELS;

    }

    /** 按规则解析条码分段（运行时扫码识别用） */
    public Map<String, String> parseSegments(BarcodeRule rule, String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }
        if (!StringUtils.hasText(rule.getSegmentsJson())) {
            return Map.of("raw", content.trim());
        }
        JSONArray segments = JSONUtil.parseArray(rule.getSegmentsJson());
        Map<String, String> parsed = new HashMap<>();
        int offset = 0;
        String trimmed = content.trim();
        for (Object item : segments) {
            JSONObject seg = (JSONObject) item;
            String type = seg.getStr("type", "FIELD");
            if ("SEPARATOR".equalsIgnoreCase(type) || "FIXED".equalsIgnoreCase(type)) {
                String sep = seg.getStr("value", "");
                if (StringUtils.hasText(sep) && trimmed.startsWith(sep, offset)) {
                    offset += sep.length();
                }
                continue;
            }
            String field = seg.getStr("source", seg.getStr("field", "field"));
            int length = seg.getInt("length", 0);
            String separator = seg.getStr("separator", rule.getSeparator());
            if (length > 0 && offset + length <= trimmed.length()) {
                parsed.put(normalizeField(field), trimmed.substring(offset, offset + length));
                offset += length;
            } else if (StringUtils.hasText(separator)) {
                int idx = trimmed.indexOf(separator, offset);
                if (idx > offset) {
                    parsed.put(normalizeField(field), trimmed.substring(offset, idx));
                    offset = idx + separator.length();
                } else if (offset < trimmed.length()) {
                    parsed.put(normalizeField(field), trimmed.substring(offset));
                    offset = trimmed.length();
                }
            } else if (offset < trimmed.length()) {
                parsed.put(normalizeField(field), trimmed.substring(offset));
                offset = trimmed.length();
            }
        }
        if (parsed.isEmpty()) {
            parsed.put("raw", trimmed);
        }
        return parsed;
    }



    private void applyTemplateDefaults(BarcodeRule rule) {

        if (StringUtils.hasText(rule.getTemplateCode())

                && !BarcodeTemplates.CUSTOM.equals(rule.getTemplateCode())

                && !StringUtils.hasText(rule.getSegmentsJson())) {

            rule.setSegmentsJson(BarcodeTemplates.buildSegmentsJson(

                    rule.getTemplateCode(), rule.getSeparator()));

        }

        if (rule.getSeparator() == null) {

            rule.setSeparator("");

        }

        if (!StringUtils.hasText(rule.getBarcodeType())) {

            rule.setBarcodeType("QR");

        }

    }



    private void saveVersionSnapshot(BarcodeRule rule, String changeLog) {

        BarcodeRuleVersion version = new BarcodeRuleVersion();

        version.setRuleCode(rule.getRuleCode());

        version.setVersionNo(rule.getVersionNo());

        version.setRuleName(rule.getRuleName());

        version.setAppliesTo(rule.getAppliesTo());

        version.setBarcodeType(rule.getBarcodeType());

        version.setTemplateCode(rule.getTemplateCode());

        version.setSeparator(rule.getSeparator());

        version.setSegmentsJson(rule.getSegmentsJson());

        version.setDescription(rule.getDescription());

        version.setStatus(rule.getStatus());

        version.setChangeLog(changeLog);

        versionMapper.insert(version);

    }



    private static String normalizeField(String field) {

        if (!StringUtils.hasText(field)) {

            return "field";

        }

        return field.toUpperCase();

    }

}


