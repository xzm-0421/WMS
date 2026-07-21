package com.wms.mobile.dto;

import lombok.Data;

@Data
public class MobileTraceRequest {

    private String barcode;
    private String batchNo;
    private String materialCode;
}
