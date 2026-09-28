package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.auth.security.LoginUser;
import com.wms.common.security.SecurityUtils;
import com.wms.mes.MesConstants;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.mapper.MesDefectMapper;
import com.wms.mes.mapper.MesOpPlanMapper;
import com.wms.mes.mapper.MesReportMapper;
import com.wms.mes.mapper.MesTransferMapper;
import com.wms.mobile.dto.MobileMesSummaryVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 轻MES 移动端首页统计（按当前登录人汇总）。
 */
@Service
@RequiredArgsConstructor
public class MesSummaryService {

    private static final List<String> PENDING_STATUSES =
            List.of(MesConstants.SYNC_PENDING, MesConstants.SYNC_SYNCING);
    private static final List<String> FAILED_STATUSES =
            List.of(MesConstants.SYNC_FAILED, MesConstants.SYNC_MANUAL);
    private static final List<String> OPEN_DEFECT_STATUSES =
            List.of(MesConstants.REWORK_REWORKING, MesConstants.REWORK_SECONDARY);

    private final MesReportMapper reportMapper;
    private final MesTransferMapper transferMapper;
    private final MesDefectMapper defectMapper;
    private final MesOpPlanMapper opPlanMapper;
    private final MesHealthService healthService;

    public MobileMesSummaryVo summary() {
        LoginUser user = SecurityUtils.currentUser();
        String operatorId = String.valueOf(user.getUserId());
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long todayReport = count(reportMapper.selectCount(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getOperatorId, operatorId)
                .ge(MesReport::getReportTime, todayStart)));

        long pending = count(reportMapper.selectCount(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getOperatorId, operatorId)
                .in(MesReport::getSyncStatus, PENDING_STATUSES)))
                + count(transferMapper.selectCount(new LambdaQueryWrapper<MesTransfer>()
                .eq(MesTransfer::getOperatorId, operatorId)
                .in(MesTransfer::getSyncStatus, PENDING_STATUSES)));

        long failed = count(reportMapper.selectCount(new LambdaQueryWrapper<MesReport>()
                .eq(MesReport::getOperatorId, operatorId)
                .in(MesReport::getSyncStatus, FAILED_STATUSES)))
                + count(transferMapper.selectCount(new LambdaQueryWrapper<MesTransfer>()
                .eq(MesTransfer::getOperatorId, operatorId)
                .in(MesTransfer::getSyncStatus, FAILED_STATUSES)));

        long incompletePlan = count(opPlanMapper.selectCount(new LambdaQueryWrapper<MesOpPlan>()
                .apply("plan_qty > COALESCE(reported_qty, 0)")));

        long openDefect = count(defectMapper.selectCount(new LambdaQueryWrapper<MesDefect>()
                .in(MesDefect::getReworkStatus, OPEN_DEFECT_STATUSES)));

        MobileMesSummaryVo vo = new MobileMesSummaryVo();
        vo.setTodayReportCount(todayReport);
        vo.setPendingCount(pending);
        vo.setFailedCount(failed);
        vo.setIncompletePlanCount(incompletePlan);
        vo.setOpenDefectCount(openDefect);
        vo.setNetworkStatus(healthService.current().getNetworkStatus());
        return vo;
    }

    private static long count(Long value) {
        return value == null ? 0 : value;
    }
}
