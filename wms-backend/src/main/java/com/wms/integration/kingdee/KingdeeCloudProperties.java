package com.wms.integration.kingdee;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "kingdee.cloud")
public class KingdeeCloudProperties {

    /** 是否启用金蝶云星空企业版对接；false 时不调金蝶 */
    private boolean enabled = false;

    /**
     * 金蝶未启用时是否向 PDA 返回内存模拟单（SLD/SCL 等）。
     * 生产环境必须为 false，否则列表会一直出现模拟数据。
     */
    private boolean mockEnabled = false;

    /** 云星空 WebAPI 站点根地址，如 https://xxx.ik3cloud.com/K3Cloud */
    private String baseUrl = "http://localhost/K3Cloud";

    private String acctId = "demo";

    private String username = "admin";

    private String password = "";

    /** 金蝶用户 FormId（解析 FUserID） */
    private String secUserFormId = "SEC_User";

    /**
     * 金蝶用户查询 FieldKeys：0=FUserID，1=用户名称 FName，2=电话 FPhone。
     */
    private String secUserFieldKeys = "FUserID,FName,FPhone";

    /** 入库单 FormId，默认采购入库单 */
    private String stockInFormId = "STK_InStock";

    /** 采购入库单 Save 成功后是否自动调用 Audit 审核 */
    private boolean stockInAutoAudit = true;

    /** 生产入库单 FormId（Save 目标单据） */
    private String prdInStockFormId = "PRD_INSTOCK";
    private boolean prdInStockAutoAudit = true;
    /**
     * 生产入库单据类型。
     * 汇报下推常用普通生产入库 SCRKD01_SYS；简单生产入库为 SCRKD02_SYS。
     */
    private String prdInStockBillTypeNumber = "SCRKD01_SYS";
    /**
     * 生产入库明细车间兜底编码。汇报下推时应取生产订单行车间，勿填与 MO 不一致的固定值。
     */
    private String prdInStockWorkShopNumber = "";
    /** 生产订单 FormId，用于读取分录生产车间 */
    private String prdMoFormId = "PRD_MO";
    /** 入库类型：1=合格品入库 */
    private String prdInStockInStockType = "1";

    /** 生产汇报 → 生产入库 转换规则 */
    private String prdInStockMorptLinkRuleId = "PRD_MORPT2INSTOCK";
    /** 生产汇报分录表 */
    private String prdInStockMorptLinkSTableName = "T_PRD_MORPTENTRY";
    private String prdInStockMorptLinkFlowId = "";
    private int prdInStockMorptLinkFlowLineId = 0;

    /**
     * PDA 扫码源单：生产汇报单 FormId。
     * 列表/明细查询用此 FormId；Save 仍写入 {@link #prdInStockFormId}。
     */
    private String prdMorptFormId = "PRD_MORPT";

    /** 生产汇报单列表 FieldKeys（车间槽位） */
    private String prdMorptLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 生产汇报单明细 FieldKeys。
     * 合格 FQuaQty、合格品入库选单 FStockInSelQty；计划=合格，已处理=选单，可入=合格−选单。
     */
    private String prdMorptDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FQuaQty,FEntity_FStockInSelQty,FEntity_FBaseQuaQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq";

    /** 兼容旧配置名：生产入库单列表 FieldKeys（仅审计旧未审入库单时使用） */
    private String prdInStockLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 兼容旧配置：生产入库单明细 FieldKeys。
     */
    private String prdInStockDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FRealQty,FEntity_FMustQty,FEntity_FBaseRealQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq";

    /** 生产退库单 FormId */
    private String prdRetStockFormId = "PRD_RetStock";

    /** 生产退库单据类型，标准 SCTK01_SYS */
    private String prdRetStockBillTypeNumber = "SCTK01_SYS";

    /** 生产退库单列表 FieldKeys（布局与生产入库接近） */
    private String prdRetStockLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 生产退库单明细 FieldKeys。
     * 数量：实退 FRealQty、应退 FMustQty（未审核单计划取应退）。
     */
    private String prdRetStockDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FRealQty,FEntity_FMustQty,FEntity_FBaseRealQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq";

    /** 其他入库单 FormId */
    private String miscInStockFormId = "STK_Miscellaneous";
    private boolean miscInStockAutoAudit = true;
    private String miscInStockBillTypeNumber = "QTRKD01_SYS";
    private String miscInStockDeptNumber = "";
    private boolean miscInStockSendStockLoc = false;

