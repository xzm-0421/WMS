package com.wms.common.alert;

import com.wms.system.entity.SysAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 默认告警通道：写入应用日志。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class LogAlertChannel implements AlertChannel {

    @Override
    public void send(SysAlert alert) {
        log.warn("告警[{}][{}] {} - {}", alert.getLevel(), alert.getAlertType(),
                alert.getTitle(), alert.getContent());
    }
}
