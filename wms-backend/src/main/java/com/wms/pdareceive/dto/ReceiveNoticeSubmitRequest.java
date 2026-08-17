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
    /** true=自动分配库位；false/空=不自动分配（可仅仓库入库） */
    private Boolean autoAllocateLocation;
    /** 手动指定库位；为空表示不指定库位 */
    private String locationCode;
    private String deviceNo;
    private String remark;
}