    /** 其他出库单 FormId */
    private String misDeliveryFormId = "STK_MisDelivery";
    private boolean misDeliveryAutoAudit = true;
    private String misDeliveryBillTypeNumber = "QTCKD01_SYS";
    private String misDeliveryDeptNumber = "";
    private String misDeliveryBizType = "0";
    private boolean misDeliverySendStockLoc = false;

    /** 生产补料单 FormId */
    private String feedMtrlFormId = "PRD_FeedMtrl";

    /** 生产补料单列表 FieldKeys（布局对齐领料单） */
    private String feedMtrlLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 生产补料单明细 FieldKeys。
     * 数量：实发 FActualQty、申请 FAppQty / FBaseAppQty（未审核单计划取申请数量）。
     */
    private String feedMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FActualQty,FEntity_FAppQty,FEntity_FBaseAppQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FPPBomEntryId,FEntity_FPPBomBillNo";

    /** 生产领料单 FormId */
    private String pickMtrlFormId = "PRD_PickMtrl";

    /** 生产领料单 Save 成功后是否自动 Submit + Audit */
    private boolean pickMtrlAutoAudit = true;

    /** 生产领料单据类型，标准普通领料 SCLLD01_SYS */
    private String pickMtrlBillTypeNumber = "SCLLD01_SYS";

    /** 默认车间（部门），为空时用提交批次上的车间编码 */
    private String pickMtrlWorkShopNumber = "";

    /** 是否传库位到金蝶 */
    private boolean pickMtrlSendStockLoc = false;

    /**
     * 领料分录库存更新标志：0=未更新，1=已更新。
     * WMS 已扣本地库存；一般交由金蝶审核更新账存时用 0。
     */
    private String pickMtrlStockFlag = "0";

    /** 用料清单 -> 生产领料 转换规则 */
    private String pickMtrlPpBomLinkRuleId = "PRD_PPBOM2PICKMTRL_NORMAL";

    /** 用料清单分录表名 */
    private String pickMtrlPpBomLinkSTableName = "T_PRD_PPBOMENTRY";

    /** 生产领退补流程 ID（FEntity_Link_FFlowId，可按环境覆盖） */
    private String pickMtrlPpBomLinkFlowId = "81119477-4778-4d0b-94b9-1c43a1c1f768";

    /** 用料清单到领料推进路线，默认 5 */
    private int pickMtrlPpBomLinkFlowLineId = 5;

    /** 生产退料单 FormId */
    private String returnMtrlFormId = "PRD_ReturnMtrl";

    /** 生产退料单 Save 成功后是否自动 Submit + Audit */
    private boolean returnMtrlAutoAudit = true;

    /** 生产退料单据类型，标准普通退料 SCTLD01_SYS */
    private String returnMtrlBillTypeNumber = "SCTLD01_SYS";

    /** 默认车间 */
    private String returnMtrlWorkShopNumber = "";

    /** 退料类型：1=良品退料（按环境覆盖） */
    private String returnMtrlReturnType = "1";

    /** 退料原因编码（可选） */
    private String returnMtrlReturnReasonNumber = "";

    private boolean returnMtrlSendStockLoc = false;

    /** 退料分录库存更新标志 */
    private String returnMtrlStockFlag = "0";

    /** 生产领料 -> 生产退料 转换规则 */
    private String returnMtrlPickLinkRuleId = "PRD_PickMtrl-PRD_ReturnMtrl";

    private String returnMtrlPickLinkSTableName = "T_PRD_PICKMTRLDATA";

    private String returnMtrlPickLinkFlowId = "81119477-4778-4d0b-94b9-1c43a1c1f768";

    private int returnMtrlPickLinkFlowLineId = 5;

    /**
     * 生产用料清单列表 FieldKeys（按分录计行数）
     * 车间映射到列表的 supplier 槽位展示
     */
    private String ppBomLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 生产用料清单明细 FieldKeys
     */
    private String ppBomDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FMOBillNO,"
                    + "FMaterialId.FNumber,FMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FMustQty,FEntity_FPickedQty,FEntity_FNoPickedQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber";

    /** 生产领料单列表 FieldKeys */
    private String pickMtrlLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID,"
                    + "FCreatorId.FNumber,FCreatorId.FName";

