package com.wms.mobile.dto;

import lombok.Data;

@Data
public class MobileLocationAllocateRequest {

    private String warehouseCode;
    private String materialCode;
    private String batchNo;
    /** true=系统自动推荐库位 */
    private Boolean autoAllocate = true;
    /** 手动指定库位（autoAllocate=false 时生效） */
    private String manualLocationCode;
}
