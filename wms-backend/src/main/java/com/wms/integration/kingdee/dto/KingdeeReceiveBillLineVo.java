package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class KingdeeReceiveBillLineVo {
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    /** 物料描述（FMaterialDesc / MaterialDesc） */
    private String materialDesc;
    private String specification;
    private String batchNo;
    private String unitCode;
    /** 计价单位 FPriceUnitId */
    private String priceUnitCode;
    /** 计价数量 FPriceUnitQty */
    private BigDecimal priceUnitQty;
    /** 金蝶收料分录仓库 FStockId */
    private String stockWarehouseCode;
    /** 金蝶分录内码 FEntryID */
    private Long entryId;
    /** 实收数量 FActReceiveQty */
    private BigDecimal planQty;
    /** 合格数量(基本单位) FReceiveBaseQty */
    private BigDecimal qualifiedQty;
    /** 库存基本数量 FStockBaseQty */
    private BigDecimal stockBaseQty;
    /** 基本单位数量 FBaseUnitQty */
    private BigDecimal baseUnitQty;
    /** 合格入库关联数量(基本单位) FInStockJoinBaseQty */
    private BigDecimal inStockJoinBaseQty;
    /** 剩余可入库数量(基本单位)，用于 FInStockEntry_Link 携带量 */
    private BigDecimal remainInStockBaseQty;
    /** 送货单号 F_QVHU_Text_qtr */
    private String sendBillNo;
    /** 上游采购订单单号（收料分录 SrcBillNo） */
    private String poOrderNo;
    /** 采购订单分录内码 POORDERENTRYID */
    private Long poOrderEntryId;
    /** 生产订单编号 FMoBillNo */
    private String moBillNo;
    /** 生产订单内码 FMoId */
    private Long moId;
    /** 生产订单分录内码 FMoEntryId */
    private Long moEntryId;
    /** 生产订单行号 FMoEntrySeq */
    private Integer moEntrySeq;
    /** 车间编码（汇报分录 FWorkShopId1 / 头 FWorkShopId） */
    private String workShopCode;
    /** 用料清单编号（头单号） */
    private String ppBomBillNo;
    /** 用料清单分录内码 FPPBomEntryId / FEntryID */
    private Long ppBomEntryId;
    /** 产品编码 FParentMaterialId */
    private String parentMaterialCode;
    /** 委外订单编号 */
    private String subReqBillNo;
    /** 委外订单内码 */
    private Long subReqId;
    /** 委外订单分录内码 */
    private Long subReqEntryId;
    /** 委外订单行号 */
    private Integer subReqEntrySeq;
    private BigDecimal receivedQty;
}
