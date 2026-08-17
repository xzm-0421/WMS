package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.barcode.util.BatchNoNormalizer;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按金蝶 STK_InStock Save 接口格式构建采购入库单 JSON（直接 Save，不走下推）。
 */
public final class KingdeePurchaseInStockBuilder {

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 收料通知单 / 采购入库分录「送货单号」自定义字段 {@code F_QVHU_Text_qtr}
     *（采购入库仅挂在 InStockEntry，勿写表头 InStock）。
     */
    public static final String SEND_BILL_NO_FIELD = "F_QVHU_Text_qtr";

    private KingdeePurchaseInStockBuilder() {
    }

    /**
     * 构建采购入库单完整 Save 请求体。
     */
    public static String build(ObjectMapper mapper, KingdeeCloudProperties props,
                               KingdeePurchaseInStockRequest req) {
        try {
            String sendBillNo = resolveSendBillNo(req);
            ObjectNode root = mapper.createObjectNode();
            // 新建单据 NeedUpDateFields 留空表示提交 Model 全部字段；勿只列部分字段否则其它列可能不落库
            root.putArray("NeedUpDateFields");
            ArrayNode needReturn = root.putArray("NeedReturnFields");
            needReturn.add("FBillNo");
            needReturn.add("FID");
            // 送货单号挂在分录 InStockEntry，表头 InStock 无此属性
            needReturn.add("FInStockEntry." + SEND_BILL_NO_FIELD);
            root.put("IsDeleteEntry", "true");
            root.put("SubSystemId", "");
            root.put("IsVerifyBaseDataField", "false");
            root.put("IsEntryBatchFill", "true");
            root.put("ValidateFlag", "true");
            root.put("NumberSearch", "true");
            // 自动调整字段顺序；送货单号在分录末尾再次写入，避免被关联源单空值覆盖
            root.put("IsAutoAdjustField", "true");
            root.put("InterationFlags", "");
            root.put("IgnoreInterationFlag", "");
            root.put("IsControlPrecision", "false");
            root.put("ValidateRepeatJson", "false");

            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            boolean outsource = isOutsourceBusiness(req, props);
            String billTypeNumber = resolveBillTypeNumber(req, props, outsource);
            String businessType = resolveBusinessType(req, props, outsource);
            putNumberRef(model, "FBillTypeID", billTypeNumber);
            model.put("FBusinessType", businessType);
            model.put("FDate", formatBillDate(req.getBillDate()));
            putNumberRef(model, "FStockOrgId", props.getStockInOrgNumber());
            putNumberRef(model, "FStockDeptId", props.getStockInStockDeptNumber());
            putNumberRef(model, "FStockerId", props.getStockInStockerNumber());
            putNumberRef(model, "FDemandOrgId", props.getStockInOrgNumber());
            putNumberRef(model, "FPurchaseOrgId", props.getStockInOrgNumber());
            putNumberRef(model, "FCorrespondOrgId", props.getStockInOrgNumber());
            putNumberRef(model, "FPurchaseDeptId", props.getStockInPurchaseDeptNumber());
            putNumberRef(model, "FPurchaserId", props.getStockInPurchaserNumber());

            String supplier = StringUtils.hasText(req.getSupplierCode())
                    ? req.getSupplierCode().trim()
                    : props.getStockInDefaultSupplierNumber();
            putNumberRef(model, "FSupplierId", supplier);
            putNumberRef(model, "FSupplyId", supplier);
            putNumberRef(model, "FSettleId", supplier);
            putNumberRef(model, "FChargeId", supplier);

            model.put("FOwnerTypeIdHead", props.getStockInOwnerTypeHead());
            putNumberRef(model, "FOwnerIdHead", props.getStockInOrgNumber());
            model.put("FCDateOffsetValue", 0);
            model.put("FSplitBillType", props.getStockInSplitBillType());
            putNumberRef(model, "FSalOutStockOrgId", props.getStockInOrgNumber());

            ObjectNode fin = model.putObject("FInStockFin");
            putNumberRef(fin, "FSettleOrgId", props.getStockInOrgNumber());
            putNumberRef(fin, "FSettleCurrId", props.getStockInSettleCurrNumber());
            fin.put("FIsIncludedTax", true);
            fin.put("FPriceTimePoint", "1");
            putNumberRef(fin, "FLocalCurrId", props.getStockInSettleCurrNumber());
            putNumberRef(fin, "FExchangeTypeId", props.getStockInExchangeTypeNumber());
            fin.put("FExchangeRate", 1.0);
            fin.put("FISPRICEEXCLUDETAX", false);
            fin.put("FAllDisCount", 0.0);
            fin.put("FHSExchangeRate", 1.0);

            ArrayNode entries = model.putArray("FInStockEntry");
            List<KingdeePurchaseInStockRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            for (KingdeePurchaseInStockRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, req, line, sendBillNo));
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶采购入库单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeePurchaseInStockRequest req,
                                         KingdeePurchaseInStockRequest.Line line,
                                         String sendBillNo) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        BigDecimal priceQty = line.getPriceUnitQty() != null && line.getPriceUnitQty().compareTo(BigDecimal.ZERO) > 0
                ? line.getPriceUnitQty() : qty;
        String unit = defaultUnit(line.getUnitCode(), props);
        String priceUnit = defaultUnit(
                StringUtils.hasText(line.getPriceUnitCode()) ? line.getPriceUnitCode() : line.getUnitCode(),
                props);
        String lineSendBillNo = firstNonBlank(line.getSendBillNo(), sendBillNo);

