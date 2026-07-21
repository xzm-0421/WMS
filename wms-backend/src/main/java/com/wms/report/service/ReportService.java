package com.wms.report.service;

import com.wms.common.result.PageResult;
import com.wms.inventory.dto.InventoryWarningDto;
import com.wms.inventory.service.InventoryService;
import com.wms.picking.service.PickIssueService;
import com.wms.report.mapper.ReportMapper;
import com.wms.system.entity.WmsAuditTrail;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;
    private final InventoryService inventoryService;
    private final AuditTrailService auditTrailService;
    private final PickIssueService pickIssueService;

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("todayInboundCount", reportMapper.countTodayInbound());
        data.put("todayOutboundCount", reportMapper.countTodayOutbound());
        data.put("skuCount", reportMapper.countSku());
        long pendingTasks = reportMapper.countPendingInbound()
                + reportMapper.countPendingOutbound()
                + reportMapper.countPendingStockcheck()
                + reportMapper.countPendingQc();
        data.put("pendingTaskCount", pendingTasks);
        data.put("pendingInbound", reportMapper.countPendingInbound());
        data.put("pendingOutbound", reportMapper.countPendingOutbound());
        data.put("pendingStockcheck", reportMapper.countPendingStockcheck());
        data.put("pendingQc", reportMapper.countPendingQc());

        Map<String, Object> trend = inboundStatistics(null, null);
        data.put("weeklyTrend", trend);

        List<Map<String, Object>> whDist = reportMapper.warehouseStockDistribution();
        List<Map<String, Object>> pie = new ArrayList<>();
        for (Map<String, Object> row : whDist) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row.get("warehouseName") != null ? row.get("warehouseName") : row.get("warehouseCode"));
            item.put("value", row.get("totalQty"));
            pie.add(item);
        }
        data.put("warehouseDistribution", pie);

        List<InventoryWarningDto> warnings = inventoryService.getWarnings(null);
        data.put("warnings", warnings.size() > 10 ? warnings.subList(0, 10) : warnings);

        List<WmsAuditTrail> trails = auditTrailService.recent(10);
        List<Map<String, Object>> logs = new ArrayList<>();
        for (WmsAuditTrail t : trails) {
            Map<String, Object> log = new HashMap<>();
            log.put("operatorName", t.getOperatorName());
            log.put("module", t.getBizType());
            log.put("operationType", t.getAction());
            log.put("operationContent", t.getBizNo() + (t.getDetailJson() != null ? " " + t.getDetailJson() : ""));
            log.put("operationTime", t.getCreateTime() != null ? t.getCreateTime().toString() : "");
            logs.add(log);
        }
        data.put("recentLogs", logs);
        return data;
    }

    public Map<String, Object> pickingDashboard() {
        Map<String, Object> data = new HashMap<>();
        long pickTotal = pickIssueService.countTodayTotal();
        long pickDone = pickIssueService.countTodayCompleted();
        data.put("pickIssueTotal", pickTotal);
        data.put("pickIssueCompleted", pickDone);
        data.put("pickIssueRate", pickTotal > 0 ? pickDone * 100.0 / pickTotal : 0);
        data.put("prepNoticeOpen", reportMapper.countOpenPrepNotice());
        data.put("deliveryOnTimeRate", reportMapper.deliveryOnTimeRate());
        return data;
    }

    public Map<String, Object> warehouseDashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("inboundTrend", inboundStatistics(null, null));
        data.put("outboundTrend", outboundStatistics(null, null));
        data.put("totalStockQty", reportMapper.totalStockQty());
        data.put("warehouseUtilization", reportMapper.warehouseUtilizationList());
        data.put("lowStockCount", inventoryService.getWarnings(null).size());
        return data;
    }

    public List<Map<String, Object>> inventorySummary(String warehouseCode) {
        return reportMapper.inventorySummary(warehouseCode);
    }

    public PageResult<InventoryWarningDto> lowStockReport(String warehouseCode, long current, long size) {
        List<InventoryWarningDto> all = inventoryService.getWarnings(warehouseCode);
        long total = all.size();
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<InventoryWarningDto> page = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResult.of(page, total, current, size);
    }

    public Map<String, Object> inboundStatistics(String startDate, String endDate) {
        return buildDailyChart(reportMapper.inboundDailyStatistics(startDate, endDate), "inbound");
    }

    public Map<String, Object> outboundStatistics(String startDate, String endDate) {
        return buildDailyChart(reportMapper.outboundDailyStatistics(startDate, endDate), "outbound");
    }

    private Map<String, Object> buildDailyChart(List<Map<String, Object>> rows, String seriesKey) {
        List<String> labels = new ArrayList<>();
        List<Number> inbound = new ArrayList<>();
        List<Number> outbound = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            labels.add(String.valueOf(row.get("dayLabel")));
            Number cnt = (Number) row.get("cnt");
            if ("inbound".equals(seriesKey)) {
                inbound.add(cnt);
                outbound.add(0);
            } else {
                inbound.add(0);
                outbound.add(cnt);
            }
        }
        Map<String, Object> chart = new HashMap<>();
        chart.put("labels", labels);
        chart.put("inbound", inbound);
        chart.put("outbound", outbound);
        return chart;
    }
}
