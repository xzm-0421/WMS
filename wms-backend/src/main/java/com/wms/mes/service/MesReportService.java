package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.common.util.OrderNoGenerator;
import com.wms.mes.MesConstants;
import com.wms.mes.MesProperties;
import com.wms.mes.domain.MesNameMask;
import com.wms.mes.domain.MesQtyControl;
import com.wms.mes.domain.MesReworkTypeRule;
import com.wms.mes.dto.MesCancelRequest;
import com.wms.mes.dto.MesReportContextVo;
import com.wms.mes.dto.MesReportExcelRow;
import com.wms.mes.dto.MesReportSubmitRequest;
import com.wms.mes.dto.MesTransferSubmitRequest;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesEquipment;
import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.entity.MesProcess;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesReworkOp;
import com.wms.mes.entity.MesRoute;
import com.wms.mes.entity.MesRouteOp;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.mapper.MesDefectMapper;
import com.wms.mes.mapper.MesOpPlanMapper;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesReworkOpMapper;
import com.wms.mes.mapper.MesRouteMapper;
import com.wms.mes.mapper.MesTransferMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import com.alibaba.excel.EasyExcel;

/**
 * 现场报工、工序转移（Web 端录入，不含 PDA）。
 */
@Service
@RequiredArgsConstructor
public class MesReportService {

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;
    private final MesOpPlanMapper opPlanMapper;
    private final MesOpPlanService opPlanService;
    private final MesMasterDataService masterDataService;
    private final MesProcessLock processLock;
    private final MesProperties mesProperties;
    private final MesDefectMapper defectMapper;
    private final MesReworkOpMapper reworkOpMapper;
    private final MesRouteMapper routeMapper;

    public PageResult<MesReport> page(String reportNo, String moNo, String syncStatus,
                                      boolean maskName, boolean onlySelf,
                                      long current, long size) {
        LambdaQueryWrapper<MesReport> wrapper = buildReportWrapper(reportNo, moNo, syncStatus, onlySelf);
        Page<MesReport> page = reportMapper.selectPage(new Page<>(current, size), wrapper);
        if (maskName) {
            page.getRecords().forEach(row -> row.setOperatorName(MesNameMask.mask(row.getOperatorName())));
        }
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesReport getByNo(String reportNo) {
        MesReport report = reportMapper.selectOne(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getReportNo, reportNo));
        if (report == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报工记录不存在", "REPORT_NOT_FOUND");
        }
        return report;
    }