        entry.put("FRowType", "Standard");
        entry.put("FWWInType", props.getStockInWwInType());
        entry.put("FProductType", "1");
        putNumberRef(entry, "FMaterialId", line.getMaterialCode());
        putNumberRef(entry, "FUnitID", unit);
        if (StringUtils.hasText(line.getMaterialName())) {
            entry.put("FMaterialDesc", line.getMaterialName().trim());
        }
        entry.put("FWWPickMtlQty", 0.0);
        applyConsistentInboundQty(entry, qty, priceQty);
        putNumberRef(entry, "FPriceUnitID", priceUnit);
        entry.put("FPrice", 0.0);
        String lot = resolveLot(req, line);
        if (StringUtils.hasText(lot)) {
            putNumberRef(entry, "FLot", lot);
        }
        putNumberRef(entry, "FStockId",
                firstNonBlank(line.getWarehouseCode(), props.getStockInDefaultWarehouseNumber()));
        if (props.isStockInSendStockLoc() && StringUtils.hasText(line.getLocationCode())) {
            putNumberRef(entry, "FStockLocId", line.getLocationCode());
        }
        entry.put("FDisPriceQty", 0.0);
        putNumberRef(entry, "FStockStatusId", props.getStockInStockStatusNumber());
        entry.put("FGiveAway", props.isStockInGiveAway());
        entry.put("FOWNERTYPEID", props.getStockInOwnerTypeHead());
        putNumberRef(entry, "FOWNERID", props.getStockInOrgNumber());
        entry.put("FExtAuxUnitQty", 0.0);
        boolean fromReceiveBill = StringUtils.hasText(req.getSourceBillNo());
        entry.put("FCheckInComing", fromReceiveBill && props.isStockInCheckInComing());
        entry.put("FIsReceiveUpdateStock", false);
        entry.put("FInvoicedJoinQty", 0.0);
        putNumberRef(entry, "FRemainInStockUnitId", unit);
        entry.put("FBILLINGCLOSE", false);
        entry.put("FTaxPrice", 0.0);
        entry.put("FEntryTaxRate", props.getStockInEntryTaxRate());
        entry.put("FDiscountRate", 0.0);
        entry.put("FCostPrice", 0.0);
        entry.put("FAuxUnitQty", 0.0);
        entry.put("FAllAmountExceptDisCount", 0.0);
        entry.put("FPriceDiscount", 0.0);
        entry.put("FConsumeSumQty", 0.0);
        entry.put("FBaseConsumeSumQty", 0.0);
        entry.put("FBeforeDisPriceQty", 0.0);
        entry.put("FSalOutStockEntryId", 0);
        entry.put("F_YVZR_Qty_qtr", 0.0);
        entry.put("FSampleQty2", 0.0);
        entry.put("FCOSTRATE", 0.0);
        entry.put("FPayableEntryID", 0);

