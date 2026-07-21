package com.wms.picking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.service.InventoryService;
import com.wms.picking.dto.PickIssueScanRequest;
import com.wms.picking.entity.PickIssue;
import com.wms.picking.entity.PickIssueLine;
import com.wms.picking.entity.PrepNotice;
import com.wms.picking.entity.PrepNoticeLine;
import com.wms.picking.mapper.PickIssueLineMapper;
import com.wms.picking.mapper.PickIssueMapper;
import com.wms.picking.mapper.PrepNoticeLineMapper;
import com.wms.picking.mapper.PrepNoticeMapper;
import com.wms.print.PrintDocumentHelper;
import com.wms.production.entity.ProductionOrder;
import com.wms.production.mapper.ProductionOrderMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PickIssueService {

    private final PickIssueMapper issueMapper;
    private final PickIssueLineMapper issueLineMapper;
    private final PrepNoticeMapper noticeMapper;
    private final PrepNoticeLineMapper lineMapper;
    private final InventoryService inventoryService;
    private final BaseMaterialMapper materialMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final AuditTrailService auditTrailService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public PageResult<PickIssue> page(String issueNo, String status, long current, long size) {
        LambdaQueryWrapper<PickIssue> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(issueNo), PickIssue::getIssueNo, issueNo)
                .eq(StringUtils.hasText(status), PickIssue::getStatus, status)
                .eq(PickIssue::getDeleted, 0)
                .orderByDesc(PickIssue::getCreateTime);
        Page<PickIssue> page = issueMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public String createFromNotice(String noticeNo, String pickerName, String handoverArea) {
        PrepNotice notice = noticeMapper.selectOne(new LambdaQueryWrapper<PrepNotice>()
                .eq(PrepNotice::getNoticeNo, noticeNo)
                .eq(PrepNotice::getDeleted, 0));
        if (notice == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "备料通知单不存在");
        }
        if (!"OPEN".equals(notice.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该备料通知单已生成拣配单，不能重复生成", "NOTICE_ALREADY_PICKED");
        }
        long existing = issueMapper.selectCount(new LambdaQueryWrapper<PickIssue>()
                .eq(PickIssue::getNoticeNo, noticeNo)
                .eq(PickIssue::getDeleted, 0));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该备料通知单已存在拣配发料单，不能重复生成", "ISSUE_ALREADY_EXISTS");
        }
        String issueNo = OrderNoGenerator.next("PI");
        PickIssue issue = new PickIssue();
        issue.setIssueNo(issueNo);
        issue.setNoticeNo(noticeNo);
        issue.setWarehouseCode(notice.getWarehouseCode());
        issue.setHandoverArea(handoverArea);
        issue.setStatus("PICKING");
        issue.setPickerName(pickerName);
        issue.setCreateTime(LocalDateTime.now());
        issue.setDeleted(0);
        issueMapper.insert(issue);
        List<PrepNoticeLine> noticeLines = lineMapper.selectList(new LambdaQueryWrapper<PrepNoticeLine>()
                .eq(PrepNoticeLine::getNoticeNo, noticeNo)
                .orderByAsc(PrepNoticeLine::getLineNo));
        int lineNo = 1;
        for (PrepNoticeLine nl : noticeLines) {
            PickIssueLine pl = new PickIssueLine();
            pl.setIssueNo(issueNo);
            pl.setLineNo(lineNo++);
            pl.setMaterialCode(nl.getMaterialCode());
            pl.setPickQty(nl.getDemandQty());
            pl.setPickedQty(BigDecimal.ZERO);
            pl.setSourceLocation(nl.getRecommendLoc());
            issueLineMapper.insert(pl);
        }
        notice.setStatus("PICKING");
        noticeMapper.updateById(notice);
        auditTrailService.log("PICK_ISSUE", issueNo, "CREATE", pickerName, noticeNo);
        return issueNo;
    }

    public Map<String, Object> detail(String issueNo) {
        PickIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<PickIssue>()
                .eq(PickIssue::getIssueNo, issueNo)
                .eq(PickIssue::getDeleted, 0));
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拣配发料单不存在");
        }
        List<PickIssueLine> lines = issueLineMapper.selectList(new LambdaQueryWrapper<PickIssueLine>()
                .eq(PickIssueLine::getIssueNo, issueNo)
                .orderByAsc(PickIssueLine::getLineNo));
        Map<String, Object> data = new HashMap<>();
        data.put("issue", issue);
        data.put("lines", lines);
        return data;
    }

    public long countTodayCompleted() {
        return issueMapper.selectCount(new LambdaQueryWrapper<PickIssue>()
                .eq(PickIssue::getStatus, "COMPLETED")
                .apply("CAST(create_time AS DATE) = CAST(GETDATE() AS DATE)"));
    }

    public long countTodayTotal() {
        return issueMapper.selectCount(new LambdaQueryWrapper<PickIssue>()
                .apply("CAST(create_time AS DATE) = CAST(GETDATE() AS DATE)"));
    }

    public List<PickIssue> listByStatus(String status, int limit) {
        LambdaQueryWrapper<PickIssue> w = new LambdaQueryWrapper<PickIssue>()
                .eq(PickIssue::getDeleted, 0)
                .orderByDesc(PickIssue::getCreateTime)
                .last("OFFSET 0 ROWS FETCH NEXT " + Math.min(limit, 50) + " ROWS ONLY");
        if (StringUtils.hasText(status)) {
            w.eq(PickIssue::getStatus, status);
        }
        return issueMapper.selectList(w);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scanPick(String issueNo, PickIssueScanRequest req) {
        PickIssue issue = getIssue(issueNo);
        if (!"PICKING".equals(issue.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "发料单状态不允许拣货", "ORDER_STATUS_CONFLICT");
        }
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "数量必须大于0");
        }

        PickIssueLine line = resolvePickLine(issueNo, req);
        BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
        BigDecimal newPicked = picked.add(req.getQuantity());
        if (newPicked.compareTo(line.getPickQty()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "拣货数量超出应拣数量", "QTY_EXCEED_ORDER");
        }

        if (StringUtils.hasText(line.getSourceLocation())
                && !line.getSourceLocation().equalsIgnoreCase(req.getLocationCode())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "库位不匹配，应拣库位: " + line.getSourceLocation(), "LOCATION_MISMATCH");
        }

        LoginUser user = currentUser();
        inventoryService.decrease(InventoryChangeCommand.builder()
                .transactionType("PRODUCTION_OUT")
                .warehouseCode(issue.getWarehouseCode())
                .locationCode(req.getLocationCode())
                .materialCode(line.getMaterialCode())
                .batchNo(req.getBatchNo())
                .quantity(req.getQuantity())
                .sourceOrderType("PICK_ISSUE")
                .sourceOrderNo(issueNo)
                .sourceOrderLine(line.getLineNo())
                .operatorId(String.valueOf(user.getUserId()))
                .operatorName(user.getRealName())
                .deviceNo(req.getDeviceNo())
                .remark(req.getBarcodeContent())
                .build());

        line.setPickedQty(newPicked);
        if (StringUtils.hasText(req.getBatchNo())) {
            line.setBatchNo(req.getBatchNo());
        }
        line.setSourceLocation(req.getLocationCode());
        issueLineMapper.updateById(line);
        syncNoticeLinePickedQty(issue.getNoticeNo(), line.getMaterialCode(), req.getQuantity());

        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, line.getMaterialCode()));
        auditTrailService.log("PICK_ISSUE", issueNo, "SCAN_PICK", user.getRealName(),
                line.getMaterialCode() + "@" + req.getLocationCode() + " x" + req.getQuantity());

        boolean allComplete = isAllLinesPicked(issueNo);
        if (allComplete) {
            issue.setStatus("COMPLETED");
            issueMapper.updateById(issue);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("issueNo", issueNo);
        result.put("lineNo", line.getLineNo());
        result.put("materialCode", line.getMaterialCode());
        result.put("materialName", material != null ? material.getMaterialName() : line.getMaterialCode());
        result.put("locationCode", req.getLocationCode());
        result.put("pickedQty", newPicked);
        result.put("pickQty", line.getPickQty());
        result.put("allComplete", allComplete);
        return result;
    }

    public Map<String, Object> buildPrintData(String issueNo) {
        PickIssue issue = getIssue(issueNo);
        List<PickIssueLine> lines = issueLineMapper.selectList(new LambdaQueryWrapper<PickIssueLine>()
                .eq(PickIssueLine::getIssueNo, issueNo)
                .orderByAsc(PickIssueLine::getLineNo));

        PrepNotice prepNotice = null;
        Map<String, PrepNoticeLine> noticeLineByMaterial = new LinkedHashMap<>();
        if (StringUtils.hasText(issue.getNoticeNo())) {
            prepNotice = noticeMapper.selectOne(new LambdaQueryWrapper<PrepNotice>()
                    .eq(PrepNotice::getNoticeNo, issue.getNoticeNo())
                    .eq(PrepNotice::getDeleted, 0));
            List<PrepNoticeLine> noticeLines = lineMapper.selectList(new LambdaQueryWrapper<PrepNoticeLine>()
                    .eq(PrepNoticeLine::getNoticeNo, issue.getNoticeNo())
                    .orderByAsc(PrepNoticeLine::getLineNo));
            for (PrepNoticeLine nl : noticeLines) {
                noticeLineByMaterial.putIfAbsent(nl.getMaterialCode(), nl);
            }
        }

        ProductionOrder productionOrder = null;
        if (prepNotice != null && StringUtils.hasText(prepNotice.getProductionPlanNo())) {
            productionOrder = productionOrderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>()
                    .eq(ProductionOrder::getOrderNo, prepNotice.getProductionPlanNo())
                    .eq(ProductionOrder::getDeleted, 0));
        }

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("FBillNo", issue.getIssueNo());
        header.put("FBarCode", issue.getIssueNo());
        header.put("FDate", issue.getCreateTime() != null ? issue.getCreateTime().format(DATE_FMT) : "");
        header.put("FDocumentStatus", statusLabel(issue.getStatus()));
        header.put("FNoticeNo", issue.getNoticeNo());
        header.put("FWarehouseCode", issue.getWarehouseCode());
        header.put("FHandoverArea", issue.getHandoverArea());
        header.put("FPickerName", issue.getPickerName());
        if (prepNotice != null) {
            header.put("FProductionPlanNo", prepNotice.getProductionPlanNo());
        }
        if (productionOrder != null) {
            header.put("FProductCode", productionOrder.getProductCode());
            header.put("FProductName", productionOrder.getProductName());
            header.put("FPlanQty", productionOrder.getPlanQty());
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Map<String, Object>> materials = new LinkedHashMap<>();
        for (PickIssueLine line : lines) {
            PrepNoticeLine noticeLine = noticeLineByMaterial.get(line.getMaterialCode());
            BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, line.getMaterialCode()));
            String materialName = noticeLine != null && StringUtils.hasText(noticeLine.getMaterialName())
                    ? noticeLine.getMaterialName()
                    : (material != null ? material.getMaterialName() : line.getMaterialCode());
            String spec = material != null ? material.getSpecification() : "";
            String unit = material != null ? material.getUnitCode() : "";

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("FSeq", line.getLineNo());
            entry.put("FMaterialId", line.getMaterialCode());
            entry.put("FMaterialCode", line.getMaterialCode());
            entry.put("FMaterialName", materialName);
            entry.put("FMaterialModel", spec);
            entry.put("FUnitID", unit);
            entry.put("FPickQty", line.getPickQty());
            entry.put("FPickedQty", line.getPickedQty());
            entry.put("FQty", line.getPickQty());
            entry.put("FLocation", line.getSourceLocation());
            entry.put("FLot", line.getBatchNo());
            entry.put("FBarCode", line.getMaterialCode());
            entries.add(entry);

            if (material != null) {
                materials.put(material.getMaterialCode(), PrintDocumentHelper.materialRow(
                        material.getMaterialCode(), material.getMaterialName(),
                        material.getSpecification(), material.getUnitCode()));
            }
        }

        return PrintDocumentHelper.document(issue.getIssueNo(), "t_wms_pick_issue", "t_wms_pick_issue_entry",
                header, entries, materials);
    }

    private PickIssue getIssue(String issueNo) {
        PickIssue issue = issueMapper.selectOne(new LambdaQueryWrapper<PickIssue>()
                .eq(PickIssue::getIssueNo, issueNo)
                .eq(PickIssue::getDeleted, 0));
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拣配发料单不存在");
        }
        return issue;
    }

    private PickIssueLine resolvePickLine(String issueNo, PickIssueScanRequest req) {
        if (req.getLineNo() != null) {
            PickIssueLine line = issueLineMapper.selectOne(new LambdaQueryWrapper<PickIssueLine>()
                    .eq(PickIssueLine::getIssueNo, issueNo)
                    .eq(PickIssueLine::getLineNo, req.getLineNo()));
            if (line == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");
            }
            if (!line.getMaterialCode().equals(req.getMaterialCode())) {
                throw new BusinessException(ErrorCode.CONFLICT, "物料不匹配", "MATERIAL_MISMATCH");
            }
            return line;
        }
        List<PickIssueLine> lines = issueLineMapper.selectList(new LambdaQueryWrapper<PickIssueLine>()
                .eq(PickIssueLine::getIssueNo, issueNo)
                .eq(PickIssueLine::getMaterialCode, req.getMaterialCode())
                .orderByAsc(PickIssueLine::getLineNo));
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT, "发料单中无此物料", "LINE_NOT_FOUND");
        }
        for (PickIssueLine line : lines) {
            BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
            if (picked.compareTo(line.getPickQty()) < 0) {
                return line;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "该物料已全部拣完", "ALL_LINES_COMPLETED");
    }

    private void syncNoticeLinePickedQty(String noticeNo, String materialCode, BigDecimal delta) {
        if (!StringUtils.hasText(noticeNo)) {
            return;
        }
        PrepNoticeLine noticeLine = lineMapper.selectOne(new LambdaQueryWrapper<PrepNoticeLine>()
                .eq(PrepNoticeLine::getNoticeNo, noticeNo)
                .eq(PrepNoticeLine::getMaterialCode, materialCode));
        if (noticeLine == null) {
            return;
        }
        BigDecimal picked = noticeLine.getPickedQty() == null ? BigDecimal.ZERO : noticeLine.getPickedQty();
        noticeLine.setPickedQty(picked.add(delta));
        lineMapper.updateById(noticeLine);
    }

    private boolean isAllLinesPicked(String issueNo) {
        List<PickIssueLine> lines = issueLineMapper.selectList(new LambdaQueryWrapper<PickIssueLine>()
                .eq(PickIssueLine::getIssueNo, issueNo));
        for (PickIssueLine line : lines) {
            BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
            if (picked.compareTo(line.getPickQty()) < 0) {
                return false;
            }
        }
        return !lines.isEmpty();
    }

    private LoginUser currentUser() {
        try {
            return SecurityUtils.currentUser();
        } catch (BusinessException ex) {
            LoginUser fallback = new LoginUser();
            fallback.setUserId(0L);
            fallback.setUsername("pda");
            fallback.setRealName("PDA操作员");
            return fallback;
        }
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "PICKING" -> "拣货中";
            case "PICKED_UP" -> "已领料";
            case "COMPLETED" -> "拣货完成";
            default -> status;
        };
    }
}
