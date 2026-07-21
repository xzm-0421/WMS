package com.wms.barcode.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.barcode.config.BarcodeArchiveProperties;
import com.wms.barcode.dto.BarcodeArchiveSaveCommand;
import com.wms.barcode.dto.BarcodeArchiveVo;
import com.wms.barcode.dto.BarcodeReprintResult;
import com.wms.barcode.entity.BarcodeArchive;
import com.wms.barcode.entity.BarcodeRule;
import com.wms.barcode.mapper.BarcodeArchiveMapper;
import com.wms.barcode.mapper.BarcodeRuleMapper;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BarcodeArchiveService {

    private static final BigDecimal DEFAULT_WIDTH = new BigDecimal("100");
    private static final BigDecimal DEFAULT_HEIGHT = new BigDecimal("55");

    private final BarcodeArchiveMapper archiveMapper;
    private final BarcodeRuleMapper ruleMapper;
    private final BaseMaterialMapper materialMapper;
    private final BarcodeArchiveProperties properties;

    @Transactional
    public BarcodeArchive archive(BarcodeArchiveSaveCommand cmd) {
        BarcodeArchive record = new BarcodeArchive();
        record.setArchiveNo(OrderNoGenerator.next("BA"));
        record.setBarcodeContent(cmd.getBarcodeContent());
        record.setBarcodeInstanceId(cmd.getBarcodeInstanceId());
        record.setRuleCode(cmd.getRuleCode());
        record.setVersionNo(cmd.getVersionNo());
        record.setMaterialCode(cmd.getMaterialCode());
        record.setBatchNo(cmd.getBatchNo());
        record.setSerialNo(cmd.getSerialNo());
        record.setPackBarcode(cmd.getPackBarcode());
        record.setBarcodeType(cmd.getBarcodeType());
        record.setTemplateCode(cmd.getTemplateCode());
        record.setLabelFormat(StringUtils.hasText(cmd.getLabelFormat()) ? cmd.getLabelFormat() : "barcode_archive");
        record.setLabelWidthMm(cmd.getLabelWidthMm() != null ? cmd.getLabelWidthMm() : DEFAULT_WIDTH);
        record.setLabelHeightMm(cmd.getLabelHeightMm() != null ? cmd.getLabelHeightMm() : DEFAULT_HEIGHT);
        record.setPrintParamsJson(buildPrintParamsJson(cmd));
        record.setActionType(StringUtils.hasText(cmd.getActionType()) ? cmd.getActionType() : "GENERATE");
        record.setReprintCount(0);
        record.setCreateTime(LocalDateTime.now());
        archiveMapper.insert(record);
        return record;
    }

    public PageResult<BarcodeArchiveVo> page(String archiveNo, String barcodeContent, String materialCode,
                                              String ruleCode, LocalDateTime createTimeFrom, LocalDateTime createTimeTo,
                                              long current, long size) {
        LambdaQueryWrapper<BarcodeArchive> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(archiveNo), BarcodeArchive::getArchiveNo, archiveNo)
                .like(StringUtils.hasText(barcodeContent), BarcodeArchive::getBarcodeContent, barcodeContent)
                .eq(StringUtils.hasText(materialCode), BarcodeArchive::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(ruleCode), BarcodeArchive::getRuleCode, ruleCode)
                .ge(createTimeFrom != null, BarcodeArchive::getCreateTime, createTimeFrom)
                .le(createTimeTo != null, BarcodeArchive::getCreateTime, createTimeTo)
                .orderByDesc(BarcodeArchive::getCreateTime);
        Page<BarcodeArchive> page = archiveMapper.selectPage(new Page<>(current, size), wrapper);
        List<BarcodeArchiveVo> rows = page.getRecords().stream().map(this::toVo).toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public BarcodeArchiveVo getByArchiveNo(String archiveNo) {
        BarcodeArchive archive = requireArchive(archiveNo);
        return toVo(archive);
    }

    @Transactional
    public BarcodeReprintResult reprint(String archiveNo) {
        BarcodeArchive archive = requireArchive(archiveNo);
        archive.setReprintCount((archive.getReprintCount() == null ? 0 : archive.getReprintCount()) + 1);
        archive.setLastReprintTime(LocalDateTime.now());
        archiveMapper.updateById(archive);
        return BarcodeReprintResult.builder()
                .archiveNo(archive.getArchiveNo())
                .barcodeContent(archive.getBarcodeContent())
                .barcodeType(archive.getBarcodeType())
                .labelFormat(archive.getLabelFormat())
                .reprintCount(archive.getReprintCount())
                .build();
    }

    public BarcodeArchive requireArchive(String archiveNo) {
        BarcodeArchive archive = archiveMapper.selectOne(new LambdaQueryWrapper<BarcodeArchive>()
                .eq(BarcodeArchive::getArchiveNo, archiveNo));
        if (archive == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "条码存档不存在: " + archiveNo, "ARCHIVE_NOT_FOUND");
        }
        return archive;
    }

    @Transactional
    public int cleanupExpired() {
        if (!properties.isCleanupEnabled()) {
            return 0;
        }
        int removed = 0;
        if (properties.getRetentionDays() > 0) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getRetentionDays());
            removed += archiveMapper.delete(new LambdaQueryWrapper<BarcodeArchive>()
                    .lt(BarcodeArchive::getCreateTime, cutoff));
        }
        if (properties.getMaxRecords() > 0) {
            Long total = archiveMapper.selectCount(null);
            if (total != null && total > properties.getMaxRecords()) {
                int excess = (int) (total - properties.getMaxRecords());
                Page<BarcodeArchive> oldestPage = archiveMapper.selectPage(
                        new Page<>(1, excess, false),
                        new LambdaQueryWrapper<BarcodeArchive>().orderByAsc(BarcodeArchive::getCreateTime));
                for (BarcodeArchive item : oldestPage.getRecords()) {
                    archiveMapper.deleteById(item.getId());
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("条码存档清理完成，删除 {} 条记录", removed);
        }
        return removed;
    }

    public BarcodeArchiveSaveCommand buildSaveCommandFromGenerate(
            BarcodeRule rule, Long instanceId, String content,
            String materialCode, String batchNo, String serialNo, String packBarcode,
            BigDecimal labelWidthMm, BigDecimal labelHeightMm, String labelFormat,
            Map<String, String> extraFields) {
        BarcodeArchiveSaveCommand cmd = new BarcodeArchiveSaveCommand();
        cmd.setBarcodeContent(content);
        cmd.setBarcodeInstanceId(instanceId);
        cmd.setRuleCode(rule.getRuleCode());
        cmd.setVersionNo(rule.getVersionNo());
        cmd.setMaterialCode(materialCode);
        cmd.setBatchNo(batchNo);
        cmd.setSerialNo(serialNo);
        cmd.setPackBarcode(packBarcode);
        cmd.setBarcodeType(rule.getBarcodeType());
        cmd.setTemplateCode(rule.getTemplateCode());
        cmd.setSeparator(rule.getSeparator());
        cmd.setSegmentsJson(rule.getSegmentsJson());
        cmd.setLabelFormat(labelFormat);
        cmd.setLabelWidthMm(labelWidthMm);
        cmd.setLabelHeightMm(labelHeightMm);
        cmd.setActionType("GENERATE");
        cmd.setExtraFields(extraFields);
        return cmd;
    }

    private String buildPrintParamsJson(BarcodeArchiveSaveCommand cmd) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("barcodeContent", cmd.getBarcodeContent());
        params.put("barcodeType", cmd.getBarcodeType());
        params.put("ruleCode", cmd.getRuleCode());
        params.put("versionNo", cmd.getVersionNo());
        params.put("templateCode", cmd.getTemplateCode());
        params.put("separator", cmd.getSeparator());
        params.put("segmentsJson", cmd.getSegmentsJson());
        params.put("labelFormat", StringUtils.hasText(cmd.getLabelFormat()) ? cmd.getLabelFormat() : "barcode_archive");
        params.put("labelWidthMm", cmd.getLabelWidthMm() != null ? cmd.getLabelWidthMm() : DEFAULT_WIDTH);
        params.put("labelHeightMm", cmd.getLabelHeightMm() != null ? cmd.getLabelHeightMm() : DEFAULT_HEIGHT);
        params.put("materialCode", cmd.getMaterialCode());
        params.put("batchNo", cmd.getBatchNo());
        params.put("serialNo", cmd.getSerialNo());
        params.put("packBarcode", cmd.getPackBarcode());
        if (cmd.getExtraFields() != null) {
            params.put("extraFields", cmd.getExtraFields());
        }
        return JSONUtil.toJsonStr(params);
    }

    private BarcodeArchiveVo toVo(BarcodeArchive archive) {
        BarcodeArchiveVo vo = new BarcodeArchiveVo();
        BeanUtils.copyProperties(archive, vo);
        if (StringUtils.hasText(archive.getMaterialCode())) {
            BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, archive.getMaterialCode()));
            if (material != null) {
                vo.setMaterialName(material.getMaterialName());
            }
        }
        if (!StringUtils.hasText(vo.getBarcodeType()) && StringUtils.hasText(archive.getRuleCode())) {
            BarcodeRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<BarcodeRule>()
                    .eq(BarcodeRule::getRuleCode, archive.getRuleCode()));
            if (rule != null) {
                vo.setBarcodeType(rule.getBarcodeType());
            }
        }
        return vo;
    }
}