        entry.put("FSRCBILLTYPEID", req.getSourceBillType());
        if (StringUtils.hasText(req.getSourceBillNo())) {
            entry.put("FSRCBillNo", req.getSourceBillNo().trim());
        }
        if (line.getSourceLineNo() != null && line.getSourceLineNo() > 0) {
            entry.put("F_YVZR_Integer_83g", line.getSourceLineNo());
        }
        if (StringUtils.hasText(line.getPoOrderNo())) {
            entry.put("FPOOrderNo", line.getPoOrderNo().trim());
        }
        if (line.getPoOrderEntryId() != null && line.getPoOrderEntryId() > 0) {
            entry.put("FPOORDERENTRYID", line.getPoOrderEntryId());
        }
        if (line.getSourceBillId() != null && line.getSourceBillId() > 0
                && line.getSourceEntryId() != null && line.getSourceEntryId() > 0) {
            ArrayNode links = entry.putArray("FInStockEntry_Link");
            ObjectNode link = links.addObject();
            KingdeeInStockEntryLink sourceLink = line.getSourceLink();
            if (sourceLink == null) {
                sourceLink = KingdeeInStockEntryLink.fromReceiveLine(
                        props,
                        line.getSourceBillId(),
                        line.getSourceEntryId(),
                        qty,
                        qty,
                        qty);
            }
            appendInStockEntryLink(link, sourceLink);
        }

