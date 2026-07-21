package com.wms.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.entity.BarcodeArchive;
import com.wms.barcode.mapper.BarcodeArchiveMapper;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.mobile.dto.MobileLabelResolveRequest;
import com.wms.mobile.dto.MobileScanRecognizeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MobilePrintService {

    private final MobileScanService mobileScanService;
    private final BarcodeArchiveMapper barcodeArchiveMapper;
    private final BaseMaterialMapper materialMapper;

    /**
     * 根据条码解析标签打印数据（优先条码存档，否则规则识别+物料主数据）
     */
    public Map<String, Object> resolveLabel(MobileLabelResolveRequest req) {
        if (!StringUtils.hasText(req.getBarcodeContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码不能为空");
        }
        String barcode = req.getBarcodeContent().trim();

        BarcodeArchive archive = barcodeArchiveMapper.selectOne(new LambdaQueryWrapper<BarcodeArchive>()
                .eq(BarcodeArchive::getBarcodeContent, barcode)
                .orderByDesc(BarcodeArchive::getCreateTime)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));

        if (archive != null) {
            return buildFromArchive(archive);
        }

        MobileScanRecognizeRequest recognizeReq = new MobileScanRecognizeRequest();
        recognizeReq.setBarcodeContent(barcode);
        recognizeReq.setWarehouseCode(req.getWarehouseCode());
        Map<String, Object> recognized = mobileScanService.recognize(recognizeReq);

        String materialCode = (String) recognized.get("materialCode");
        if (!StringUtils.hasText(materialCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法识别条码，不能生成标签");
        }

        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, materialCode));
        if (material == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在: " + materialCode);
        }

        String batchNo = (String) recognized.get("batchNo");
        String titleLine = StringUtils.hasText(material.getMaterialName())
                ? material.getMaterialName()
                : materialCode;
        if (StringUtils.hasText(material.getModel())) {
            titleLine = titleLine + " " + material.getModel();
        }

        Map<String, Object> label = new HashMap<>();
        label.put("biz", "material_label");
        label.put("docNo", materialCode);
        label.put("titleLine", titleLine);
        label.put("materialCode", materialCode);
        label.put("materialName", material.getMaterialName());
        label.put("specification", material.getSpecification());
        label.put("unitCode", material.getUnitCode());
        label.put("batchNo", StringUtils.hasText(batchNo) ? batchNo : "");
        label.put("barcodeContent", barcode);
        label.put("barcodeType", "QR");
        label.put("labelWidthMm", 100);
        label.put("labelHeightMm", 55);
        label.put("labelFormat", "material_label");
        label.put("fromArchive", false);
        return label;
    }

    private Map<String, Object> buildFromArchive(BarcodeArchive archive) {
        BaseMaterial material = null;
        if (StringUtils.hasText(archive.getMaterialCode())) {
            material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, archive.getMaterialCode()));
        }
        String materialName = material != null ? material.getMaterialName() : "";
        String specification = material != null ? material.getSpecification() : "";
        String titleLine;
        if (StringUtils.hasText(materialName)) {
            titleLine = StringUtils.hasText(material.getModel())
                    ? materialName + " " + material.getModel()
                    : materialName;
        } else if (StringUtils.hasText(archive.getMaterialCode())) {
            titleLine = archive.getMaterialCode();
        } else {
            titleLine = archive.getBarcodeContent();
        }

        Map<String, Object> label = new HashMap<>();
        label.put("biz", "barcode_archive");
        label.put("docNo", archive.getArchiveNo());
        label.put("archiveNo", archive.getArchiveNo());
        label.put("titleLine", titleLine);
        label.put("materialCode", archive.getMaterialCode());
        label.put("materialName", materialName);
        label.put("specification", specification);
        label.put("unitCode", material != null ? material.getUnitCode() : "");
        label.put("batchNo", archive.getBatchNo());
        label.put("serialNo", archive.getSerialNo());
        label.put("barcodeContent", archive.getBarcodeContent());
        label.put("barcodeType", StringUtils.hasText(archive.getBarcodeType()) ? archive.getBarcodeType() : "QR");
        label.put("labelWidthMm", archive.getLabelWidthMm() != null ? archive.getLabelWidthMm() : new BigDecimal("100"));
        label.put("labelHeightMm", archive.getLabelHeightMm() != null ? archive.getLabelHeightMm() : new BigDecimal("55"));
        label.put("labelFormat", archive.getLabelFormat());
        label.put("fromArchive", true);
        return label;
    }
}
