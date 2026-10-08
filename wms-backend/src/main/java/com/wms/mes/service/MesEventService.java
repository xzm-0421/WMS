package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.auth.security.LoginUser;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.mes.entity.MesEventLog;
import com.wms.mes.mapper.MesEventLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据埋点：写入 mes_event_log，失败不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesEventService {

    public static final String REPORT_SUBMIT = "mes_report_submit";
    public static final String TRANSFER_SUBMIT = "mes_transfer_submit";
    public static final String REWORK_CREATE = "mes_rework_create";
    public static final String SYNC_SUCCESS = "mes_sync_success";
    public static final String SYNC_FAIL = "mes_sync_fail";
    public static final String OFFLINE_ENTER = "mes_offline_enter";
    public static final String OFFLINE_EXIT = "mes_offline_exit";
    public static final String BASICDATA_SYNC = "mes_basicdata_sync";

    private final MesEventLogMapper eventLogMapper;
    private final ObjectMapper objectMapper;

    public void track(String eventName, String bizType, String bizNo, Map<String, Object> properties) {
        try {
            MesEventLog event = new MesEventLog();
            event.setEventName(eventName);
            event.setBizType(bizType);
            event.setBizNo(bizNo);
            event.setEventTime(LocalDateTime.now());
            event.setCreateTime(LocalDateTime.now());
            LoginUser user = currentUserQuietly();
            if (user != null) {
                event.setOperatorId(String.valueOf(user.getUserId()));
                event.setOperatorName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
            }
            if (properties != null && !properties.isEmpty()) {
                event.setProperties(objectMapper.writeValueAsString(properties));
            }
            eventLogMapper.insert(event);
        } catch (Exception e) {
            log.warn("写入埋点事件失败 event={}: {}", eventName, e.getMessage());
        }
    }

    public void track(String eventName, String bizType, String bizNo) {
        track(eventName, bizType, bizNo, null);
    }

    public PageResult<MesEventLog> page(String eventName, String bizNo, long current, long size) {
        LambdaQueryWrapper<MesEventLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(eventName), MesEventLog::getEventName, eventName)
                .like(StringUtils.hasText(bizNo), MesEventLog::getBizNo, bizNo)
                .orderByDesc(MesEventLog::getEventTime)
                .orderByDesc(MesEventLog::getId);
        Page<MesEventLog> page = eventLogMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private static LoginUser currentUserQuietly() {
        try {
            return SecurityUtils.currentUser();
        } catch (Exception e) {
            return null;
        }
    }
}
