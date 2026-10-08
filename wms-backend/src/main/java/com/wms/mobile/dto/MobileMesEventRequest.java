package com.wms.mobile.dto;

import lombok.Data;

import java.util.Map;

/**
 * 移动端埋点上报（离线进入/退出等）。
 */
@Data
public class MobileMesEventRequest {

    private String eventName;
    private String bizNo;
    private Map<String, Object> properties;
}
