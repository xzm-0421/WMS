package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KingdeeMasterDataSyncResult {
    private int totalFetched;
    private int inserted;
    private int updated;
    private int skipped;
    private String message;
}
