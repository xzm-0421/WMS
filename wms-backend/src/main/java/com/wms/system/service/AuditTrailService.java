package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.result.PageResult;
import com.wms.system.entity.WmsAuditTrail;
import com.wms.system.mapper.WmsAuditTrailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditTrailService {

    private final WmsAuditTrailMapper auditMapper;

    public void log(String bizType, String bizNo, String action, String operatorName, String detail) {
        WmsAuditTrail trail = new WmsAuditTrail();
        trail.setBizType(bizType);
        trail.setBizNo(bizNo);
        trail.setAction(action);
        trail.setOperatorName(operatorName);
        trail.setDetailJson(detail);
        trail.setCreateTime(LocalDateTime.now());
        auditMapper.insert(trail);
    }

    public PageResult<WmsAuditTrail> page(String bizType, long current, long size) {
        Page<WmsAuditTrail> page = auditMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<WmsAuditTrail>()
                        .eq(bizType != null, WmsAuditTrail::getBizType, bizType)
                        .orderByDesc(WmsAuditTrail::getCreateTime));
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<WmsAuditTrail> recent(int limit) {
        return auditMapper.selectList(new LambdaQueryWrapper<WmsAuditTrail>()
                .orderByDesc(WmsAuditTrail::getCreateTime)
                .last("OFFSET 0 ROWS FETCH NEXT " + limit + " ROWS ONLY"));
    }
}
