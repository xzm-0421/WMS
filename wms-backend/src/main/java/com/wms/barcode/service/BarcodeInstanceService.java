package com.wms.barcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.dto.BarcodeArchiveSaveCommand;
import com.wms.barcode.dto.BarcodeInstanceUpdateRequest;
import com.wms.barcode.entity.BarcodeArchive;
import com.wms.barcode.entity.BarcodeInstance;
import com.wms.barcode.entity.BarcodeRule;
import com.wms.barcode.mapper.BarcodeInstanceMapper;
import com.wms.barcode.mapper.BarcodeRuleMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BarcodeInstanceService {

    private final BarcodeInstanceMapper instanceMapper;
    private final BarcodeRuleMapper ruleMapper;
    private final BarcodeArchiveService archiveService;

    public BarcodeInstance getById(Long id) {
        BarcodeInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "条码实例不存在", "INSTANCE_NOT_FOUND");
        }
        return instance;
    }

    @Transactional
    public BarcodeArchive update(Long id, BarcodeInstanceUpdateRequest req) {
        BarcodeInstance instance = getById(id);
        if (StringUtils.hasText(req.getMaterialCode())) {
            instance.setMaterialCode(req.getMaterialCode());
        }
        if (req.getBatchNo() != null) {
            instance.setBatchNo(req.getBatchNo());
        }
        if (req.getSerialNo() != null) {
            instance.setSerialNo(req.getSerialNo());
        }
        if (req.getPackBarcode() != null) {
            instance.setPackBarcode(req.getPackBarcode());
        }
        if (req.getStatus() != null) {
            instance.setStatus(req.getStatus());
        }
        instanceMapper.updateById(instance);

        BarcodeRule rule = null;
        if (StringUtils.hasText(instance.getRuleCode())) {
            rule = ruleMapper.selectOne(new LambdaQueryWrapper<BarcodeRule>()
                    .eq(BarcodeRule::getRuleCode, instance.getRuleCode()));
        }

        BarcodeArchiveSaveCommand cmd = new BarcodeArchiveSaveCommand();
        cmd.setBarcodeContent(instance.getBarcodeContent());
        cmd.setBarcodeInstanceId(instance.getId());
        cmd.setRuleCode(instance.getRuleCode());
        cmd.setVersionNo(instance.getVersionNo());
        cmd.setMaterialCode(instance.getMaterialCode());
        cmd.setBatchNo(instance.getBatchNo());
        cmd.setSerialNo(instance.getSerialNo());
        cmd.setPackBarcode(instance.getPackBarcode());
        cmd.setBarcodeType(instance.getBarcodeType() != null ? instance.getBarcodeType()
                : (rule != null ? rule.getBarcodeType() : null));
        if (rule != null) {
            cmd.setTemplateCode(rule.getTemplateCode());
            cmd.setSeparator(rule.getSeparator());
            cmd.setSegmentsJson(rule.getSegmentsJson());
        }
        cmd.setLabelWidthMm(req.getLabelWidthMm() != null ? req.getLabelWidthMm() : new BigDecimal("100"));
        cmd.setLabelHeightMm(req.getLabelHeightMm() != null ? req.getLabelHeightMm() : new BigDecimal("60"));
        cmd.setActionType("UPDATE");
        return archiveService.archive(cmd);
    }
}
