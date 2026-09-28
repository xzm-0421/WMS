package com.wms.mes.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeSaveEnvelope;
import com.wms.mes.entity.MesDefect;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 金蝶不良单 Save 报文（FormId 与字段可配置，待现场确认）。
 */
public final class MesDefectSaveBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MesDefectSaveBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props, MesDefect defect) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            if (StringUtils.hasText(props.getMesDefectBillTypeNumber())) {
                KingdeeSaveEnvelope.putNumberRef(model, "FBillType", props.getMesDefectBillTypeNumber());
            }
            model.put("FDate", formatDate(defect.getCreateTime()));
            model.put(props.getMesDefectMoField(), defect.getMoNo());
            if (StringUtils.hasText(defect.getDefectDesc()) && StringUtils.hasText(props.getMesDefectDescField())) {
                model.put(props.getMesDefectDescField(), defect.getDefectDesc());
            }
            if (StringUtils.hasText(defect.getDefectNo())) {
                model.put("FNote", "MES不良单 " + defect.getDefectNo());
            }
            ArrayNode entries = model.putArray(props.getMesDefectEntryKey());
            ObjectNode entry = entries.addObject();
            if (StringUtils.hasText(props.getMesDefectProcessField())) {
                KingdeeSaveEnvelope.putNumberRef(entry, props.getMesDefectProcessField(), defect.getSourceProcessCode());
            }
            if (defect.getDefectQty() != null && StringUtils.hasText(props.getMesDefectQtyField())) {
                entry.put(props.getMesDefectQtyField(), defect.getDefectQty());
            }
            if (StringUtils.hasText(defect.getDefectType()) && StringUtils.hasText(props.getMesDefectTypeField())) {
                entry.put(props.getMesDefectTypeField(), defect.getDefectType());
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶不良单保存请求失败", e);
        }
    }

    private static String formatDate(LocalDateTime time) {
        LocalDateTime value = time == null ? LocalDateTime.now() : time;
        return value.format(DATE_TIME);
    }
}