    public MesReportContextVo context(String moNo) {
        if (!StringUtils.hasText(moNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单号不能为空");
        }
        List<MesOpPlan> plans = opPlanService.listByMo(moNo.trim());
        if (plans.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该工单的工序计划，请先同步工序计划。", "PLAN_NOT_FOUND");
        }
        boolean allCompleted = plans.stream()
                .allMatch(p -> MesQtyControl.normalProcessCompleted(p.getPlanQty(), p.getReportedQty()));
        List<MesDefect> openDefects = defectMapper.selectList(new LambdaQueryWrapper<MesDefect>()
                .eq(MesDefect::getMoNo, moNo.trim())
                .in(MesDefect::getReworkStatus, MesConstants.REWORK_REWORKING, MesConstants.REWORK_SECONDARY));
        boolean hasRework = !openDefects.isEmpty();
        MesReportContextVo vo = new MesReportContextVo();
        vo.setMoNo(moNo.trim());
        vo.setPlans(plans);
        vo.setNormalCompleted(allCompleted);
        vo.setAllowedReportTypes(MesReworkTypeRule.allowedReportTypes(allCompleted, hasRework));
        vo.setTypeHint(allCompleted && hasRework ? MesReworkTypeRule.completedHint() : null);
        vo.setEquipment(masterDataService.listActiveEquipment(null));
        vo.setOpenDefectNos(openDefects.stream().map(MesDefect::getDefectNo).collect(Collectors.toList()));
        if (!openDefects.isEmpty()) {
            List<String> defectNos = openDefects.stream().map(MesDefect::getDefectNo).toList();
            vo.setReworkProcessCodes(reworkOpMapper.selectList(new LambdaQueryWrapper<MesReworkOp>()
                            .in(MesReworkOp::getDefectNo, defectNos))
                    .stream().map(MesReworkOp::getProcessCode).distinct().toList());
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public MesReport submit(MesReportSubmitRequest request) {
        validateSubmit(request);
        String moNo = request.getMoNo().trim();
        String processCode = request.getProcessCode().trim();
        return processLock.withLock(moNo, processCode, () -> doSubmit(request, moNo, processCode));
    }

    private MesReport doSubmit(MesReportSubmitRequest request, String moNo, String processCode) {
        if (StringUtils.hasText(request.getClientReportNo())) {
            MesReport existing = findByClientReportNo(request.getClientReportNo().trim());
            if (existing != null) {
                return existing;
            }
        }
        assertQueueCapacity();
        MesOpPlan plan = opPlanService.findByMoAndProcess(moNo, processCode);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到该工单工序计划", "PLAN_NOT_FOUND");
        }
        MesProcess process = masterDataService.getProcess(processCode);
        if (process != null && MesConstants.STATUS_DISABLED == nvl(process.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工序已停用，不可报工");
        }
        boolean normalCompleted = MesQtyControl.normalProcessCompleted(plan.getPlanQty(), plan.getReportedQty());
        String reportType = StringUtils.hasText(request.getReportType())
                ? request.getReportType().trim() : MesConstants.REPORT_NORMAL;
        if (MesConstants.REPORT_NORMAL.equals(reportType) && !MesReworkTypeRule.allowNormal(normalCompleted)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, MesReworkTypeRule.completedHint());
        }
        MesReworkOp reworkOp = null;
        if (MesConstants.REPORT_REWORK.equals(reportType)) {
            reworkOp = resolveReworkOp(request.getDefectNo(), processCode);
        }
        BigDecimal ratio = resolveRatio(plan, process);
        BigDecimal maxQty;
        if (reworkOp != null) {
            maxQty = MesQtyControl.maxAllowedQty(reworkOp.getPlanQty(), reworkOp.getReportedQty(), ratio);
        } else {
            maxQty = MesQtyControl.maxAllowedQty(plan.getPlanQty(), plan.getReportedQty(), ratio);
        }
        if (!MesQtyControl.withinLimit(request.getQty(), maxQty)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "报工数量超出计划上限，请调整后重新提交。", "QTY_EXCEED");
        }
        LoginUser user = SecurityUtils.currentUser();
        MesEquipment equipment = requireEquipment(request.getEquipmentCode(), processCode);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reportTime = resolveReportTime(request, now);
        MesReport report = new MesReport();
        report.setReportNo(OrderNoGenerator.next(MesConstants.PREFIX_REPORT));
        report.setMoNo(moNo);
        report.setProcessCode(processCode);
        report.setProcessName(plan.getProcessName());
        report.setPlanId(plan.getId());
        report.setReportType(reportType);
        report.setQty(request.getQty());
        report.setWeightKg(request.getWeightKg());
        report.setEquipmentCode(equipment.getEquipmentCode());
        report.setEquipmentName(equipment.getEquipmentName());
        report.setOperatorId(String.valueOf(user.getUserId()));
        report.setOperatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        report.setRemark(request.getRemark());
        report.setDefectNo(reworkOp == null ? null : reworkOp.getDefectNo());
        report.setReportTime(reportTime);
        report.setClientReportNo(StringUtils.hasText(request.getClientReportNo())
                ? request.getClientReportNo().trim() : null);
        report.setClientTime(request.getClientTime() != null ? reportTime : null);
        report.setSyncStatus(MesConstants.SYNC_PENDING);
        report.setRetryCount(0);
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException ex) {
            MesReport existing = findByClientReportNo(report.getClientReportNo());
            if (existing != null) {
                return existing;
            }
            throw ex;
        }

        if (MesConstants.REPORT_REWORK.equals(reportType) && reworkOp != null) {
            reworkOp.setReportedQty(nvl(reworkOp.getReportedQty()).add(request.getQty()));
            if (reworkOp.getReportedQty().compareTo(reworkOp.getPlanQty()) >= 0) {
                reworkOp.setOpStatus(MesConstants.OP_DONE);
            }
            reworkOpMapper.updateById(reworkOp);
            plan.setReworkReportedQty(nvl(plan.getReworkReportedQty()).add(request.getQty()));
        } else {
            plan.setReportedQty(nvl(plan.getReportedQty()).add(request.getQty()));
        }
        plan.setPlanStatus(MesConstants.PLAN_RUNNING);
        opPlanMapper.updateById(plan);
        maybeAutoTransfer(plan, request.getQty(), user);
        return report;
    }