        ArrayNode taxDetails = entry.putArray("FTaxDetailSubEntity");
        ObjectNode tax = taxDetails.addObject();
        tax.put("FTaxRate", 0.0);
        // 送货单号放在分录最后写入：关联源单/自动调整后仍保证必填有值
        putSendBillNo(entry, lineSendBillNo);
        return entry;
    }

    private static boolean isOutsourceBusiness(KingdeePurchaseInStockRequest req, KingdeeCloudProperties props) {
        String biz = StringUtils.hasText(req.getBusinessType())
                ? req.getBusinessType().trim()
                : "";
        String ww = StringUtils.hasText(props.getStockInWwBusinessType())
                ? props.getStockInWwBusinessType().trim()
                : "WW";
        return ww.equalsIgnoreCase(biz);
    }

    private static String resolveBusinessType(KingdeePurchaseInStockRequest req,
                                              KingdeeCloudProperties props,
                                              boolean outsource) {
        if (StringUtils.hasText(req.getBusinessType())) {
            return req.getBusinessType().trim();
        }
        if (outsource && StringUtils.hasText(props.getStockInWwBusinessType())) {
            return props.getStockInWwBusinessType().trim();
        }
        return StringUtils.hasText(props.getStockInBusinessType())
                ? props.getStockInBusinessType().trim() : "CG";
    }

    private static String resolveBillTypeNumber(KingdeePurchaseInStockRequest req,
                                                KingdeeCloudProperties props,
                                                boolean outsource) {
        if (StringUtils.hasText(req.getBillTypeNumber())) {
            return req.getBillTypeNumber().trim();
        }
        if (outsource && StringUtils.hasText(props.getStockInWwBillTypeNumber())) {
            return props.getStockInWwBillTypeNumber().trim();
        }
        return props.getStockInBillTypeNumber();
    }

    /**
     * 采购入库送货单号写入分录 {@link #SEND_BILL_NO_FIELD}（表头 InStock 实体无此属性）。
     */
    private static void putSendBillNo(ObjectNode entry, String sendBillNo) {
        String value = StringUtils.hasText(sendBillNo) ? sendBillNo.trim() : "WMS";
        entry.put(SEND_BILL_NO_FIELD, value);
    }

    private static void applyConsistentInboundQty(ObjectNode entry, BigDecimal qty, BigDecimal priceQty) {
        BigDecimal value = qty == null ? BigDecimal.ZERO : qty;
        BigDecimal price = priceQty == null ? value : priceQty;
        entry.put("FRealQty", value);
        entry.put("FPriceUnitQty", price);
        entry.put("FPriceBaseQty", value);
        entry.put("FStockBaseQty", value);
        entry.put("FBaseUnitQty", value);
        entry.put("FBaseJoinQty", value);
        entry.put("FInStockJoinBaseQty", value);
        entry.put("FRemainInStockQty", value);
        entry.put("FRemainInStockBaseQty", value);
        entry.put("FAPNotJoinQty", value);
    }

    /**
     * 送货单号取值：行上收料通知单 F_QVHU_Text_qtr → 收料单号 → 批次号 → 固定占位，保证必填不为空。
     */
    private static String resolveSendBillNo(KingdeePurchaseInStockRequest req) {
        if (req.getLines() != null) {
            for (KingdeePurchaseInStockRequest.Line line : req.getLines()) {
                if (line != null && StringUtils.hasText(line.getSendBillNo())) {
                    return line.getSendBillNo().trim();
                }
            }
        }
        if (StringUtils.hasText(req.getSourceBillNo())) {
            return req.getSourceBillNo().trim();
        }
        if (StringUtils.hasText(req.getBatchNo())) {
            return req.getBatchNo().trim();
        }
        return "WMS";
    }

    private static String resolveLot(KingdeePurchaseInStockRequest req,
                                     KingdeePurchaseInStockRequest.Line line) {
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());
        if (StringUtils.hasText(lot)) {
            return lot;
        }
        LocalDate date = req.getBillDate() != null ? req.getBillDate() : LocalDate.now();
        return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static void appendInStockEntryLink(ObjectNode link, KingdeeInStockEntryLink sourceLink) {
        putLong(link, "FLinkId", sourceLink.getLinkId());
        link.put("FInStockEntry_Link_FFlowId", defaultBlank(sourceLink.getFlowId()));
        putInt(link, "FInStockEntry_Link_FFlowLineId", sourceLink.getFlowLineId());
        link.put("FInStockEntry_Link_FRuleId", defaultBlank(sourceLink.getRuleId()));
        putInt(link, "FInStockEntry_Link_FSTableId", sourceLink.getSourceTableId());
        link.put("FInStockEntry_Link_FSTableName", defaultBlank(sourceLink.getSourceTableName()));
        putLong(link, "FInStockEntry_Link_FSBillId", sourceLink.getSourceBillId());
        putLong(link, "FInStockEntry_Link_FSId", sourceLink.getSourceEntryId());
        putDecimal(link, "FInStockEntry_Link_FRemainInStockBaseQtyOld", sourceLink.getRemainInStockBaseQtyOld());
        putDecimal(link, "FInStockEntry_Link_FRemainInStockBaseQty", sourceLink.getRemainInStockBaseQty());
        putDecimal(link, "FInStockEntry_Link_FBaseUnitQtyOld", sourceLink.getBaseUnitQtyOld());
        putDecimal(link, "FInStockEntry_Link_FBaseUnitQty", sourceLink.getBaseUnitQty());
        putDecimal(link, "FInStockEntry_Link_FBaseJoinQty", sourceLink.getBaseUnitQty());
        putDecimal(link, "FInStockEntry_Link_FStockBaseQty", sourceLink.getBaseUnitQty());
        link.put("FInStockEntry_Link_FLnk1TrackerId", defaultBlank(sourceLink.getLnk1TrackerId()));
        link.put("FInStockEntry_Link_FLnk1SState", defaultBlank(sourceLink.getLnk1State()));
        putDecimal(link, "FInStockEntry_Link_FLnk1Amount", sourceLink.getLnk1Amount());
        link.put("FInStockEntry_Link_FLnkTrackerId", defaultBlank(sourceLink.getLnkTrackerId()));
        link.put("FInStockEntry_Link_FLnkSState", defaultBlank(sourceLink.getLnkState()));
        putDecimal(link, "FInStockEntry_Link_FLnkAmount", sourceLink.getLnkAmount());
        link.put("FInStockEntry_Link_FLnk2TrackerId", defaultBlank(sourceLink.getLnk2TrackerId()));
        link.put("FInStockEntry_Link_FLnk2SState", defaultBlank(sourceLink.getLnk2State()));
        putDecimal(link, "FInStockEntry_Link_FLnk2Amount", sourceLink.getLnk2Amount());
    }

    private static String defaultBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : " ";
    }

    private static void putLong(ObjectNode node, String field, Long value) {
        if (value != null && value > 0) {
            node.put(field, value);
        } else if (value != null) {
            node.put(field, 0);
        }
    }

    private static void putInt(ObjectNode node, String field, Integer value) {
        node.put(field, value == null ? 0 : value);
    }

    private static void putDecimal(ObjectNode node, String field, BigDecimal value) {
        node.put(field, value == null ? BigDecimal.ZERO : value);
    }

    private static String defaultUnit(String unitCode, KingdeeCloudProperties props) {
        if (StringUtils.hasText(unitCode)) {
            return unitCode.trim();
        }
        return props.getStockInDefaultUnitNumber();
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    private static String formatBillDate(LocalDate billDate) {
        LocalDate date = billDate != null ? billDate : LocalDate.now();
        return date.format(DATE_ONLY) + " 00:00:00";
    }

    private static void putNumberRef(ObjectNode parent, String field, String number) {
        if (!StringUtils.hasText(number)) {
            return;
        }
        ObjectNode ref = parent.putObject(field);
        ref.put("FNumber", number.trim());
    }
}
