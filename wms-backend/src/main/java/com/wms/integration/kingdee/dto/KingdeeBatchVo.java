package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KingdeeBatchVo {
    private String batchNo;
    private String materialCode;
    private String materialName;
    private String status;
}
