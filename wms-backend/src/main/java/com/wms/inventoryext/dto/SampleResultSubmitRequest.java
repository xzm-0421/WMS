package com.wms.inventoryext.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SampleResultSubmitRequest {
    private String planNo;
    private String materialCode;
    private String batchNo;
    private String locationCode;
    private String qualityStatus;
    private LocalDate expireDate;
    private String remark;
}
