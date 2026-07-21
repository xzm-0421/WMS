package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KingdeeSerialVo {
    private String serialNo;
    private String materialCode;
    private String batchNo;
    private String status;
}
