package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.mes.MesConstants;
import com.wms.mes.dto.MesRouteVo;
import com.wms.mes.entity.MesEquipment;
import com.wms.mes.entity.MesPersonnel;
import com.wms.mes.entity.MesProcess;
import com.wms.mes.entity.MesResource;
import com.wms.mes.entity.MesRoute;
import com.wms.mes.entity.MesRouteOp;
import com.wms.mes.entity.MesWorkCenter;
import com.wms.mes.mapper.MesEquipmentMapper;
import com.wms.mes.mapper.MesPersonnelMapper;
import com.wms.mes.mapper.MesProcessMapper;
import com.wms.mes.mapper.MesResourceMapper;
import com.wms.mes.mapper.MesRouteMapper;
import com.wms.mes.mapper.MesRouteOpMapper;
import com.wms.mes.mapper.MesWorkCenterMapper;
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
    private final MesWorkCenterMapper workCenterMapper;
    private final MesResourceMapper resourceMapper;
    private final MesPersonnelMapper personnelMapper;

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
        return listRouteOps(null, productCode);
    }

    /**
     * 按产品内码优先（为空回退编码）取最新启用工艺路线的工序明细。
     */
    public List<MesRouteOp> listRouteOps(Long erpMaterialId, String productCode) {
        MesRoute route = null;
        if (erpMaterialId != null) {
            route = latestActiveRoute(new LambdaQueryWrapper<MesRoute>()
                    .eq(MesRoute::getErpMaterialId, erpMaterialId));
        }
        if (route == null && StringUtils.hasText(productCode)) {
            route = latestActiveRoute(new LambdaQueryWrapper<MesRoute>()
                    .eq(MesRoute::getProductCode, productCode.trim()));
        }
        if (route == null) {
            return List.of();
        }
        return routeOpMapper.selectList(new LambdaQueryWrapper<MesRouteOp>()
                .eq(MesRouteOp::getRouteId, route.getId())
                .orderByAsc(MesRouteOp::getSeqNo));
    }

    private MesRoute latestActiveRoute(LambdaQueryWrapper<MesRoute> wrapper) {
        wrapper.eq(MesRoute::getStatus, MesConstants.STATUS_ACTIVE)
                .orderByDesc(MesRoute::getVersionNo);
        var page = routeMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1), wrapper);
        return page.getRecords().isEmpty() ? null : page.getRecords().get(0);
    }

    public PageResult<MesWorkCenter> pageWorkCenter(String code, String name, Integer status,
                                                    long current, long size) {
        LambdaQueryWrapper<MesWorkCenter> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(code), MesWorkCenter::getWorkCenterCode, code)
                .like(StringUtils.hasText(name), MesWorkCenter::getWorkCenterName, name)
                .eq(status != null, MesWorkCenter::getStatus, status)
                .orderByAsc(MesWorkCenter::getWorkCenterCode);
        var page = workCenterMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesWorkCenter getWorkCenter(String workCenterCode) {
        return workCenterMapper.selectOne(new LambdaQueryWrapper<MesWorkCenter>()
                .eq(MesWorkCenter::getWorkCenterCode, workCenterCode));
    }

    public List<MesWorkCenter> listActiveWorkCenters() {
        return workCenterMapper.selectList(new LambdaQueryWrapper<MesWorkCenter>()
                .eq(MesWorkCenter::getStatus, MesConstants.STATUS_ACTIVE)
                .orderByAsc(MesWorkCenter::getWorkCenterCode));
    }

    public PageResult<MesResource> pageResource(String code, String name, String resourceType,
                                                String workCenterCode, Integer status, long current, long size) {
        LambdaQueryWrapper<MesResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(code), MesResource::getResourceCode, code)
                .like(StringUtils.hasText(name), MesResource::getResourceName, name)
                .eq(StringUtils.hasText(resourceType), MesResource::getResourceType, resourceType)
                .eq(StringUtils.hasText(workCenterCode), MesResource::getWorkCenterCode, workCenterCode)
                .eq(status != null, MesResource::getStatus, status)
                .orderByAsc(MesResource::getResourceCode);
        var page = resourceMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesResource getResource(String resourceCode) {
        return resourceMapper.selectOne(new LambdaQueryWrapper<MesResource>()
                .eq(MesResource::getResourceCode, resourceCode));
    }

    public List<MesResource> listActiveResources(String workCenterCode) {
        LambdaQueryWrapper<MesResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesResource::getStatus, MesConstants.STATUS_ACTIVE)
                .eq(StringUtils.hasText(workCenterCode), MesResource::getWorkCenterCode, workCenterCode)
                .orderByAsc(MesResource::getResourceCode);
        return resourceMapper.selectList(wrapper);
    }

    public PageResult<MesPersonnel> pagePersonnel(String code, String name, String deptCode,
                                                  String workCenterCode, Integer status, long current, long size) {
        LambdaQueryWrapper<MesPersonnel> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(code), MesPersonnel::getPersonnelCode, code)
                .like(StringUtils.hasText(name), MesPersonnel::getPersonnelName, name)
                .eq(StringUtils.hasText(deptCode), MesPersonnel::getDeptCode, deptCode)
                .eq(StringUtils.hasText(workCenterCode), MesPersonnel::getWorkCenterCode, workCenterCode)
                .eq(status != null, MesPersonnel::getStatus, status)
                .orderByAsc(MesPersonnel::getPersonnelCode);
        var page = personnelMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public MesPersonnel getPersonnel(String personnelCode) {
        return personnelMapper.selectOne(new LambdaQueryWrapper<MesPersonnel>()
                .eq(MesPersonnel::getPersonnelCode, personnelCode));
    }

    /**
     * 生产人员选项。按工作中心过滤（若有）；仅返回启用且标记生产人员的人员。
     */
    public List<MesPersonnel> listActivePersonnel(String workCenterCode) {
        LambdaQueryWrapper<MesPersonnel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesPersonnel::getStatus, MesConstants.STATUS_ACTIVE)
                .eq(MesPersonnel::getProductionFlag, MesConstants.FLAG_YES)
                .eq(StringUtils.hasText(workCenterCode), MesPersonnel::getWorkCenterCode, workCenterCode)
                .orderByAsc(MesPersonnel::getPersonnelCode);
        return personnelMapper.selectList(wrapper);
    }

    /**
     * 绑定/解绑系统用户（本地字段，同步不覆盖）。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public MesPersonnel bindPersonnelUser(String personnelCode, Long sysUserId, String sysUsername) {
        if (!StringUtils.hasText(personnelCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "人员编码不能为空");
        }
        MesPersonnel personnel = getPersonnel(personnelCode.trim());
        if (personnel == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "人员不存在", "PERSONNEL_NOT_FOUND");
        }
        personnel.setSysUserId(sysUserId);
        personnel.setSysUsername(StringUtils.hasText(sysUsername) ? sysUsername.trim() : null);
        personnelMapper.updateById(personnel);
        return personnel;
    }
}
