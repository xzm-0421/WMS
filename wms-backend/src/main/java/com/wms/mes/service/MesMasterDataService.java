package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.result.PageResult;
import com.wms.mes.MesConstants;
import com.wms.mes.dto.MesRouteVo;
import com.wms.mes.entity.MesEquipment;
import com.wms.mes.entity.MesProcess;
import com.wms.mes.entity.MesRoute;
import com.wms.mes.entity.MesRouteOp;
import com.wms.mes.mapper.MesEquipmentMapper;
import com.wms.mes.mapper.MesProcessMapper;
import com.wms.mes.mapper.MesRouteMapper;
import com.wms.mes.mapper.MesRouteOpMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 轻 MES 基础资料只读查询。
 */
@Service
@RequiredArgsConstructor
public class MesMasterDataService {

    private final MesProcessMapper processMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final MesRouteMapper routeMapper;
    private final MesRouteOpMapper routeOpMapper;

    public PageResult<MesProcess> pageProcess(String processCode, String processName, Integer status,
                                              long current, long size) {
        LambdaQueryWrapper<MesProcess> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(processCode), MesProcess::getProcessCode, processCode)
                .like(StringUtils.hasText(processName), MesProcess::getProcessName, processName)
                .eq(status != null, MesProcess::getStatus, status)
                .orderByAsc(MesProcess::getProcessCode);
        var page = processMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesProcess getProcess(String processCode) {
        return processMapper.selectOne(new LambdaQueryWrapper<MesProcess>()
                .eq(MesProcess::getProcessCode, processCode));
    }

    public List<MesProcess> listActiveProcesses() {
        return processMapper.selectList(new LambdaQueryWrapper<MesProcess>()
                .eq(MesProcess::getStatus, MesConstants.STATUS_ACTIVE)
                .orderByAsc(MesProcess::getProcessCode));
    }

    public PageResult<MesEquipment> pageEquipment(String equipmentCode, String equipmentName, String processCode,
                                                 Integer status, long current, long size) {
        LambdaQueryWrapper<MesEquipment> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(equipmentCode), MesEquipment::getEquipmentCode, equipmentCode)
                .like(StringUtils.hasText(equipmentName), MesEquipment::getEquipmentName, equipmentName)
                .eq(StringUtils.hasText(processCode), MesEquipment::getProcessCode, processCode)
                .eq(status != null, MesEquipment::getStatus, status)
                .orderByAsc(MesEquipment::getEquipmentCode);
        var page = equipmentMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesEquipment getEquipment(String equipmentCode) {
        return equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getEquipmentCode, equipmentCode));
    }

    public List<MesEquipment> listActiveEquipment(String processCode) {
        LambdaQueryWrapper<MesEquipment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesEquipment::getStatus, MesConstants.STATUS_ACTIVE)
                .eq(StringUtils.hasText(processCode), MesEquipment::getProcessCode, processCode)
                .orderByAsc(MesEquipment::getEquipmentCode);
        return equipmentMapper.selectList(wrapper);
    }

    public PageResult<MesRoute> pageRoute(String productCode, String productName, long current, long size) {
        LambdaQueryWrapper<MesRoute> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(productCode), MesRoute::getProductCode, productCode)
                .like(StringUtils.hasText(productName), MesRoute::getProductName, productName)
                .eq(MesRoute::getStatus, MesConstants.STATUS_ACTIVE)
                .orderByAsc(MesRoute::getProductCode)
                .orderByDesc(MesRoute::getVersionNo);
        var page = routeMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesRouteVo getRoute(Long id) {
        MesRoute header = routeMapper.selectById(id);
        MesRouteVo vo = new MesRouteVo();
        vo.setHeader(header);
        if (header != null) {
            vo.setOperations(routeOpMapper.selectList(new LambdaQueryWrapper<MesRouteOp>()
                    .eq(MesRouteOp::getRouteId, header.getId())
                    .orderByAsc(MesRouteOp::getSeqNo)));
        }
        return vo;
    }

    public List<MesRouteOp> listRouteOps(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            return List.of();
        }
        var page = routeMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1),
                new LambdaQueryWrapper<MesRoute>()
                        .eq(MesRoute::getProductCode, productCode.trim())
                        .eq(MesRoute::getStatus, MesConstants.STATUS_ACTIVE)
                        .orderByDesc(MesRoute::getVersionNo));
        MesRoute route = page.getRecords().isEmpty() ? null : page.getRecords().get(0);
        if (route == null) {
            return List.of();
        }
        return routeOpMapper.selectList(new LambdaQueryWrapper<MesRouteOp>()
                .eq(MesRouteOp::getRouteId, route.getId())
                .orderByAsc(MesRouteOp::getSeqNo));
    }
}
