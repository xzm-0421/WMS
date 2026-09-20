package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesOpPlanDetailVo;
import com.wms.mes.dto.MesSyncResult;
import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.mapper.MesOpPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 工序计划查询与手动重试同步。
 */
@Service
@RequiredArgsConstructor
public class MesOpPlanService {

    private final MesOpPlanMapper opPlanMapper;
    private final MesKingdeePullService pullService;
    private final MesMasterDataService masterDataService;

    public PageResult<MesOpPlan> page(String moNo, String productCode, String status,
                                      LocalDate planStartFrom, LocalDate planStartTo,
                                      long current, long size) {
        LambdaQueryWrapper<MesOpPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(moNo), MesOpPlan::getMoNo, moNo)
                .like(StringUtils.hasText(productCode), MesOpPlan::getProductCode, productCode)
                .eq(StringUtils.hasText(status), MesOpPlan::getPlanStatus, status)
                .ge(planStartFrom != null, MesOpPlan::getPlanStart, planStartFrom)
                .le(planStartTo != null, MesOpPlan::getPlanStart, planStartTo)
                .orderByDesc(MesOpPlan::getLastSyncTime)
                .orderByDesc(MesOpPlan::getId);
        Page<MesOpPlan> page = opPlanMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesOpPlan getById(Long id) {
        MesOpPlan plan = opPlanMapper.selectById(id);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "工序计划不存在", "PLAN_NOT_FOUND");
        }
        return plan;
    }

    public MesOpPlanDetailVo detail(Long id) {
        MesOpPlan plan = getById(id);
        MesOpPlanDetailVo vo = new MesOpPlanDetailVo();
        vo.setPlan(plan);
        vo.setRouteOps(masterDataService.listRouteOps(plan.getProductCode()));
        return vo;
    }

    public List<MesOpPlan> listByMo(String moNo) {
        if (!StringUtils.hasText(moNo)) {
            return List.of();
        }
        return opPlanMapper.selectList(new LambdaQueryWrapper<MesOpPlan>()
                .eq(MesOpPlan::getMoNo, moNo.trim())
                .orderByAsc(MesOpPlan::getSeqNo)
                .orderByAsc(MesOpPlan::getProcessCode));
    }

    public MesOpPlan findByMoAndProcess(String moNo, String processCode) {
        Page<MesOpPlan> page = opPlanMapper.selectPage(new Page<>(1, 1),
                new LambdaQueryWrapper<MesOpPlan>()
                        .eq(MesOpPlan::getMoNo, moNo)
                        .eq(MesOpPlan::getProcessCode, processCode)
                        .orderByDesc(MesOpPlan::getId));
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    public MesSyncResult refresh(String moNo) {
        return pullService.syncOpPlans(moNo);
    }

    public MesSyncResult retry(Long id) {
        MesOpPlan plan = getById(id);
        return pullService.syncOpPlans(plan.getMoNo());
    }
}
