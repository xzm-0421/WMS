package com.wms.mobile.service;

import com.wms.inbound.dto.InboundScanRequest;
import com.wms.inbound.service.InboundService;
import com.wms.mobile.dto.MobileSyncRequest;
import com.wms.outbound.dto.OutboundScanRequest;
import com.wms.outbound.service.OutboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MobileSyncService {

    private final InboundService inboundService;
    private final OutboundService outboundService;

    public Map<String, Object> sync(MobileSyncRequest request) {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int fail = 0;
        if (request.getOfflineData() != null) {
            for (MobileSyncRequest.MobileSyncItem item : request.getOfflineData()) {
                Map<String, Object> row = new HashMap<>();
                row.put("clientId", item.getClientId());
                try {
                    processItem(item, request.getDeviceNo());
                    row.put("success", true);
                    row.put("message", "同步成功");
                    success++;
                } catch (Exception e) {
                    row.put("success", false);
                    row.put("message", e.getMessage());
                    fail++;
                }
                results.add(row);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("totalSynced", results.size());
        data.put("successCount", success);
        data.put("failCount", fail);
        data.put("results", results);
        data.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        data.put("updatedTasks", Map.of("inbound", List.of(), "outbound", List.of(), "stockcheck", List.of()));
        return data;
    }

    @SuppressWarnings("unchecked")
    private void processItem(MobileSyncRequest.MobileSyncItem item, String deviceNo) {
        Map<String, Object> payload = item.getPayload() instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        if ("INBOUND_SCAN".equals(item.getOperationType())) {
            InboundScanRequest req = new InboundScanRequest();
            req.setLineNo(((Number) payload.get("lineNo")).intValue());
            req.setMaterialCode(String.valueOf(payload.get("materialCode")));
            req.setBatchNo(String.valueOf(payload.getOrDefault("batchNo", "")));
            req.setQuantity(new java.math.BigDecimal(String.valueOf(payload.get("quantity"))));
            req.setTargetLocation(String.valueOf(payload.get("targetLocation")));
            req.setDeviceNo(deviceNo);
            inboundService.scanReceive(item.getOrderNo(), req);
        } else if ("OUTBOUND_SCAN".equals(item.getOperationType())) {
            OutboundScanRequest req = new OutboundScanRequest();
            req.setLineNo(((Number) payload.get("lineNo")).intValue());
            req.setMaterialCode(String.valueOf(payload.get("materialCode")));
            req.setBatchNo(String.valueOf(payload.getOrDefault("batchNo", "")));
            req.setQuantity(new java.math.BigDecimal(String.valueOf(payload.get("quantity"))));
            req.setSourceLocation(String.valueOf(payload.get("sourceLocation")));
            req.setDeviceNo(deviceNo);
            outboundService.scanIssue(item.getOrderNo(), req);
        }
    }
}