    /** 生产领料单明细 FieldKeys */
    private String pickMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FActualQty,FEntity_FAppQty,FEntity_FBaseActualQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FPPBomEntryId,FEntity_FPPBomBillNo,"
                    + "FCreatorId.FNumber,FCreatorId.FName";

    /** 生产退料单列表 FieldKeys（字段布局与领料单一致，便于共用解析） */
    private String returnMtrlLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 生产退料单明细 FieldKeys。
     * 数量字段与领料不同：申请=FAPPQty，实退=FQty（非 FActualQty/FAppQty）。
     */
    private String returnMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FQty,FEntity_FAPPQty,FEntity_FBaseQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FMoBillNo,FEntity_FMoId,FEntity_FMoEntryId,FEntity_FMoEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FPPBomEntryId,FEntity_FPPBomBillNo";

    /** 委外领料单 FormId */
    private String subPickMtrlFormId = "SUB_PickMtrl";

    private boolean subPickMtrlAutoAudit = true;

    /** 委外领料单据类型，标准 WWLLD01_SYS */
    private String subPickMtrlBillTypeNumber = "WWLLD01_SYS";

    /** 默认供应商（为空时用用料清单上的供应商） */
    private String subPickMtrlDefaultSupplierNumber = "";

    private boolean subPickMtrlSendStockLoc = false;

    private String subPickMtrlStockFlag = "0";

    /** 委外用料清单 -> 委外领料 转换规则 */
    private String subPickMtrlPpBomLinkRuleId = "SUB_PPBOM2PICKMTRL_NORMAL";

    private String subPickMtrlPpBomLinkSTableName = "T_SUB_PPBOMENTRY";

    private String subPickMtrlPpBomLinkFlowId = "81119477-4778-4d0b-94b9-1c43a1c1f768";

    private int subPickMtrlPpBomLinkFlowLineId = 5;

    /** 委外退料单 FormId */
    private String subReturnMtrlFormId = "SUB_RETURNMTRL";

    private boolean subReturnMtrlAutoAudit = true;

    /** 委外退料单据类型，标准 WWTLD01_SYS */
    private String subReturnMtrlBillTypeNumber = "WWTLD01_SYS";

    /** 默认供应商（为空时用领料单上的供应商） */
    private String subReturnMtrlDefaultSupplierNumber = "";

    /** 退料类型：1=良品退料 */
    private String subReturnMtrlReturnType = "1";

    /** 退料原因2（必填，默认与退料类型一致） */
    private String subReturnMtrlReturnType2 = "1";

    private String subReturnMtrlReturnReasonNumber = "";

    private boolean subReturnMtrlSendStockLoc = false;

    private String subReturnMtrlStockFlag = "0";

    /** 委外领料 -> 委外退料 转换规则 */
    private String subReturnMtrlPickLinkRuleId = "SUB_PickMtrl-SUB_RETURNMTRL";

    private String subReturnMtrlPickLinkSTableName = "T_SUB_PICKMTRLDATA";

    private String subReturnMtrlPickLinkFlowId = "81119477-4778-4d0b-94b9-1c43a1c1f768";

    private int subReturnMtrlPickLinkFlowLineId = 5;

    /** 委外用料清单列表 FieldKeys */
    private String subPpBomLineCountFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /** 委外用料清单明细 FieldKeys */
    private String subPpBomDetailFieldKeys =
            "FBillNo,FID,FDate,FSupplierId.FNumber,FSupplierId.FName,FSubReqBillNo,"
                    + "FMaterialId.FNumber,FMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FMustQty,FEntity_FPickedQty,FEntity_FNoPickedQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FSubReqBillNo,FEntity_FSubReqId,FEntity_FSubReqEntryId,FEntity_FSubReqEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber";

    /** 委外领料单列表 FieldKeys（未审核单据列表） */
    private String subPickMtrlLineCountFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /** 委外领料单明细 FieldKeys */
    private String subPickMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FSupplierId.FNumber,FSupplierId.FName,FEntity_FSubReqBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FActualQty,FEntity_FAppQty,FEntity_FBaseActualQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FSubReqBillNo,FEntity_FSubReqId,FEntity_FSubReqEntryId,FEntity_FSubReqEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FPPbomEntryId,FEntity_FPPbomBillNo";

    /** 委外补料单 FormId（金蝶官方标识 SUB_FEEDMTRL） */
    private String subFeedMtrlFormId = "SUB_FEEDMTRL";

