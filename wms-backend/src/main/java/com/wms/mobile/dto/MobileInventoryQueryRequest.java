package com.wms.mobile.dto;

import lombok.Data;

@Data
public class MobileInventoryQueryRequest {

    private String barcode;
    private String materialCode;
    private String locationCode;
    private String batchNo;
    private String warehouseCode;
    /** MATERIAL / LOCATION / BATCH */
    private String queryType = "MATERIAL";
}
