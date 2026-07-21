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

    /** 是否启用金蝶云星空企业版对接；false 时使用模拟数据 */
    private boolean enabled = false;

    /** 云星空 WebAPI 站点根地址，如 https://xxx.ik3cloud.com/K3Cloud */
    private String baseUrl = "http://localhost/K3Cloud";

    private String acctId = "demo";

    private String username = "admin";

    private String password = "";

    /** 入库单 FormId，默认采购入库单 */
    private String stockInFormId = "STK_InStock";

    /** 采购入库单 Save 成功后是否自动调用 Audit 审核 */
    private boolean stockInAutoAudit = true;

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

    /** 生产领料单列表 FieldKeys（生产退料源单） */
    private String pickMtrlLineCountFieldKeys =
            "FBillNo,FWorkShopId.FNumber,FWorkShopId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /** 生产领料单明细 FieldKeys（生产退料源单） */
    private String pickMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FWorkShopId.FNumber,FWorkShopId.FName,FEntity_FMoBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FActualQty,FEntity_FAppQty,FEntity_FBaseActualQty,"
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

    /** 委外领料单列表 FieldKeys（委外退料源单） */
    private String subPickMtrlLineCountFieldKeys =
            "FBillNo,FSupplierId.FNumber,FSupplierId.FName,FDocumentStatus,FDate,FEntity_FEntryID";

    /** 委外领料单明细 FieldKeys（委外退料源单） */
    private String subPickMtrlDetailFieldKeys =
            "FBillNo,FID,FDate,FSupplierId.FNumber,FSupplierId.FName,FEntity_FSubReqBillNo,"
                    + "FEntity_FParentMaterialId.FNumber,FEntity_FParentMaterialId.FName,"
                    + "FEntity_FMaterialId.FNumber,FEntity_FMaterialId.FName,"
                    + "FEntity_FActualQty,FEntity_FAppQty,FEntity_FBaseActualQty,"
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
     * 盘点作业列表 FieldKeys（仅头字段，用于已审核单据列表）
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
     * 检验数量 FCheckBaseQty、收料数 FActReceiveQty、判退数量 FRefuseBaseQty、
     * 合格数量(基本单位) FReceiveBaseQty、样本破坏数量(基本单位) FSampleDamageBaseQty、
     * 让步接收数量(基本单位) FCsnReceiveBaseQty、工废数量(基本单位) FProcScrapBaseQty、
     * 料废数量(基本单位) FMtrlScrapBaseQty
     */
    private String receiveBillInspectionListFieldKeys =
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
                    + "FDetailEntity_FSeq,FDetailEntity_FEntryID,FLot.FNumber,FBaseUnitId.FNumber,FStockId.FNumber";

    private int receiveBillQueryLimit = 2000;

    /** 采购入库单（STK_InStock）保存默认字段 */
    private String stockInBillTypeNumber = "RKD01_SYS";
    private String stockInBusinessType = "CG";
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

    private int connectTimeoutMs = 10000;

    private int readTimeoutMs = 30000;

    private int maxRetry = 3;
}