    /** 委外补料单列表 FieldKeys（委外供应商槽位） */
    private String subFeedMtrlLineCountFieldKeys =
            "FBillNo,FSubSupplierId.FNumber,FSubSupplierId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 委外补料单明细 FieldKeys。
     * 数量：实发 FActualQty、申请 FAppQty / FBaseAppQty；布局对齐委外领料。
     */
    private String subFeedMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FSubSupplierId.FNumber,FSubSupplierId.FName,FEntity_FSubReqBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FActualQty,FEntity_FAppQty,FEntity_FBaseAppQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FSubReqBillNo,FEntity_FSubReqId,FEntity_FSubReqEntryId,FEntity_FSubReqEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FPPbomEntryId,FEntity_FPPbomBillNo";

    /** 委外退料单列表 FieldKeys */
    private String subReturnMtrlLineCountFieldKeys =
            "FBillNo,FSubSupplierId.FNumber,FSubSupplierId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 委外退料单明细 FieldKeys。
     * 数量：申请=FAPPQty，实退=FQty（与生产退料一致）。
     */
    private String subReturnMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FSubSupplierId.FNumber,FSubSupplierId.FName,FEntity_FSubReqBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FQty,FEntity_FAPPQty,FEntity_FBaseQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FEntity_FLot.FNumber,"
                    + "FEntity_FUnitID.FNumber,FEntity_FStockId.FNumber,"
                    + "FEntity_FSubReqBillNo,FEntity_FSubReqId,FEntity_FSubReqEntryId,FEntity_FSubReqEntrySeq,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FPPbomEntryId,FEntity_FPPbomBillNo";

    /** 物料清单 FormId，默认工程 BOM */
    private String bomFormId = "ENG_BOM";

    /**
     * BOM 列表查询字段（ExecuteBillQuery FieldKeys，逗号分隔）
     */
    private String bomListFieldKeys =
            "FNumber,FMaterialId.FNumber,FMaterialId.FName,FBOMVERSION,FDocumentStatus,FForbidStatus";

    /**
     * BOM 明细查询字段（含 FTreeEntity 子件行）
     */
    private String bomDetailFieldKeys =
            "FNumber,FMaterialId.FNumber,FMaterialId.FName,FBOMVERSION,"
                    + "FTreeEntity_FSEQ,FTreeEntity_FMaterialIdChild.FNumber,FTreeEntity_FMaterialIdChild.FName,"
                    + "FTreeEntity_FNumerator,FTreeEntity_FDenominator,FTreeEntity_FUnitID.FNumber";

    /** 单次 BOM 明细查询上限 */
    private int bomQueryLimit = 2000;

    /** 物料主数据 FormId */
    private String materialFormId = "BD_MATERIAL";

    private String materialFieldKeys = "FNumber,FName,FBarCode,FIsBatchManage,FIsSNManage";

    /** 物料主数据同步 FieldKeys（含规格、单位、状态） */
    private String materialSyncFieldKeys =
            "FNumber,FName,FSpecification,FBaseUnitId.FNumber,FIsBatchManage,FIsSNManage,FForbidStatus,FDocumentStatus";

    /** 仓库主数据 FormId */
    private String warehouseFormId = "BD_STOCK";

    /** 仓库主数据同步 FieldKeys */
    private String warehouseFieldKeys = "FNumber,FName,FStockProperty,FForbidStatus,FDocumentStatus";

    /** 主数据单次拉取条数 */
    private int masterDataQueryLimit = 500;

    /** 仅同步已审核且未禁用的主数据 */
    private boolean masterDataApprovedOnly = true;

    /** 批号主档 FormId */
    private String batchFormId = "BD_BatchMainFile";

    private String batchFieldKeys = "FNumber,FMaterialId.FNumber,FMaterialId.FName";

    /** 序列号主档 FormId */
    private String serialFormId = "BD_SerialMainFile";

    private String serialFieldKeys = "FNumber,FMaterialId.FNumber,FLot.FNumber";

    private int barcodeQueryLimit = 500;

    /** 物料盘点作业 FormId（云星空企业版-库存管理） */
    private String stockCountFormId = "STK_StockCountInput";

    /**
     * 盘点作业列表 FieldKeys（仅头字段，用于未审核单据列表）
     */
    private String stockCountListFieldKeys =
            "FBillNo,FDocumentStatus,FDate,FStockOrgId.FNumber,FNoteHead";

