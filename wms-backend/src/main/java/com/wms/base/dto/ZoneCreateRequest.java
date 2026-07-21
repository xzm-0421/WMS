package com.wms.base.dto;

import lombok.Data;

@Data
public class ZoneCreateRequest {
    private String warehouseCode;
    private String zoneCode;
    private String zoneName;
}
