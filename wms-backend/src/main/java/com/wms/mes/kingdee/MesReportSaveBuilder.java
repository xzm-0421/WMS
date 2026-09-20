package com.wms.mes.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeSaveEnvelope;
import com.wms.mes.MesConstants;
import com.wms.mes.entity.MesReport;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 金蝶工序汇报（默认 SFC_OperationReport）Save 报文。
 */
public final class MesReportSaveBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MesReportSaveBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props, MesReport report) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            if (StringUtils.hasText(props.getMesReportBillTypeNumber())) {
                KingdeeSaveEnvelope.putNumberRef(model, "FBillType", props.getMesReportBillTypeNumber());
            }
            model.put("FDate", formatDate(report.getReportTime()));
            model.put(props.getMesReportMoField(), report.getMoNo());
            if (StringUtils.hasText(report.getRemark())) {
                model.put("FDescription", report.getRemark());
            }
            if (StringUtils.hasText(report.getReportNo())) {
                model.put("FNote", "WMS MES报工 " + report.getReportNo());
            }
            boolean rework = MesConstants.REPORT_REWORK.equalsIgnoreCase(report.getReportType());
            if (StringUtils.hasText(props.getMesReportReworkField())) {
                model.put(props.getMesReportReworkField(), rework);
            }
            if (rework && StringUtils.hasText(report.getDefectNo())) {
                model.put("F_MES_DefectNo", report.getDefectNo());
            }
            ArrayNode entries = model.putArray(props.getMesReportEntryKey());
            ObjectNode entry = entries.addObject();
            KingdeeSaveEnvelope.putNumberRef(entry, props.getMesReportProcessField(), report.getProcessCode());
            if (report.getQty() != null) {
                entry.put(props.getMesReportQtyField(), report.getQty());
            }
            if (StringUtils.hasText(report.getEquipmentCode())) {
                KingdeeSaveEnvelope.putNumberRef(entry, props.getMesReportEquipmentField(), report.getEquipmentCode());
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶工序汇报保存请求失败", e);
        }
    }

    private static String formatDate(LocalDateTime time) {
        LocalDateTime value = time == null ? LocalDateTime.now() : time;
        return value.format(DATE_TIME);
    }
}
