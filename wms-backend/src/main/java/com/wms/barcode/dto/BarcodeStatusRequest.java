package com.wms.barcode.dto;

import lombok.Data;

@Data
public class BarcodeStatusRequest {
    private Integer status;
    private String changeLog;
}