    /**
     * 盘点作业明细 FieldKeys（分录：物料/规格/账存数量/盘点数量等）
     */
    private String stockCountDetailFieldKeys =
            "FBillNo,FID,FDocumentStatus,FDate,FStockId.FNumber,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FMaterialId.FSpecification,"
                    + "FAcctQty,FCountQty,FLot.FNumber,FUnitID.FNumber,"
                    + "FBillEntry_FEntryID,FBillEntry_FSeq";

    /** 盘点作业列表/明细单次拉取上限 */
    private int stockCountQueryLimit = 2000;

    /** 盘点列表仅显示近 N 天（按 FDate），0 表示不限制 */
    private int stockCountListDays = 0;

    /** 收料通知单 FormId（云星空企业版-采购管理） */
    private String receiveBillFormId = "PUR_ReceiveBill";

    /**
     * 收料通知单列表 FieldKeys（与金蝶 ExecuteBillQuery 已验证参数一致）
     * 返回：单据编号、供应商名称、单据状态（C=已审核）
     */
    private String receiveBillListFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate";

    /** 收料通知单列表单次拉取上限（与金蝶 Limit 一致） */
    private int receiveBillListQueryLimit = 2000;

    /** 收料通知单列表仅显示近 N 天（按 FDate），0 表示不限制 */
    private int receiveBillListDays = 0;

    /**
     * 来料检验列表 FieldKeys（分录字段在本环境为扁平名，勿用 FDetailEntity. 前缀）
     * 末尾含已入库/剩余可入库，用于列表剔除可处理=0 的单据。
     */
    private String receiveBillInspectionListFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,"
                    + "FCheckInComing,FActReceiveQty,FCheckBaseQty,"
                    + "FRefuseBaseQty,FReceiveBaseQty,FSampleDamageBaseQty,"
                    + "FCsnReceiveBaseQty,FProcScrapBaseQty,FMtrlScrapBaseQty,"
                    + "FInStockJoinBaseQty,FRemainInStockBaseQty";

    /**
     * 检验列表降级 FieldKeys（不含已入库/剩余字段，账套缺字段时回退用，仍走检验+本地余量推算）。
     */
    private String receiveBillInspectionListFieldKeysNoRemain =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,"
                    + "FCheckInComing,FActReceiveQty,FCheckBaseQty,"
                    + "FRefuseBaseQty,FReceiveBaseQty,FSampleDamageBaseQty,"
                    + "FCsnReceiveBaseQty,FProcScrapBaseQty,FMtrlScrapBaseQty";

    /**
     * 按分录统计物料行数（本环境分录内码为 FDetailEntity_FEntryID）
     */
    private String receiveBillLineCountFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,FDetailEntity_FEntryID";

    /**
     * 收料通知单明细 FieldKeys（ExecuteBillQuery 扁平分录字段，供入库关联）
     */
    private String receiveBillDetailFieldKeys =
            "FBillNo,FID,FDate,FSupplierId.FNumber,FSupplierId.FName,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FActReceiveQty,"
                    + "FReceiveBaseQty,FStockBaseQty,FInStockJoinBaseQty,FBaseUnitQty,"
                    + "FDetailEntity_FSeq,FDetailEntity_FEntryID,FLot.FNumber,FBaseUnitId.FNumber,FStockId.FNumber,"
                    + "FUnitID.FNumber,FPriceUnitId.FNumber,FPriceUnitQty,"
                    // 收料通知单送货单号（与采购入库同字段 F_QVHU_Text_qtr）
                    + "F_QVHU_Text_qtr";

    /** 其他入库/出库列表 FieldKeys（部门占供应商位，便于复用列表解析） */
    private String miscBillLineCountFieldKeys =
            "FBillNo,FDeptId.FNumber,FDeptId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /** 其他入库/出库明细 FieldKeys（数量优先取申请/应收） */
    private String miscBillDetailFieldKeys =
            "FBillNo,FID,FDate,FDeptId.FNumber,FDeptId.FName,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FQty,"
                    + "FQty,FQty,FQty,FQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FLot.FNumber,FUnitID.FNumber,FStockId.FNumber";

    /** 销售发货通知列表 FieldKeys（含未出库数量，便于列表剔除已出完） */
    private String salesDeliveryLineCountFieldKeys =
            "FBillNo,FCustomerID.FNumber,FCustomerID.FName,FDocumentStatus,FDate,FEntity_FEntryID,FRemainOutQty";

