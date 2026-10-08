package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.alert.AlertChannel;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.system.entity.SysAlert;
import com.wms.system.mapper.SysAlertMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警记录与处理：同类型 + 同单号的未关闭告警累加计数，否则新建；并 fan-out 到告警通道。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ACK = "ACK";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String TYPE_QUEUE_FULL = "QUEUE_FULL";
    public static final String TYPE_SLA_TIMEOUT = "SLA_TIMEOUT";
    public static final String TYPE_LONG_UNSYNCED = "LONG_UNSYNCED";

    private final SysAlertMapper alertMapper;
    private final List<AlertChannel> channels;

    public SysAlert raise(String alertType, String level, String title, String content, String bizNo) {
        LocalDateTime now = LocalDateTime.now();
        List<SysAlert> existing = alertMapper.selectList(new LambdaQueryWrapper<SysAlert>()
                .eq(SysAlert::getAlertType, alertType)
                .eq(SysAlert::getBizNo, bizNo)
                .eq(SysAlert::getStatus, STATUS_OPEN)
                .orderByDesc(SysAlert::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        SysAlert alert;
        boolean created = existing.isEmpty();
        if (!created) {
            alert = existing.get(0);
            alert.setAlertCount((alert.getAlertCount() == null ? 0 : alert.getAlertCount()) + 1);
            alert.setLevel(level);
            alert.setTitle(title);
            alert.setContent(content);
            alert.setLastTime(now);
            alert.setUpdateTime(now);
            alertMapper.updateById(alert);
        } else {
            alert = new SysAlert();
            alert.setAlertType(alertType);
            alert.setLevel(level);
            alert.setTitle(title);
            alert.setContent(content);
            alert.setBizNo(bizNo);
            alert.setStatus(STATUS_OPEN);
            alert.setAlertCount(1);
            alert.setFirstTime(now);
            alert.setLastTime(now);
            alert.setCreateTime(now);
            alertMapper.insert(alert);
        }
        for (AlertChannel channel : channels) {
            try {
                channel.send(alert);
            } catch (Exception e) {
                log.warn("告警通道发送失败: {}", e.getMessage());
            }
        }
        return alert;
    }

    public PageResult<SysAlert> page(String alertType, String status, long current, long size) {
        LambdaQueryWrapper<SysAlert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(alertType), SysAlert::getAlertType, alertType)
                .eq(StringUtils.hasText(status), SysAlert::getStatus, status)
                .orderByDesc(SysAlert::getLastTime)
                .orderByDesc(SysAlert::getId);
        Page<SysAlert> page = alertMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public void ack(Long id, String operator) {
        SysAlert alert = require(id);
        if (STATUS_CLOSED.equals(alert.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "告警已关闭");
        }
        alert.setStatus(STATUS_ACK);
        alert.setAckBy(operator);
        alert.setAckTime(LocalDateTime.now());
        alert.setUpdateTime(LocalDateTime.now());
        alertMapper.updateById(alert);
    }

    public void close(Long id, String operator) {
        SysAlert alert = require(id);
        alert.setStatus(STATUS_CLOSED);
        if (alert.getAckBy() == null) {
            alert.setAckBy(operator);
            alert.setAckTime(LocalDateTime.now());
        }
        alert.setUpdateTime(LocalDateTime.now());
        alertMapper.updateById(alert);
    }

    private SysAlert require(Long id) {
        SysAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "告警不存在");
        }
        return alert;
    }
}
