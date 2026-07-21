package com.wms.pdareceive.dto;

import lombok.Data;

@Data
public class ReceiveNoticeSubmitRequest {
    /** 入库手动选仓时的 WMS 仓库编码 */
    private String warehouseCode;
    /** 金蝶供应商编码（PDA 从收料单详情带入） */
    private String supplierCode;
    private String supplierName;
    /** 金蝶仓库 FStockId；手动选仓或分录兜底 */
    private String erpWarehouseCode;
    /** true=按分录物料仓库自动分配（默认）；false=使用 warehouseCode/erpWarehouseCode */
    private Boolean autoAssignWarehouse;
    /** 已废弃：入库不再分配库位 */
    private Boolean autoAllocateLocation;
    /** 已废弃：入库不再分配库位 */
    private String locationCode;
    private String deviceNo;
    private String remark;
}
