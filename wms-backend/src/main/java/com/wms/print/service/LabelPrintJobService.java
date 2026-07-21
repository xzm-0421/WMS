package com.wms.print.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.auth.security.LoginUser;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.print.PrintDocumentHelper;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.dto.LabelPrintJobVo;
import com.wms.print.entity.LabelPrintJob;
import com.wms.print.mapper.LabelPrintJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LabelPrintJobService {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final LabelPrintJobMapper jobMapper;
    private final BaseMaterialMapper materialMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public LabelPrintJobVo createFromKingdee(KingdeeLabelPrintRequest req, String sourceType) {
        requireMaterialMaster(req.getMaterialCode());
        LabelPrintJob job = buildJob(req, sourceType != null ? sourceType : "KINGDEE", null);
        jobMapper.insert(job);
        return toVo(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelPrintJobVo createManual(KingdeeLabelPrintRequest req) {
        LoginUser user = SecurityUtils.currentUser();
        requireMaterialMaster(req.getMaterialCode());
        LabelPrintJob job = buildJob(req, "MANUAL", user);
        jobMapper.insert(job);
        return toVo(job);
    }

    public PageResult<LabelPrintJobVo> page(String keyword, String status, long current, long size) {
        LambdaQueryWrapper<LabelPrintJob> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(LabelPrintJob::getMaterialCode, kw)
                    .or().like(LabelPrintJob::getJobId, kw));
        }
        wrapper.eq(StringUtils.hasText(status), LabelPrintJob::getStatus, status)
                .orderByDesc(LabelPrintJob::getCreateTime);
        Page<LabelPrintJob> page = jobMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(
                page.getRecords().stream().map(this::toVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public LabelPrintJobVo getByJobId(String jobId) {
        return toVo(requireJob(jobId));
    }

    public Map<String, Object> buildPrintDocument(String jobId) {
        LabelPrintJob job = requireJob(jobId);
        MaterialSnapshot material = requireMaterialMaster(job.getMaterialCode());
        String titleLine = material.materialName();
        if (StringUtils.hasText(material.specification())) {
            titleLine = titleLine + " " + material.specification();
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("FTitleLine", titleLine);
        header.put("FNumber", job.getMaterialCode());
        header.put("FName", material.materialName());
        header.put("FModel", material.specification());
        header.put("FBaseUnit", material.unitCode());
        header.put("FLot", job.getBatchNo());
        header.put("FProductionDate", resolveProductionDate(job));
        header.put("FQty", job.getQuantity());
        header.put("FQtyDisplay", buildQtyDisplay(job, material.unitCode()));
        header.put("FBarCode", job.getBarcodeContent());
        header.put("FBarcodeType", job.getBarcodeType());
        header.put("FPageWidth", job.getLabelWidthMm());
        header.put("FPageHeight", job.getLabelHeightMm());
        header.put("FCopies", job.getCopies());
        header.put("FSourceBillNo", job.getSourceBillNo());
        header.put("FJobId", job.getJobId());
        return PrintDocumentHelper.document(jobId, "t_bd_material", null, header, java.util.List.of(), null);
    }

    public LabelPrintJobVo syncMaterial(String jobId) {
        return getByJobId(jobId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markOpened(String jobId) {
        LabelPrintJob job = requireJob(jobId);
        requireMaterialMaster(job.getMaterialCode());
        if ("PENDING".equals(job.getStatus())) {
            job.setStatus("OPENED");
            job.setOpenedTime(LocalDateTime.now());
            jobMapper.updateById(job);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelPrintJobVo markPrinted(String jobId, String errorMessage) {
        LabelPrintJob job = requireJob(jobId);
        if (StringUtils.hasText(errorMessage)) {
            job.setStatus("FAILED");
            job.setErrorMessage(errorMessage);
        } else {
            job.setStatus("PRINTED");
            job.setPrintedTime(LocalDateTime.now());
            job.setErrorMessage(null);
        }
        jobMapper.updateById(job);
        return toVo(job);
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelPrintJobVo updateSettings(String jobId, BigDecimal width, BigDecimal height,
                                            Integer copies, String barcodeType) {
        LabelPrintJob job = requireJob(jobId);
        if (width != null) job.setLabelWidthMm(width);
        if (height != null) job.setLabelHeightMm(height);
        if (copies != null && copies > 0) job.setCopies(copies);
        if (StringUtils.hasText(barcodeType)) job.setBarcodeType(barcodeType.toUpperCase());
        jobMapper.updateById(job);
        return toVo(job);
    }

    private LabelPrintJob buildJob(KingdeeLabelPrintRequest req, String sourceType, LoginUser user) {
        requireMaterialMaster(req.getMaterialCode());
        String barcode = StringUtils.hasText(req.getBarcodeContent())
                ? req.getBarcodeContent().trim()
                : buildDefaultBarcode(req);
        LabelPrintJob job = new LabelPrintJob();
        job.setJobId(generateJobId());
        job.setSourceType(sourceType);
        job.setSourceBillNo(req.getSourceBillNo());
        job.setMaterialCode(req.getMaterialCode().trim());
        job.setMaterialName(null);
        job.setSpecification(null);
        job.setBatchNo(req.getBatchNo());
        job.setProductionDate(normalizeProductionDate(req.getProductionDate()));
        job.setQuantity(req.getQuantity());
        job.setUnitCode(null);
        job.setBarcodeContent(barcode);
        job.setBarcodeType(normalizeBarcodeType(req.getBarcodeType()));
        job.setLabelWidthMm(req.getLabelWidthMm() != null ? req.getLabelWidthMm() : new BigDecimal("60"));
        job.setLabelHeightMm(req.getLabelHeightMm() != null ? req.getLabelHeightMm() : new BigDecimal("40"));
        job.setCopies(req.getCopies() != null && req.getCopies() > 0 ? req.getCopies() : 1);
        job.setStatus("PENDING");
        if (user != null) {
            job.setOperatorId(String.valueOf(user.getUserId()));
            job.setOperatorName(user.getRealName() != null ? user.getRealName() : user.getUsername());
        } else if (StringUtils.hasText(req.getOperatorName())) {
            job.setOperatorName(req.getOperatorName());
        }
        try {
            job.setRequestPayload(objectMapper.writeValueAsString(req));
        } catch (Exception e) {
            job.setRequestPayload(req.toString());
        }
        job.setCreateTime(LocalDateTime.now());
        return job;
    }

    private MaterialSnapshot requireMaterialMaster(String materialCode) {
        if (!StringUtils.hasText(materialCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "物料编码不能为空");
        }
        MaterialSnapshot snapshot = resolveMaterialFromMaster(materialCode.trim());
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "物料不存在，请先在物料信息中维护或从金蝶同步: " + materialCode.trim());
        }
        return snapshot;
    }

    private MaterialSnapshot resolveMaterialFromMaster(String materialCode) {
        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, materialCode));
        if (material == null) {
            return null;
        }
        return new MaterialSnapshot(
                defaultText(material.getMaterialName(), materialCode),
                material.getSpecification(),
                defaultText(material.getUnitCode(), "PCS"));
    }

    private void enrichVoFromMaterial(LabelPrintJobVo vo) {
        if (!StringUtils.hasText(vo.getMaterialCode())) {
            return;
        }
        MaterialSnapshot snapshot = resolveMaterialFromMaster(vo.getMaterialCode().trim());
        if (snapshot == null) {
            return;
        }
        vo.setMaterialName(snapshot.materialName());
        vo.setSpecification(snapshot.specification());
        vo.setUnitCode(snapshot.unitCode());
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record MaterialSnapshot(String materialName, String specification, String unitCode) {}

    private String buildDefaultBarcode(KingdeeLabelPrintRequest req) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("materialCode", req.getMaterialCode().trim());
            if (StringUtils.hasText(req.getMaterialName())) {
                payload.put("materialName", req.getMaterialName().trim());
            }
            if (StringUtils.hasText(req.getSpecification())) {
                payload.put("specification", req.getSpecification().trim());
            }
            if (StringUtils.hasText(req.getBatchNo())) {
                payload.put("batchNo", req.getBatchNo().trim());
            }
            if (req.getQuantity() != null && req.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                payload.put("quantity", req.getQuantity().stripTrailingZeros());
            }
            if (StringUtils.hasText(req.getUnitCode())) {
                payload.put("unitCode", req.getUnitCode().trim());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            String code = req.getMaterialCode().trim();
            String batch = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo().trim() : null;
            if (req.getQuantity() != null && req.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                String qty = req.getQuantity().stripTrailingZeros().toPlainString();
                if (StringUtils.hasText(batch)) {
                    return code + "|" + batch + "|" + qty;
                }
                return code + "|" + qty;
            }
            if (StringUtils.hasText(batch)) {
                return code + batch;
            }
            return code;
        }
    }

    private String resolveProductionDate(LabelPrintJob job) {
        if (StringUtils.hasText(job.getProductionDate())) {
            return job.getProductionDate().trim();
        }
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String buildQtyDisplay(LabelPrintJob job, String unitCode) {
        if (job.getQuantity() == null) {
            return StringUtils.hasText(unitCode) ? unitCode.trim() : "";
        }
        String qty = job.getQuantity().stripTrailingZeros().toPlainString();
        if (StringUtils.hasText(unitCode)) {
            return qty + " " + unitCode.trim();
        }
        return qty;
    }

    private String normalizeProductionDate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if (value.length() >= 10) {
            return value.substring(0, 10);
        }
        return value;
    }

    private String normalizeBarcodeType(String type) {
        if (!StringUtils.hasText(type)) return "QR";
        String t = type.trim().toUpperCase();
        if ("QRCODE".equals(t)) return "QR";
        return t;
    }

    private LabelPrintJob requireJob(String jobId) {
        LabelPrintJob job = jobMapper.selectOne(new LambdaQueryWrapper<LabelPrintJob>()
                .eq(LabelPrintJob::getJobId, jobId));
        if (job == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "打印任务不存在: " + jobId);
        }
        return job;
    }

    private LabelPrintJobVo toVo(LabelPrintJob job) {
        LabelPrintJobVo vo = new LabelPrintJobVo();
        BeanUtils.copyProperties(job, vo);
        enrichVoFromMaterial(vo);
        StringBuilder url = new StringBuilder("/print-designer/index.html?biz=kingdee_label&docNo=")
                .append(job.getJobId())
                .append("&embed=1&jobId=")
                .append(job.getJobId());
        if (job.getLabelWidthMm() != null) {
            url.append("&width=").append(job.getLabelWidthMm().stripTrailingZeros().toPlainString());
        }
        if (job.getLabelHeightMm() != null) {
            url.append("&height=").append(job.getLabelHeightMm().stripTrailingZeros().toPlainString());
        }
        if (job.getCopies() != null && job.getCopies() > 1) {
            url.append("&copies=").append(job.getCopies());
        }
        vo.setPrintUrl(url.toString());
        return vo;
    }

    private String generateJobId() {
        return "LPJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", SEQ.getAndIncrement() % 10000);
    }
}