    /** 销售发货通知明细 FieldKeys */
    private String salesDeliveryDetailFieldKeys =
            "FBillNo,FID,FDate,FCustomerID.FNumber,FCustomerID.FName,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FQty,"
                    + "FQty,FQty,FQty,FQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FLot.FNumber,FUnitID.FNumber,FStockId.FNumber";

    /** 销售出库单 FormId */
    private String salOutStockFormId = "SAL_OUTSTOCK";

    /**
     * 发货通知 → 销售出库 转换规则（BOS 单据转换 RuleId）。
     * 标准规则一般为 DeliveryNotice-OutStock。
     */
    private String salesDeliveryPushRuleId = "DeliveryNotice-OutStock";

    /**
     * PDA 确认发货通知时：true=下推生成销售出库并审核出库单；false=仅提交审核发货通知本身。
     */
    private boolean salesDeliveryPushToOutStock = true;

    /**
     * 销售出库缺省仓库（发货通知分录 FStockID 为空且 PDA 未指定时回退）。
     */
    private String salesOutStockDefaultWarehouseNumber = "CK004";

    /**
     * 销售退货通知单 FormId（PDA 扫已审核通知 → 新建销售退货单）。
     */
    private String salReturnNoticeFormId = "SAL_RETURNNOTICE";

    /** 销售退货通知列表 FieldKeys（客户 FRetcustId） */
    private String salReturnNoticeLineCountFieldKeys =
            "FBillNo,FRetcustId.FNumber,FRetcustId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /**
     * 销售退货通知明细 FieldKeys。
     * 布局对齐 {@link KingdeeReceiveBillDetailRowParser}：客户槽位 + 物料 + FQty + 批号/单位/仓库。
     */
    /**
     * 复用收料单明细解析器的列布局，退货通知无「已入库关联量」，
     * 该列（第 11 列）须用非数值字段占位，否则会被算成 remain = FQty - FQty = 0，
     * 提交时被「超过收料单剩余可入库 0」误拦。
     */
    private String salReturnNoticeDetailFieldKeys =
            "FBillNo,FID,FDate,FRetcustId.FNumber,FRetcustId.FName,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FQty,"
                    + "FQty,FQty,FLot.FNumber,FQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FLot.FNumber,FUnitID.FNumber,FStockId.FNumber";

    /** 销售退货单 FormId（SAL_RETURNSTOCK） */
    private String salReturnStockFormId = "SAL_RETURNSTOCK";

    private boolean salReturnStockAutoAudit = true;

    /** 销售退货单据类型，标准 THD01_SYS */
    private String salReturnStockBillTypeNumber = "THD01_SYS";

    /** 退货类型（FReturnType），按账套核对 */
    private String salReturnStockReturnTypeNumber = "THLX01_SYS";

    /** 缺省退货客户（通知单客户为空时回退，一般勿依赖） */
    private String salReturnStockDefaultCustomerNumber = "";

    private boolean salReturnStockSendStockLoc = false;

    /** 退货通知 → 销售退货 转换规则 */
    private String salReturnStockLinkRuleId = "ReturnNotice-ReturnStock";

    private String salReturnStockLinkSTableName = "T_SAL_RETURNNOTICEENTRY";

    private String salReturnStockLinkFlowId = " ";

    private int salReturnStockLinkFlowLineId = 0;

    /** 列表已改为退货通知；保留字段以免旧 yml 报错 */
    @Deprecated
    private String salReturnStockLineCountFieldKeys =
            "FBillNo,FCustomerID.FNumber,FCustomerID.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /** 见 salReturnNoticeDetailFieldKeys */
    @Deprecated
    private String salReturnStockDetailFieldKeys =
            "FBillNo,FID,FDate,FCustomerID.FNumber,FCustomerID.FName,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FQty,"
                    + "FQty,FQty,FLot.FNumber,FQty,"
                    + "FEntity_FSeq,FEntity_FEntryID,FLot.FNumber,FUnitID.FNumber,FStockId.FNumber";

    /** 采购退料单 FormId */
    private String purMrbFormId = "PUR_MRB";

    /** 采购退料单列表 FieldKeys（分录实体为 FPURMRBENTRY，非 FEntity） */
    private String purMrbLineCountFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,FPURMRBENTRY_FEntryID";

