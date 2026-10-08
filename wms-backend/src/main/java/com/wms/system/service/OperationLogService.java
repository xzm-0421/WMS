package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.result.PageResult;
import com.wms.system.entity.SysOperationLog;
import com.wms.system.mapper.SysOperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 系统操作日志读写。
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final SysOperationLogMapper operationLogMapper;

    public void save(SysOperationLog log) {
        operationLogMapper.insert(log);
    }

    public PageResult<SysOperationLog> page(String operatorName, String module, String operationType,
                                            String responseResult, LocalDateTime start, LocalDateTime end,
                                            long current, long size) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(operatorName), SysOperationLog::getOperatorName, operatorName)
                .eq(StringUtils.hasText(module), SysOperationLog::getModule, module)
                .eq(StringUtils.hasText(operationType), SysOperationLog::getOperationType, operationType)
                .eq(StringUtils.hasText(responseResult), SysOperationLog::getResponseResult, responseResult)
                .ge(start != null, SysOperationLog::getOperationTime, start)
                .le(end != null, SysOperationLog::getOperationTime, end)
                .orderByDesc(SysOperationLog::getOperationTime)
                .orderByDesc(SysOperationLog::getId);
        Page<SysOperationLog> page = operationLogMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