    @Transactional(rollbackFor = Exception.class)
    public MesTransfer submitTransfer(MesTransferSubmitRequest request) {
        if (request == null || !StringUtils.hasText(request.getMoNo())
                || !StringUtils.hasText(request.getFromProcessCode())
                || !StringUtils.hasText(request.getToProcessCode())
                || request.getQty() == null || request.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单号、源工序、目标工序和数量不能为空");
        }
        String moNo = request.getMoNo().trim();
        String from = request.getFromProcessCode().trim();
        return processLock.withLock(moNo, from, () -> {
            assertQueueCapacity();
            MesOpPlan fromPlan = opPlanService.findByMoAndProcess(moNo, from);
            if (fromPlan == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "源工序计划不存在");
            }
            if (nvl(fromPlan.getReportedQty()).compareTo(request.getQty()) < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "转移数量不能大于已报工数量");
            }
            LoginUser user = SecurityUtils.currentUser();
            return insertTransfer(moNo, from, fromPlan.getProcessName(),
                    request.getToProcessCode().trim(), null, request.getQty(),
                    request.getRemark(), 0, user);
        });
    }

    public PageResult<MesTransfer> pageTransfer(String transferNo, String moNo, String syncStatus,
                                                long current, long size) {
        LambdaQueryWrapper<MesTransfer> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(transferNo), MesTransfer::getTransferNo, transferNo)
                .like(StringUtils.hasText(moNo), MesTransfer::getMoNo, moNo)
                .eq(StringUtils.hasText(syncStatus), MesTransfer::getSyncStatus, syncStatus)
                .orderByDesc(MesTransfer::getTransferTime);
        Page<MesTransfer> page = transferMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesTransfer getTransfer(String transferNo) {
        MesTransfer transfer = transferMapper.selectOne(new LambdaQueryWrapper<MesTransfer>()
                .eq(MesTransfer::getTransferNo, transferNo));
        if (transfer == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "转移单不存在");
        }
        return transfer;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelReport(String reportNo, MesCancelRequest request) {
        MesReport report = getByNo(reportNo);
        if (!MesConstants.SYNC_PENDING.equals(report.getSyncStatus())
                && !MesConstants.SYNC_FAILED.equals(report.getSyncStatus())
                && !MesConstants.SYNC_MANUAL.equals(report.getSyncStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅待同步或失败记录可取消暂存");
        }
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "取消暂存必须填写原因");
        }
        report.setSyncStatus(MesConstants.SYNC_CANCELLED);
        report.setCancelReason(request.getReason().trim());
        reportMapper.updateById(report);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryReport(String reportNo) {
        MesReport report = getByNo(reportNo);
        if (MesConstants.SYNC_SUCCESS.equals(report.getSyncStatus())
                || MesConstants.SYNC_CANCELLED.equals(report.getSyncStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可重试");
        }
        report.setSyncStatus(MesConstants.SYNC_PENDING);
        report.setNextRetryTime(LocalDateTime.now());
        report.setFailReason(null);
        reportMapper.updateById(report);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retryTransfer(String transferNo) {
        MesTransfer transfer = getTransfer(transferNo);
        if (MesConstants.SYNC_SUCCESS.equals(transfer.getSyncStatus())
                || MesConstants.SYNC_CANCELLED.equals(transfer.getSyncStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前状态不可重试");
        }
        transfer.setSyncStatus(MesConstants.SYNC_PENDING);
        transfer.setNextRetryTime(LocalDateTime.now());
        transfer.setFailReason(null);
        transferMapper.updateById(transfer);
    }

    public void exportReports(String reportNo, String moNo, String syncStatus, boolean onlySelf,
                              OutputStream outputStream) {
        LambdaQueryWrapper<MesReport> wrapper = buildReportWrapper(reportNo, moNo, syncStatus, onlySelf);
        List<MesReport> records = reportMapper.selectList(wrapper);
        List<MesReportExcelRow> rows = records.stream().map(item -> {
            MesReportExcelRow row = new MesReportExcelRow();
            row.setReportNo(item.getReportNo());
            row.setMoNo(item.getMoNo());
            row.setProcessCode(item.getProcessCode());
            row.setProcessName(item.getProcessName());
            row.setReportType(item.getReportType());
            row.setQty(item.getQty());
            row.setWeightKg(item.getWeightKg());
            row.setEquipmentName(item.getEquipmentName());
            row.setOperatorName(item.getOperatorName());
            row.setReportTime(item.getReportTime());
            row.setSyncStatus(item.getSyncStatus());
            row.setSyncTime(item.getSyncTime());
            row.setErpBillNo(item.getErpBillNo());
            row.setFailReason(item.getFailReason());
            return row;
        }).toList();
        EasyExcel.write(outputStream, MesReportExcelRow.class).sheet("报工记录").doWrite(rows);
    }

    public boolean canViewAllReports() {
        LoginUser user = SecurityUtils.currentUser();
        if (user.isSuperAdmin() || (user.getRoles() != null && user.getRoles().contains(SecurityUtils.SUPER_ADMIN_ROLE))) {
            return true;
        }
        List<String> perms = user.getPermissions();
        return perms != null && (perms.contains("mes:report:list:all") || perms.contains("mes:sync:panel"));
    }

    private LambdaQueryWrapper<MesReport> buildReportWrapper(String reportNo, String moNo, String syncStatus,
                                                             boolean onlySelf) {
        LambdaQueryWrapper<MesReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(reportNo), MesReport::getReportNo, reportNo)
                .like(StringUtils.hasText(moNo), MesReport::getMoNo, moNo)
                .eq(StringUtils.hasText(syncStatus), MesReport::getSyncStatus, syncStatus)
                .orderByDesc(MesReport::getReportTime);
        if (onlySelf) {
            LoginUser user = SecurityUtils.currentUser();
            wrapper.and(w -> w.eq(MesReport::getOperatorId, String.valueOf(user.getUserId()))
                    .or().eq(MesReport::getOperatorId, user.getUsername()));
        }
        return wrapper;
    }

    private void maybeAutoTransfer(MesOpPlan plan, BigDecimal qty, LoginUser user) {
        List<MesRouteOp> ops = masterDataService.listRouteOps(plan.getProductCode());
        if (ops.isEmpty()) {
            return;
        }
        int index = -1;
        for (int i = 0; i < ops.size(); i++) {
            if (plan.getProcessCode().equals(ops.get(i).getProcessCode())) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= ops.size() - 1) {
            return;
        }
        MesRouteOp current = ops.get(index);
        MesRouteOp next = ops.get(index + 1);
        String currentWc = firstNonBlank(current.getWorkCenterCode(), deptOf(plan.getProcessCode()));
        String nextWc = firstNonBlank(next.getWorkCenterCode(), deptOf(next.getProcessCode()));
        if (!StringUtils.hasText(currentWc) || !currentWc.equals(nextWc)) {
            return;
        }
        insertTransfer(plan.getMoNo(), current.getProcessCode(), current.getProcessName(),
                next.getProcessCode(), next.getProcessName(), qty, "同工作中心自动转移", 1, user);
    }

    private MesTransfer insertTransfer(String moNo, String fromCode, String fromName,
                                       String toCode, String toName, BigDecimal qty,
                                       String remark, int autoFlag, LoginUser user) {
        MesProcess toProcess = masterDataService.getProcess(toCode);
        MesTransfer transfer = new MesTransfer();
        transfer.setTransferNo(OrderNoGenerator.next(MesConstants.PREFIX_TRANSFER));
        transfer.setMoNo(moNo);
        transfer.setFromProcessCode(fromCode);
        transfer.setFromProcessName(fromName);
        transfer.setToProcessCode(toCode);
        transfer.setToProcessName(toName != null ? toName : (toProcess == null ? toCode : toProcess.getProcessName()));
        transfer.setQty(qty);
        transfer.setAutoFlag(autoFlag);
        transfer.setOperatorId(String.valueOf(user.getUserId()));
        transfer.setOperatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
        transfer.setRemark(remark);
        transfer.setTransferTime(LocalDateTime.now());
        transfer.setSyncStatus(MesConstants.SYNC_PENDING);
        transfer.setRetryCount(0);
        transferMapper.insert(transfer);
        return transfer;
    }

    private MesReworkOp resolveReworkOp(String defectNo, String processCode) {
        if (!StringUtils.hasText(defectNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "返工报工需选择关联不良单");
        }
        MesReworkOp op = reworkOpMapper.selectOne(new LambdaQueryWrapper<MesReworkOp>()
                .eq(MesReworkOp::getDefectNo, defectNo.trim())
                .eq(MesReworkOp::getProcessCode, processCode));
        if (op == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不良单返工序列中不包含该工序");
        }
        return op;
    }

    private MesEquipment requireEquipment(String equipmentCode, String processCode) {
        if (!StringUtils.hasText(equipmentCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择设备");
        }
        List<MesEquipment> list = masterDataService.listActiveEquipment(null);
        MesEquipment matched = list.stream()
                .filter(item -> equipmentCode.trim().equals(item.getEquipmentCode()))
                .findFirst()
                .orElse(null);
        if (matched == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "设备不存在或已停用");
        }
        return matched;
    }

    private BigDecimal resolveRatio(MesOpPlan plan, MesProcess process) {
        BigDecimal processRatio = process == null ? null : process.getOverReceiveRatio();
        if (processRatio == null && plan.getOverReceiveRatio() != null) {
            processRatio = plan.getOverReceiveRatio();
        }
        BigDecimal productRatio = null;
        if (StringUtils.hasText(plan.getProductCode())) {
            Page<MesRoute> page = routeMapper.selectPage(new Page<>(1, 1),
                    new LambdaQueryWrapper<MesRoute>()
                            .eq(MesRoute::getProductCode, plan.getProductCode())
                            .eq(MesRoute::getStatus, MesConstants.STATUS_ACTIVE)
                            .orderByDesc(MesRoute::getVersionNo));
            if (!page.getRecords().isEmpty()) {
                productRatio = page.getRecords().get(0).getOverReceiveRatio();
            }
        }
        return MesQtyControl.resolveRatio(processRatio, productRatio, mesProperties.getDefaultOverReceiveRatio());
    }

    private void assertQueueCapacity() {
        Long pending = reportMapper.selectCount(new LambdaQueryWrapper<MesReport>()
                .in(MesReport::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_SYNCING, MesConstants.SYNC_FAILED));
        Long pendingTransfer = transferMapper.selectCount(new LambdaQueryWrapper<MesTransfer>()
                .in(MesTransfer::getSyncStatus, MesConstants.SYNC_PENDING, MesConstants.SYNC_SYNCING, MesConstants.SYNC_FAILED));
        long total = (pending == null ? 0 : pending) + (pendingTransfer == null ? 0 : pendingTransfer);
        if (total >= mesProperties.getQueueCapacity()) {
            throw new BusinessException(ErrorCode.CONFLICT, "暂存队列已满，请尽快处理已失败数据。", "QUEUE_FULL");
        }
    }

    private MesReport findByClientReportNo(String clientReportNo) {
        if (!StringUtils.hasText(clientReportNo)) {
            return null;
        }
        return reportMapper.selectOne(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getClientReportNo, clientReportNo)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
    }

    private LocalDateTime resolveReportTime(MesReportSubmitRequest request, LocalDateTime fallback) {
        if (request.getClientTime() == null) {
            return fallback;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(request.getClientTime()), ZoneId.systemDefault());
    }

    private void validateSubmit(MesReportSubmitRequest request) {
        if (request == null || !StringUtils.hasText(request.getMoNo()) || !StringUtils.hasText(request.getProcessCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单号和工序不能为空");
        }
        if (request.getQty() == null || request.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报工数量必须大于 0");
        }
    }

    private String deptOf(String processCode) {
        MesProcess process = masterDataService.getProcess(processCode);
        return process == null ? null : process.getDeptCode();
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        return StringUtils.hasText(b) ? b.trim() : "";
    }
}
