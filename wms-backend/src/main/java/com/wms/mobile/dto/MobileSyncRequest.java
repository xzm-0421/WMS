package com.wms.mobile.dto;

import lombok.Data;

import java.util.List;

@Data
public class MobileSyncRequest {

    private String deviceNo;
    private String lastSyncTime;
    private List<MobileSyncItem> offlineData;

    @Data
    public static class MobileSyncItem {
        private String operationType;
        private String orderNo;
        private Object payload;
        private String clientTime;
        private String clientId;
    }
}
