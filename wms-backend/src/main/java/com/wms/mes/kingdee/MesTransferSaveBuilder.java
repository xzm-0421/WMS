package com.wms.mes.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeSaveEnvelope;
import com.wms.mes.entity.MesTransfer;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 金蝶工序转移（默认 SFC_TransferDirect）Save 报文。
 */
public final class MesTransferSaveBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MesTransferSaveBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props, MesTransfer transfer) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            model.put("FDate", formatDate(transfer.getTransferTime()));
            model.put(props.getMesReportMoField(), transfer.getMoNo());
            if (StringUtils.hasText(transfer.getRemark())) {
                model.put("FDescription", transfer.getRemark());
            }
            if (StringUtils.hasText(transfer.getTransferNo())) {
                model.put("FNote", "WMS MES工序转移 " + transfer.getTransferNo());
            }
            ArrayNode entries = model.putArray(props.getMesTransferEntryKey());
            ObjectNode entry = entries.addObject();
            KingdeeSaveEnvelope.putNumberRef(entry, props.getMesTransferFromProcessField(), transfer.getFromProcessCode());
            KingdeeSaveEnvelope.putNumberRef(entry, props.getMesTransferToProcessField(), transfer.getToProcessCode());
            if (transfer.getQty() != null) {
                entry.put(props.getMesTransferQtyField(), transfer.getQty());
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶工序转移保存请求失败", e);
        }
    }

    private static String formatDate(LocalDateTime time) {
        LocalDateTime value = time == null ? LocalDateTime.now() : time;
        return value.format(DATE_TIME);
    }
}
