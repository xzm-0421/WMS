package com.wms.mes.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeSaveEnvelope;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesReworkOp;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 金蝶返工工单 Save 报文（FormId 与字段可配置，待现场确认）。
 */
public final class MesReworkOrderSaveBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MesReworkOrderSaveBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props,
                               MesDefect defect, List<MesReworkOp> ops) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            if (StringUtils.hasText(props.getMesReworkBillTypeNumber())) {
                KingdeeSaveEnvelope.putNumberRef(model, "FBillType", props.getMesReworkBillTypeNumber());
            }
            model.put("FDate", formatDate(defect.getCreateTime()));
            model.put(props.getMesReworkMoField(), defect.getMoNo());
            if (StringUtils.hasText(props.getMesReworkDefectNoField())) {
                model.put(props.getMesReworkDefectNoField(), defect.getDefectNo());
            }
            model.put("FNote", "MES返工工单 源不良单 " + defect.getDefectNo());
            ArrayNode entries = model.putArray(props.getMesReworkEntryKey());
            for (MesReworkOp op : ops) {
                ObjectNode entry = entries.addObject();
                if (StringUtils.hasText(props.getMesReworkProcessField())) {
                    KingdeeSaveEnvelope.putNumberRef(entry, props.getMesReworkProcessField(), op.getProcessCode());
                }
                if (op.getPlanQty() != null && StringUtils.hasText(props.getMesReworkQtyField())) {
                    entry.put(props.getMesReworkQtyField(), op.getPlanQty());
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶返工工单保存请求失败", e);
        }
    }

    private static String formatDate(LocalDateTime time) {
        LocalDateTime value = time == null ? LocalDateTime.now() : time;
        return value.format(DATE_TIME);
    }
}
