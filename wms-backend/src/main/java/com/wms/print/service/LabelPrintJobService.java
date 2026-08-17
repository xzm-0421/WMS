package com.wms.print.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.auth.security.LoginUser;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.entity.BaseWarehouse;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.base.mapper.BaseWarehouseMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.excel.ExcelImportHelper;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.print.PrintDocumentHelper;
import com.wms.print.dto.KingdeeLabelPrintBatchRequest;
import com.wms.print.dto.KingdeeLabelPrintBatchResult;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.dto.LabelPrintJobVo;
import com.wms.print.dto.OpeningStockExcelRow;
import com.wms.print.entity.LabelPrintJob;
import com.wms.print.mapper.LabelPrintJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LabelPrintJobService {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final LabelPrintJobMapper jobMapper;
    private final BaseMaterialMapper materialMapper;
    private final BaseWarehouseMapper warehouseMapper;
    private final KingdeeCloudProperties kingdeeCloudProperties;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public LabelPrintJobVo createFromKingdee(KingdeeLabelPrintRequest req, String sourceType) {
        // 金蝶二开可携带物料名称/规格；WMS 未同步物料时仍允许建任务
        LabelPrintJob job = buildJob(req, sourceType != null ? sourceType : "KINGDEE", null, true);
        jobMapper.insert(job);
        return toVo(job, true);
    }

    /**
     * 金蝶批量创建标签任务，并返回可直接打开的打印地址。
     */
    @Transactional(rollbackFor = Exception.class)
    public KingdeeLabelPrintBatchResult createBatchFromKingdee(KingdeeLabelPrintBatchRequest request) {
        boolean autoPrint = request.getAutoPrint() == null || Boolean.TRUE.equals(request.getAutoPrint());
        KingdeeLabelPrintBatchResult result = new KingdeeLabelPrintBatchResult();
        for (KingdeeLabelPrintRequest line : request.getLines()) {
            LabelPrintJobVo job = toVo(
                    buildAndInsertKingdeeJob(line),
                    autoPrint);
            result.getJobs().add(job);
            String url = StringUtils.hasText(job.getAbsolutePrintUrl()) ? job.getAbsolutePrintUrl() : job.getPrintUrl();
            result.getPrintUrls().add(url);
        }
        result.setCount(result.getJobs().size());
        if (!result.getPrintUrls().isEmpty()) {
            result.setFirstPrintUrl(result.getPrintUrls().get(0));
        }
        return result;
    }

    private LabelPrintJob buildAndInsertKingdeeJob(KingdeeLabelPrintRequest line) {
        LabelPrintJob job = buildJob(line, "KINGDEE", null, true);
        jobMapper.insert(job);
        return job;
    }

    @Transactional(rollbackFor = Exception.class)
    public LabelPrintJobVo createManual(KingdeeLabelPrintRequest req) {
        LoginUser user = SecurityUtils.currentUser();
        LabelPrintJob job = buildJob(req, "OPENING", user, false);
        jobMapper.insert(job);
        return toVo(job, false);
    }

    public PageResult<LabelPrintJobVo> page(String warehouseCode, String warehouseName,
                                            String materialKeyword, long current, long size) {
        Page<LabelPrintJob> page = jobMapper.selectPage(
                new Page<>(current, size),
                buildOpeningQuery(warehouseCode, warehouseName, materialKeyword));
        return PageResult.of(
                page.getRecords().stream().map(this::toVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public int importExcel(MultipartFile file) throws IOException {
        List<OpeningStockExcelRow> allRows = ExcelImportHelper.readRows(file, OpeningStockExcelRow.class);
        LoginUser user = SecurityUtils.currentUser();
        int created = 0;
        for (int i = 0; i < allRows.size(); i++) {
            OpeningStockExcelRow row = allRows.get(i);
            int excelRowNum = i + 2;
            if (isBlankImportRow(row)) {
                continue;
            }
            ExcelImportHelper.requireText(row.getMaterialCode(), excelRowNum, "物料编码");
            KingdeeLabelPrintRequest req = new KingdeeLabelPrintRequest();
            req.setWarehouseCode(trimToNull(row.getWarehouseCode()));
            req.setWarehouseName(trimToNull(row.getWarehouseName()));
            req.setOrgCode(trimToNull(row.getOrgCode()));
            req.setOrgName(trimToNull(row.getOrgName()));
            req.setMaterialCode(row.getMaterialCode().trim());
            req.setMaterialName(trimToNull(row.getMaterialName()));
            req.setSpecification(trimToNull(row.getSpecification()));
            req.setBatchNo(trimToNull(row.getBatchNo()));
            req.setProductionDate(trimToNull(row.getProductionDate()));
            req.setQuantity(row.getQuantity());
            req.setUnitCode(trimToNull(row.getUnitCode()));
            req.setPriceUnitCode(trimToNull(row.getPriceUnitCode()));
            req.setCopies(row.getCopies() != null && row.getCopies() > 0 ? row.getCopies() : 1);
            req.setBarcodeType("QR");
            LabelPrintJob job = buildJob(req, "OPENING", user, false);
            jobMapper.insert(job);
            created++;
        }
        if (created == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 中没有有效数据行，请先下载模板填写后再导入");
        }
        return created;
    }

    public void exportExcel(String warehouseCode, String warehouseName, String materialKeyword,
                            OutputStream outputStream) {
        List<LabelPrintJob> jobs = jobMapper.selectList(
                buildOpeningQuery(warehouseCode, warehouseName, materialKeyword));
        List<OpeningStockExcelRow> rows = new ArrayList<>(jobs.size());
        for (LabelPrintJob job : jobs) {
            LabelPrintJobVo vo = toVo(job);
            OpeningStockExcelRow row = new OpeningStockExcelRow();
            row.setWarehouseCode(vo.getWarehouseCode());
            row.setWarehouseName(vo.getWarehouseName());
            row.setOrgCode(vo.getOrgCode());
            row.setOrgName(vo.getOrgName());
            row.setMaterialCode(vo.getMaterialCode());
            row.setMaterialName(vo.getMaterialName());
            row.setSpecification(vo.getSpecification());
            row.setBatchNo(vo.getBatchNo());
            row.setProductionDate(vo.getProductionDate());
            row.setQuantity(vo.getQuantity());
            row.setUnitCode(vo.getUnitCode());
            row.setPriceUnitCode(vo.getPriceUnitCode());
            row.setCopies(vo.getCopies());
            rows.add(row);
        }
        EasyExcel.write(outputStream, OpeningStockExcelRow.class).sheet("期初库存").doWrite(rows);
    }

    /**
     * 期初库存导入模板：数据表（表头+示例行）+ 填写说明表。
     */
    public void writeImportTemplate(OutputStream outputStream) {
        try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {
            WriteSheet dataSheet = EasyExcel.writerSheet(0, "期初库存")
                    .head(OpeningStockExcelRow.class)
                    .build();
            excelWriter.write(List.of(OpeningStockExcelRow.sample()), dataSheet);

            // EasyExcel 动态表头：每个内层 List 代表一列
            WriteSheet tipSheet = EasyExcel.writerSheet(1, "填写说明")
                    .head(List.of(
                            List.of("列名"),
                            List.of("是否必填"),
                            List.of("填写说明")))
                    .build();
            excelWriter.write(openingStockTemplateGuideRows(), tipSheet);
        }
    }

    private static List<List<String>> openingStockTemplateGuideRows() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("仓库编码", "否", "仓库主数据编码，可与仓库名称二选一或同时填写"));
        rows.add(List.of("仓库名称", "否", "可不填；填写仓库编码时系统可按编码回填名称"));
        rows.add(List.of("业务组织编码", "否", "金蝶/业务组织编码，可按环境默认组织回填"));
        rows.add(List.of("业务组织名称", "否", "业务组织显示名称"));
        rows.add(List.of("物料编码", "是", "须在「物料信息」中已存在（可先金蝶同步）"));
        rows.add(List.of("物料名称", "否", "可不填，导入时优先取物料主数据名称"));
        rows.add(List.of("规格型号", "否", "可不填，导入时优先取物料主数据规格"));
        rows.add(List.of("批次号", "否", "批次管理物料建议填写"));
        rows.add(List.of("生产日期", "否", "格式 yyyy-MM-dd，如 2026-01-01；不填则打印时可用当天"));
        rows.add(List.of("数量", "否", "标签数量，支持小数"));
        rows.add(List.of("入库单位", "否", "库存/入库单位；不填则取物料主数据单位"));
        rows.add(List.of("计价单位", "否", "计价单位，如 KG"));
        rows.add(List.of("打印份数", "否", "正整数，默认 1"));
        rows.add(List.of("使用提示", "-", "请保留「期初库存」表头行；从第 2 行起填写；可删除示例行后再导入"));
        return rows;
    }

    /**
     * 按主键批量删除期初库存打印任务（不含金蝶推送任务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteOpeningStock(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先选择要删除的数据");
        }
        List<Long> distinctIds = ids.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先选择要删除的数据");
        }
        List<LabelPrintJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<LabelPrintJob>()
                .in(LabelPrintJob::getId, distinctIds)
                .and(w -> w.isNull(LabelPrintJob::getSourceType)
                        .or().ne(LabelPrintJob::getSourceType, "KINGDEE")));
        if (jobs.isEmpty()) {
            return 0;
        }
        List<Long> deleteIds = jobs.stream().map(LabelPrintJob::getId).filter(id -> id != null).toList();
        jobMapper.deleteBatchIds(deleteIds);
        return deleteIds.size();
    }

    private LambdaQueryWrapper<LabelPrintJob> buildOpeningQuery(String warehouseCode, String warehouseName,
                                                                String materialKeyword) {
        LambdaQueryWrapper<LabelPrintJob> wrapper = new LambdaQueryWrapper<>();
        // 期初库存页不展示金蝶推送任务
        wrapper.and(w -> w.isNull(LabelPrintJob::getSourceType)
                .or().ne(LabelPrintJob::getSourceType, "KINGDEE"));
        if (StringUtils.hasText(warehouseCode)) {
            wrapper.like(LabelPrintJob::getWarehouseCode, warehouseCode.trim());
        }
        if (StringUtils.hasText(warehouseName)) {
            wrapper.like(LabelPrintJob::getWarehouseName, warehouseName.trim());
        }
        if (StringUtils.hasText(materialKeyword)) {
            String kw = materialKeyword.trim();
            wrapper.and(w -> w.like(LabelPrintJob::getMaterialCode, kw)
                    .or().like(LabelPrintJob::getMaterialName, kw)
                    .or().like(LabelPrintJob::getJobId, kw));
        }
        wrapper.orderByDesc(LabelPrintJob::getCreateTime);
        return wrapper;
    }

    private static boolean isBlankImportRow(OpeningStockExcelRow row) {
        return !StringUtils.hasText(row.getMaterialCode())
                && !StringUtils.hasText(row.getWarehouseCode())
                && !StringUtils.hasText(row.getOrgCode())
                && row.getQuantity() == null;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public LabelPrintJobVo getByJobId(String jobId) {
        return toVo(requireJob(jobId));
    }

    public Map<String, Object> buildPrintDocument(String jobId) {
        LabelPrintJob job = requireJob(jobId);
        MaterialSnapshot material = resolveMaterialForJob(job);
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

    private LabelPrintJob buildJob(KingdeeLabelPrintRequest req, String sourceType, LoginUser user,
                                   boolean allowMissingMaterial) {
        MaterialSnapshot material = resolveMaterialSnapshot(req, allowMissingMaterial);
        String barcode = StringUtils.hasText(req.getBarcodeContent())
                ? req.getBarcodeContent().trim()
                : buildDefaultBarcode(req);
        LabelPrintJob job = new LabelPrintJob();
        job.setJobId(generateJobId());
        job.setSourceType(sourceType);
        job.setSourceBillNo(req.getSourceBillNo());
        applyWarehouse(job, req.getWarehouseCode(), req.getWarehouseName());
        applyOrg(job, req.getOrgCode(), req.getOrgName());
        job.setMaterialCode(req.getMaterialCode().trim());
        job.setMaterialName(material.materialName());
        job.setSpecification(material.specification());
        job.setBatchNo(req.getBatchNo());
        job.setProductionDate(normalizeProductionDate(req.getProductionDate()));
        job.setQuantity(req.getQuantity());
        job.setUnitCode(StringUtils.hasText(req.getUnitCode()) ? req.getUnitCode().trim() : material.unitCode());
        job.setPriceUnitCode(trimToNull(req.getPriceUnitCode()));
        job.setBarcodeContent(barcode);
        job.setBarcodeType(normalizeBarcodeType(req.getBarcodeType()));
        job.setLabelWidthMm(req.getLabelWidthMm() != null ? req.getLabelWidthMm() : new BigDecimal("110"));
        job.setLabelHeightMm(req.getLabelHeightMm() != null ? req.getLabelHeightMm() : new BigDecimal("80"));
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

    private MaterialSnapshot resolveMaterialSnapshot(KingdeeLabelPrintRequest req, boolean allowMissingMaterial) {
        if (!StringUtils.hasText(req.getMaterialCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "物料编码不能为空");
        }
        MaterialSnapshot fromMaster = resolveMaterialFromMaster(req.getMaterialCode().trim());
        if (fromMaster != null) {
            return new MaterialSnapshot(
                    StringUtils.hasText(req.getMaterialName()) ? req.getMaterialName().trim() : fromMaster.materialName(),
                    StringUtils.hasText(req.getSpecification()) ? req.getSpecification().trim() : fromMaster.specification(),
                    StringUtils.hasText(req.getUnitCode()) ? req.getUnitCode().trim() : fromMaster.unitCode());
        }
        if (!allowMissingMaterial) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "物料不存在，请先在物料信息中维护或从金蝶同步: " + req.getMaterialCode().trim());
        }
        return new MaterialSnapshot(
                defaultText(req.getMaterialName(), req.getMaterialCode().trim()),
                trimToNull(req.getSpecification()),
                defaultText(req.getUnitCode(), "PCS"));
    }

    private MaterialSnapshot resolveMaterialForJob(LabelPrintJob job) {
        MaterialSnapshot fromMaster = resolveMaterialFromMaster(job.getMaterialCode());
        if (fromMaster != null) {
            return new MaterialSnapshot(
                    StringUtils.hasText(job.getMaterialName()) ? job.getMaterialName() : fromMaster.materialName(),
                    StringUtils.hasText(job.getSpecification()) ? job.getSpecification() : fromMaster.specification(),
                    StringUtils.hasText(job.getUnitCode()) ? job.getUnitCode() : fromMaster.unitCode());
        }
        return new MaterialSnapshot(
                defaultText(job.getMaterialName(), job.getMaterialCode()),
                job.getSpecification(),
                defaultText(job.getUnitCode(), "PCS"));
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

    private void applyWarehouse(LabelPrintJob job, String warehouseCode, String warehouseName) {
        String code = StringUtils.hasText(warehouseCode) ? warehouseCode.trim() : null;
        String name = StringUtils.hasText(warehouseName) ? warehouseName.trim() : null;
        if (code != null && !StringUtils.hasText(name)) {
            BaseWarehouse wh = warehouseMapper.selectOne(new LambdaQueryWrapper<BaseWarehouse>()
                    .eq(BaseWarehouse::getWarehouseCode, code));
            if (wh != null) {
                name = wh.getWarehouseName();
            }
        }
        job.setWarehouseCode(code);
        job.setWarehouseName(name);
    }

    private void applyOrg(LabelPrintJob job, String orgCode, String orgName) {
        String code = StringUtils.hasText(orgCode) ? orgCode.trim() : null;
        String name = StringUtils.hasText(orgName) ? orgName.trim() : null;
        if (!StringUtils.hasText(code) && StringUtils.hasText(kingdeeCloudProperties.getStockInOrgNumber())) {
            code = kingdeeCloudProperties.getStockInOrgNumber().trim();
        }
        job.setOrgCode(code);
        job.setOrgName(name);
    }

    private void enrichVoFromMaterial(LabelPrintJobVo vo) {
        if (!StringUtils.hasText(vo.getMaterialCode())) {
            return;
        }
        MaterialSnapshot snapshot = resolveMaterialFromMaster(vo.getMaterialCode().trim());
        if (snapshot == null) {
            return;
        }
        if (!StringUtils.hasText(vo.getMaterialName())) {
            vo.setMaterialName(snapshot.materialName());
        }
        if (!StringUtils.hasText(vo.getSpecification())) {
            vo.setSpecification(snapshot.specification());
        }
        // 入库单位以任务落库为准，缺失时回填物料主数据单位
        if (!StringUtils.hasText(vo.getUnitCode())) {
            vo.setUnitCode(snapshot.unitCode());
        }
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
        return toVo(job, false);
    }

    private LabelPrintJobVo toVo(LabelPrintJob job, boolean autoPrint) {
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
        if (autoPrint) {
            url.append("&autoPrint=1");
        }
        String relative = url.toString();
        vo.setPrintUrl(relative);
        String base = kingdeeCloudProperties.getPrintPublicBaseUrl();
        if (StringUtils.hasText(base)) {
            String normalized = base.trim().replaceAll("/+$", "");
            vo.setAbsolutePrintUrl(normalized + relative);
        }
        return vo;
    }

    private String generateJobId() {
        return "LPJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", SEQ.getAndIncrement() % 10000);
    }
}