    /**
     * 采购退料单明细 FieldKeys。
     * 布局对齐 {@link KingdeeReceiveBillDetailRowParser}：
     * 实退 FRMREALQTY 作库存计划量；计价数量必须用 FPriceUnitQty（勿填实退，否则 PCS↔KG 换算率变 1）。
     * 第 11 列（原 FInStockJoin）用 FNOTE 占位，避免实退被当成已处理。
     */
    private String purMrbDetailFieldKeys =
            "FBillNo,FID,FDate,FSupplierId.FNumber,FSupplierId.FName,"
                    + "FMaterialId.FNumber,FMaterialId.FName,FRMREALQTY,"
                    + "FRMREALQTY,FRMREALQTY,FNOTE,FRMREALQTY,"
                    + "FPURMRBENTRY_FSeq,FPURMRBENTRY_FEntryID,FLot.FNumber,FUnitID.FNumber,FStockId.FNumber,"
                    + "FUnitID.FNumber,FPriceUnitId.FNumber,FPriceUnitQty";

    private int receiveBillQueryLimit = 2000;

    /** 采购入库单（STK_InStock）保存默认字段 */
    private String stockInBillTypeNumber = "RKD01_SYS";
    private String stockInBusinessType = "CG";
    /** 委外收料下推采购入库单时的单据类型（委外入库），空则沿用 stockInBillTypeNumber */
    private String stockInWwBillTypeNumber = "RKD02_SYS";
    /** 委外业务类型编码 */
    private String stockInWwBusinessType = "WW";
    private String stockInOrgNumber = "100";
    private String stockInStockDeptNumber = "BM000026";
    private String stockInStockerNumber = "11567";
    private String stockInPurchaseDeptNumber = "BM000003";
    private String stockInPurchaserNumber = "12638";
    private String stockInDefaultSupplierNumber = "";
    private String stockInStockStatusNumber = "KCZT01_SYS";
    private String stockInSettleCurrNumber = "PRE001";
    private String stockInExchangeTypeNumber = "HLTX01_SYS";
    private String stockInOwnerTypeHead = "BD_OwnerOrg";
    private String stockInSplitBillType = "A";
    private String stockInWwInType = "QLI";
    private String stockInDefaultUnitNumber = "Pcs";
    private String stockInDefaultWarehouseNumber = "CK004";
    /**
     * 金蝶占位仓（名称多为「未分配」）。自动分配仓库时跳过，改取物料默认仓或生产订单仓。
     */
    private String stockInUnassignedWarehouseNumber = "CK004";
    private double stockInEntryTaxRate = 13.0;
    private boolean stockInGiveAway = true;
    /** 同步时是否传 WMS 库位到金蝶 FStockLocId（收料通知单建议 false） */
    private boolean stockInSendStockLoc = false;
    /** 有收料通知单源单时是否标记来料检验 */
    private boolean stockInCheckInComing = true;

    /** 下推失败时是否暂存为草稿（IsDraftWhenSaveFail，暂存单无编码） */
    private boolean stockInPushDraftWhenSaveFail = true;
    /** 下推目标单据类型内码 TargetBillTypeId（非必录，留空则不传有效值） */
    private String stockInTargetBillTypeId = "";
    /** 下推目标组织内码 TargetOrgId（非必录，0 表示不指定） */
    private int stockInTargetOrgId = 0;

    /** 收料通知单 -> 采购入库单 关联规则 */
    private String stockInReceiveLinkRuleId = "PUR_ReceiveBill-STK_InStock";
    private String stockInReceiveLinkSTableName = "T_PUR_ReceiveEntry";
    /** 源单表内码 T_PUR_ReceiveEntry（金蝶 BOS 实体表 ID，可按环境在 yml 覆盖） */
    private int stockInReceiveLinkSTableId = 73;

    /** WMS 仓库编码 -> 金蝶 FStockId，如 WH01:CK004 */
    private Map<String, String> warehouseMappings = new HashMap<>();

    /** 金蝶调用 WMS 标签打印接口的 API Key（为空则不校验） */
    private String printApiKey = "";

    /**
     * WMS 对外访问根地址（金蝶浏览器可打开），如 http://192.168.1.10:9980
     * 用于拼装绝对 printUrl；为空则仅返回相对路径。
     */
    private String printPublicBaseUrl = "";

    private int connectTimeoutMs = 10000;

    private int readTimeoutMs = 30000;

    private int maxRetry = 3;

