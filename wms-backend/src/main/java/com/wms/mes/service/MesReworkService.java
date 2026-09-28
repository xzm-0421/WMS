package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.mes.MesConstants;
import com.wms.mes.domain.MesReworkStatusRule;
import com.wms.mes.dto.MesDefectCreateRequest;
import com.wms.mes.dto.MesReworkSequenceVo;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesErpOutbox;
import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.entity.MesProcess;
import com.wms.mes.entity.MesReworkOp;
import com.wms.mes.entity.MesRouteOp;
import com.wms.mes.mapper.MesDefectMapper;
import com.wms.mes.mapper.MesErpOutboxMapper;
import com.wms.mes.mapper.MesReworkOpMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 不良登记与返工序列。
 */
@Service
@RequiredArgsConstructor
public class MesReworkService {

    private final MesDefectMapper defectMapper;
    private final MesReworkOpMapper reworkOpMapper;
    private final MesErpOutboxMapper outboxMapper;
    private final MesOpPlanService opPlanService;
    private final MesMasterDataService masterDataService;

    public PageResult<MesDefect> page(String moNo, String reworkStatus, long current, long size) {
        LambdaQueryWrapper<MesDefect> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(moNo), MesDefect::getMoNo, moNo)
                .eq(StringUtils.hasText(reworkStatus), MesDefect::getReworkStatus, reworkStatus)
                .orderByDesc(MesDefect::getCreateTime);
        Page<MesDefect> page = defectMapper.selectPage(new Page<>(current, size), wrapper);
        attachSyncStatus(page.getRecords());
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private void attachSyncStatus(List<MesDefect> defects) {
        if (defects == null || defects.isEmpty()) {
            return;
        }
        List<String> nos = defects.stream().map(MesDefect::getDefectNo).toList();
        List<MesErpOutbox> tasks = outboxMapper.selectList(new LambdaQueryWrapper<MesErpOutbox>()
                .in(MesErpOutbox::getBizNo, nos));
        Map<String, String> createStatus = new HashMap<>();
        Map<String, String> submitStatus = new HashMap<>();
        for (MesErpOutbox task : tasks) {
            String key = task.getBizType() + "|" + task.getBizNo();
            if (MesConstants.ACTION_SUBMIT.equals(task.getAction())) {
                submitStatus.put(key, task.getSyncStatus());
            } else {
                createStatus.put(key, task.getSyncStatus());
            }
        }
        for (MesDefect defect : defects) {
            defect.setDefectSyncStatus(resolveStatus(createStatus, submitStatus,
                    MesConstants.BIZ_DEFECT + "|" + defect.getDefectNo()));
            defect.setReworkSyncStatus(resolveStatus(createStatus, submitStatus,
                    MesConstants.BIZ_REWORK + "|" + defect.getDefectNo()));
        }
    }

    private static String resolveStatus(Map<String, String> createStatus, Map<String, String> submitStatus, String key) {
        String submit = submitStatus.get(key);
        return submit != null ? submit : createStatus.get(key);
    }

