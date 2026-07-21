package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KingdeeStockInRequest {

    private String recordNo;
    private String materialCode;
    private String materialName;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private java.math.BigDecimal quantity;
    private String unitCode;
    /** 金蝶供应商编码 */
    private String supplierCode;
    /** 金蝶仓库 FStockId（优先于 warehouseCode） */
    private String erpWarehouseCode;
    /** 源单类型，如 PUR_ReceiveBill */
    private String sourceBillType;
    /** 源单编号，如收料通知单号 */
    private String sourceBillNo;
    private Integer sourceLineNo;
    private Long sourceBillId;
    private Long sourceEntryId;
}