    /**
     * 轻 MES：工序 FormId。OpenAPI 工程数据以工艺路线分录带出工序；
     * 云星空另有工序基础资料 ENG_Process，可按现场覆盖。
     */
    private String mesProcessFormId = "ENG_Process";
    private String mesProcessFieldKeys =
            "FNumber,FName,FDeptId.FNumber,FDeptId.FName,FForbidStatus,FDocumentStatus";

    /** 轻 MES：设备 FormId（设备组/设备基础资料，按现场覆盖） */
    private String mesEquipmentFormId = "ENG_Equipment";
    private String mesEquipmentFieldKeys =
            "FNumber,FName,FProcessId.FNumber,FModel,FSpecification,FForbidStatus,FDocumentStatus";

    /**
     * 工艺路线 FormId。OpenAPI：ENG_ROUTE。
     * 云星空部分环境为 ENG_Route，拉取时会自动回退。
     */
    private String mesRouteFormId = "ENG_ROUTE";
    /**
     * 工艺路线 ExecuteBillQuery FieldKeys：路线编码/名称、物料、车间、工序明细（工序号、工作中心、准备/加工/传送工时）。
     */
    private String mesRouteFieldKeys =
            "FNumber,FName,FMaterialID.FNumber,FMaterialID.FName,FVersion,FBOMID,"
                    + "FWorkShopId.FNumber,FWorkShopId.FName,FForbidStatus,FDocumentStatus,"
                    + "FEntity_FSeq,FEntity_FProcessId.FNumber,FEntity_FProcessId.FName,"
                    + "FEntity_FWorkCenterId.FNumber,FEntity_FWorkCenterId.FName,"
                    + "FEntity_FPrepareTime,FEntity_FProcessTime,FEntity_FTransferTime,FEntity_FStdHour";
    /** 工艺路线备用 FieldKeys（FormId 回退到 ENG_Route 时使用） */
    private String mesRouteAltFieldKeys =
            "FNumber,FName,FMaterialId.FNumber,FMaterialId.FName,FVersion,FForbidStatus,FDocumentStatus,"
                    + "FEntity_FSeq,FEntity_FProcessId.FNumber,FEntity_FProcessId.FName,FEntity_FStdHour";

    /**
     * 工序计划单 FormId。OpenAPI：PRD_PROCESSCHEDULE。
     * 云星空车间常用 SFC_OperationPlanning，拉取失败时自动回退。
     */
    private String mesOpPlanFormId = "PRD_PROCESSCHEDULE";
    private String mesOpPlanFieldKeys =
            "FBillNo,FID,FMOBillNO,FMaterialID.FNumber,FMaterialID.FName,"
                    + "FWorkShopId.FNumber,FWorkShopId.FName,FQty,FDocumentStatus,"
                    + "FEntity_FSeq,FEntity_FProcessId.FNumber,FEntity_FProcessId.FName,"
                    + "FEntity_FWorkCenterId.FNumber,FEntity_FPlanStartDate,FEntity_FPlanFinishDate,"
                    + "FEntity_FPlanQty,FEntity_FReportQty,FEntity_FEntryID";
    /** 工序计划备用（SFC_OperationPlanning） */
    private String mesOpPlanAltFormId = "SFC_OperationPlanning";
    private String mesOpPlanAltFieldKeys =
            "FBillNo,FID,FMoNumber,FProductId.FNumber,FProductId.FName,"
                    + "FOperID.FNumber,FOperID.FName,FOperNumber,FPlanQty,FDocumentStatus,"
                    + "FPlanStartDate,FPlanFinishDate,FEntryID";
    private String mesOpPlanFilter = "FDocumentStatus='C'";

    /** 轻 MES：工序汇报回写 FormId */
    private String mesReportFormId = "SFC_OperationReport";
    private boolean mesReportAutoAudit = true;
    private String mesReportBillTypeNumber = "";
    private String mesReportMoField = "FMoNumber";
    private String mesReportEntryKey = "FEntity";
    private String mesReportProcessField = "FOperID";
    private String mesReportQtyField = "FFinishQty";
    private String mesReportEquipmentField = "FEquipmentId";
    private String mesReportReworkField = "FIsRework";

    /** 轻 MES：工序转移回写 FormId */
    private String mesTransferFormId = "SFC_TransferDirect";
    private boolean mesTransferAutoAudit = true;
    private String mesTransferEntryKey = "FEntity";
    private String mesTransferFromProcessField = "FFromOperId";
    private String mesTransferToProcessField = "FToOperId";
    private String mesTransferQtyField = "FQty";
}