    public MesReworkSequenceVo sequence(String defectNo) {
        MesDefect defect = getDefect(defectNo);
        MesReworkSequenceVo vo = new MesReworkSequenceVo();
        vo.setDefect(defect);
        vo.setOperations(reworkOpMapper.selectList(new LambdaQueryWrapper<MesReworkOp>()
                .eq(MesReworkOp::getDefectNo, defectNo)
                .orderByAsc(MesReworkOp::getSeqNo)));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public MesReworkSequenceVo create(MesDefectCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getMoNo())
                || !StringUtils.hasText(request.getSourceProcessCode())
                || request.getDefectQty() == null || request.getDefectQty().compareTo(BigDecimal.ZERO) <= 0
                || !StringUtils.hasText(request.getDefectType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工单、源工序、不良数量和不良类型不能为空");
        }
        MesOpPlan plan = opPlanService.findByMoAndProcess(request.getMoNo().trim(), request.getSourceProcessCode().trim());
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "源工序尚无报工计划");
        }
        BigDecimal reported = plan.getReportedQty() == null ? BigDecimal.ZERO : plan.getReportedQty();
        if (request.getDefectQty().compareTo(reported) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次不良数量不能大于该工序已报工数量");
        }
        List<String> processCodes = resolveReworkProcesses(plan, request);
        if (processCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法确定返工工序序列，请指定返工工序或维护工艺路线");
        }
        MesDefect defect = new MesDefect();
        defect.setDefectNo(OrderNoGenerator.next(MesConstants.PREFIX_DEFECT));
        defect.setMoNo(plan.getMoNo());
        defect.setSourceProcessCode(plan.getProcessCode());
        defect.setSourceProcessName(plan.getProcessName());
        defect.setDefectQty(request.getDefectQty());
        defect.setDefectType(request.getDefectType().trim());
        defect.setDefectDesc(request.getDefectDesc());
        defect.setOwnerName(request.getOwnerName());
        defect.setReworkStatus(MesConstants.REWORK_REWORKING);
        defectMapper.insert(defect);

        int seq = 1;
        for (String processCode : processCodes) {
            MesProcess process = masterDataService.getProcess(processCode);
            MesReworkOp op = new MesReworkOp();
            op.setDefectNo(defect.getDefectNo());
            op.setSeqNo(seq++);
            op.setProcessCode(processCode);
            op.setProcessName(process == null ? processCode : process.getProcessName());
            op.setPlanQty(request.getDefectQty());
            op.setReportedQty(BigDecimal.ZERO);
            op.setOpStatus(MesConstants.OP_PENDING);
            reworkOpMapper.insert(op);
        }
        enqueue(defect.getDefectNo(), MesConstants.ACTION_CREATE);
        return sequence(defect.getDefectNo());
    }

    /** 完成返工：REWORKING -> DONE，并入队提交 ERP 单据 */
    @Transactional(rollbackFor = Exception.class)
    public MesDefect complete(String defectNo) {
        MesDefect defect = transit(defectNo, MesConstants.REWORK_DONE, "当前状态不可完成返工",
                MesReworkStatusRule::canComplete);
        enqueue(defectNo, MesConstants.ACTION_SUBMIT);
        return defect;
    }

    private void enqueue(String defectNo, String action) {
        for (String bizType : List.of(MesConstants.BIZ_DEFECT, MesConstants.BIZ_REWORK)) {
            Long exists = outboxMapper.selectCount(new LambdaQueryWrapper<MesErpOutbox>()
                    .eq(MesErpOutbox::getBizType, bizType)
                    .eq(MesErpOutbox::getBizNo, defectNo)
                    .eq(MesErpOutbox::getAction, action));
            if (exists != null && exists > 0) {
                continue;
            }
            MesErpOutbox task = new MesErpOutbox();
            task.setBizType(bizType);
            task.setBizNo(defectNo);
            task.setAction(action);
            task.setSyncStatus(MesConstants.SYNC_PENDING);
            task.setRetryCount(0);
            outboxMapper.insert(task);
        }
    }

    /** 再次不良：REWORKING/DONE -> SECONDARY */
    @Transactional(rollbackFor = Exception.class)
    public MesDefect markSecondary(String defectNo) {
        return transit(defectNo, MesConstants.REWORK_SECONDARY, "当前状态不可标记二次返工", MesReworkStatusRule::canSecondary);
    }

    /** 关闭返工：DONE/SECONDARY -> CLOSED */
    @Transactional(rollbackFor = Exception.class)
    public MesDefect close(String defectNo) {
        return transit(defectNo, MesConstants.REWORK_CLOSED, "当前状态不可关闭返工", MesReworkStatusRule::canClose);
    }

    private MesDefect transit(String defectNo, String target, String message,
                              java.util.function.Predicate<String> allowed) {
        MesDefect defect = getDefect(defectNo);
        if (!allowed.test(defect.getReworkStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
        defect.setReworkStatus(target);
        defectMapper.updateById(defect);
        return defect;
    }

    private MesDefect getDefect(String defectNo) {
        MesDefect defect = defectMapper.selectOne(new LambdaQueryWrapper<MesDefect>()
                .eq(MesDefect::getDefectNo, defectNo));
        if (defect == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "不良单不存在");
        }
        return defect;
    }

    private List<String> resolveReworkProcesses(MesOpPlan plan, MesDefectCreateRequest request) {
        if (request.getReworkProcessCodes() != null && !request.getReworkProcessCodes().isEmpty()) {
            return request.getReworkProcessCodes().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
        List<MesRouteOp> ops = masterDataService.listRouteOps(plan.getProductCode());
        if (ops.isEmpty()) {
            return List.of(plan.getProcessCode());
        }
        int start = -1;
        for (int i = 0; i < ops.size(); i++) {
            if (plan.getProcessCode().equals(ops.get(i).getProcessCode())) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            return List.of(plan.getProcessCode());
        }
        int end = ops.size() - 1;
        for (int i = start; i < ops.size(); i++) {
            MesRouteOp op = ops.get(i);
            if (nvl(op.getInspectFlag()) == MesConstants.FLAG_YES
                    || nvl(op.getReworkJoinFlag()) == MesConstants.FLAG_YES
                    || Boolean.TRUE.equals(op.getIsConvergeOp())) {
                end = i;
                break;
            }
        }
        List<String> codes = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            codes.add(ops.get(i).getProcessCode());
        }
        return codes;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
