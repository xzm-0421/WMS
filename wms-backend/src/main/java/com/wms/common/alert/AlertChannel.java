package com.wms.common.alert;

import com.wms.system.entity.SysAlert;

/**
 * 告警推送通道；外部通道（钉钉/邮件/短信）后续实现，默认仅日志。
 */
public interface AlertChannel {

    void send(SysAlert alert);
}
