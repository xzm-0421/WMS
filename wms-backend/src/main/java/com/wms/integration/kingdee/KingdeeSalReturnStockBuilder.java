package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.barcode.util.BatchNoNormalizer;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按金蝶 SAL_RETURNSTOCK Save 格式构建销售退货单（关联退货通知单）。
 */
public final class KingdeeSalReturnStockBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private KingdeeSalReturnStockBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props,
                               KingdeeSalReturnStockRequest req) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            KingdeeSaveEnvelope.putNumberRef(model, "FBillTypeID", props.getSalReturnStockBillTypeNumber());
            model.put("FDate", formatDateTime(req.getBillDate()));
            String org = props.getStockInOrgNumber();
            KingdeeSaveEnvelope.putNumberRef(model, "FSaleOrgId", org);
            KingdeeSaveEnvelope.putNumberRef(model, "FStockOrgId", org);
            String customer = KingdeeSaveEnvelope.firstNonBlank(
                    req.getCustomerCode(), props.getSalReturnStockDefaultCustomerNumber());
            KingdeeSaveEnvelope.putNumberRef(model, "FRetcustId", customer);
            KingdeeSaveEnvelope.putNumberRef(model, "FReceiveCustId", customer);
            KingdeeSaveEnvelope.putNumberRef(model, "FSettleCustId", customer);
            KingdeeSaveEnvelope.putNumberRef(model, "FPayCustId", customer);
            model.put("FOwnerTypeIdHead", props.getStockInOwnerTypeHead());
            KingdeeSaveEnvelope.putNumberRef(model, "FOwnerIdHead", org);
            if (StringUtils.hasText(req.getNote())) {
                model.put("FHeadNote", req.getNote().trim());
            } else if (StringUtils.hasText(req.getSourceBillNo())) {
                model.put("FHeadNote", "WMS PDA退货通知:" + req.getSourceBillNo().trim());
            }

            ObjectNode fin = model.putObject("SubHeadEntity");
            KingdeeSaveEnvelope.putNumberRef(fin, "FSettleOrgId", org);
            KingdeeSaveEnvelope.putNumberRef(fin, "FSettleCurrId", props.getStockInSettleCurrNumber());
            KingdeeSaveEnvelope.putNumberRef(fin, "FLocalCurrId", props.getStockInSettleCurrNumber());
            KingdeeSaveEnvelope.putNumberRef(fin, "FExchangeTypeId", props.getStockInExchangeTypeNumber());
            fin.put("FExchangeRate", 1.0);

            ArrayNode entries = model.putArray("FEntity");
            List<KingdeeSalReturnStockRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            for (KingdeeSalReturnStockRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, req, line));
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶销售退货单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeeSalReturnStockRequest req,
                                         KingdeeSalReturnStockRequest.Line line) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        String unit = StringUtils.hasText(line.getUnitCode())
                ? line.getUnitCode().trim() : props.getStockInDefaultUnitNumber();
        String stock = KingdeeSaveEnvelope.firstNonBlank(
                line.getWarehouseCode(), props.getSalesOutStockDefaultWarehouseNumber(),
                props.getStockInDefaultWarehouseNumber());
        String org = props.getStockInOrgNumber();

        entry.put("FENTRYID", 0);
        entry.put("FRowType", "Standard");
        KingdeeSaveEnvelope.putNumberRef(entry, "FMaterialId", line.getMaterialCode());
        KingdeeSaveEnvelope.putNumberRef(entry, "FUnitID", unit);
        entry.put("FRealQty", qty);
        entry.put("FMustqty", qty);
        KingdeeSaveEnvelope.putNumberRef(entry, "FSalUnitID", unit);
        entry.put("FSalUnitQty", qty);
        entry.put("FSalBaseQty", qty);
        entry.put("FPriceBaseQty", qty);
        KingdeeSaveEnvelope.putNumberRef(entry, "FReturnType",
                KingdeeSaveEnvelope.firstNonBlank(line.getReturnTypeNumber(), props.getSalReturnStockReturnTypeNumber()));
        entry.put("FOwnerTypeId", props.getStockInOwnerTypeHead());
        KingdeeSaveEnvelope.putNumberRef(entry, "FOwnerId", org);
        KingdeeSaveEnvelope.putNumberRef(entry, "FStockId", stock);
        KingdeeSaveEnvelope.putNumberRef(entry, "FStockstatusId", props.getStockInStockStatusNumber());
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());
        if (StringUtils.hasText(lot)) {
            KingdeeSaveEnvelope.putNumberRef(entry, "FLot", lot);
        }
        LocalDate deliveryDate = line.getDeliveryDate() != null ? line.getDeliveryDate()
                : (req.getBillDate() != null ? req.getBillDate() : LocalDate.now());
        entry.put("FDeliveryDate", deliveryDate.format(DATE_ONLY));
        if (StringUtils.hasText(req.getSourceBillNo())) {
            entry.put("FSrcBillTypeID", props.getSalReturnNoticeFormId());
            entry.put("FSrcBillNo", req.getSourceBillNo().trim());
        }
        if (StringUtils.hasText(line.getEntryNote())) {
            entry.put("FNote", line.getEntryNote().trim());
        }
        if (props.isSalReturnStockSendStockLoc() && StringUtils.hasText(line.getLocationCode())) {
            ObjectNode loc = entry.putObject("FStocklocId");
            ObjectNode flex = loc.putObject("FSTOCKLOCID__FF100001");
            flex.put("FNumber", line.getLocationCode().trim());
        }

        if (req.getSourceBillId() != null && req.getSourceBillId() > 0
                && line.getSourceEntryId() != null && line.getSourceEntryId() > 0) {
            ArrayNode links = entry.putArray("FEntity_Link");
            ObjectNode link = links.addObject();
            link.put("FLinkId", 0);
            link.put("FEntity_Link_FFlowId", blankAsSpace(props.getSalReturnStockLinkFlowId()));
            link.put("FEntity_Link_FFlowLineId", props.getSalReturnStockLinkFlowLineId());
            link.put("FEntity_Link_FRuleId", props.getSalReturnStockLinkRuleId());
            link.put("FEntity_Link_FSTableName", props.getSalReturnStockLinkSTableName());
            KingdeeSaveEnvelope.putLong(link, "FEntity_Link_FSBillId", req.getSourceBillId());
            KingdeeSaveEnvelope.putLong(link, "FEntity_Link_FSId", line.getSourceEntryId());
            link.put("FEntity_Link_FBaseunitQtyOld", qty);
            link.put("FEntity_Link_FBaseunitQty", qty);
            link.put("FEntity_Link_FSalBaseQtyOld", qty);
            link.put("FEntity_Link_FSalBaseQty", qty);
            link.put("FEntity_Link_FPriceBaseQtyOld", qty);
            link.put("FEntity_Link_FPriceBaseQty", qty);
        }
        return entry;
    }

    private static String blankAsSpace(String value) {
        return StringUtils.hasText(value) ? value.trim() : " ";
    }

    private static String formatDateTime(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return LocalDateTime.of(d.getYear(), d.getMonth(), d.getDayOfMonth(), 0, 0, 0).format(DATE_TIME);
    }
}
