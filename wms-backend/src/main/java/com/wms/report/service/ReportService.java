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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        long pendingInbound = reportMapper.countPendingInbound();
        long pendingOutbound = reportMapper.countPendingOutbound();
        long pendingStockcheck = reportMapper.countPendingStockcheck();
        long pendingQc = reportMapper.countPendingQc();
        long pendingTasks = pendingInbound + pendingOutbound + pendingStockcheck + pendingQc;
        data.put("pendingTaskCount", pendingTasks);
        data.put("pendingInbound", pendingInbound);
        data.put("pendingOutbound", pendingOutbound);
        data.put("pendingStockcheck", pendingStockcheck);
        data.put("pendingQc", pendingQc);

        data.put("weeklyTrend", buildWeeklyPdaIoTrend());

        List<Map<String, Object>> whDist = reportMapper.warehouseStockDistribution();
        List<Map<String, Object>> pie = new ArrayList<>();
        for (Map<String, Object> row : whDist) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row.get("warehouseName") != null ? row.get("warehouseName") : row.get("warehouseCode"));
            item.put("value", row.get("totalQty"));
            pie.add(item);
        }
        data.put("warehouseDistribution", pie);

        List<Map<String, Object>> taskPie = new ArrayList<>();
        taskPie.add(pieItem("待入库", pendingInbound));
        taskPie.add(pieItem("待出库", pendingOutbound));
        taskPie.add(pieItem("待盘点", pendingStockcheck));
        taskPie.add(pieItem("待质检", pendingQc));
        data.put("taskDistribution", taskPie);

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

    private static Map<String, Object> pieItem(String name, long value) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
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

    /**
     * 工作台近 7 天趋势：入库取 PDA 入库记录，出库取 PDA 出库提交批次。
     */
    private Map<String, Object> buildWeeklyPdaIoTrend() {
        Map<String, Long> inboundByDay = toDayCountMap(reportMapper.pdaInboundDailyStatistics());
        Map<String, Long> outboundByDay = toDayCountMap(reportMapper.pdaOutboundDailyStatistics());
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MM-dd");
        List<String> labels = new ArrayList<>();
        List<Number> inbound = new ArrayList<>();
        List<Number> outbound = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String key = day.format(dayFmt);
            labels.add(day.format(labelFmt));
            inbound.add(inboundByDay.getOrDefault(key, 0L));
            outbound.add(outboundByDay.getOrDefault(key, 0L));
        }
        Map<String, Object> chart = new HashMap<>();
        chart.put("labels", labels);
        chart.put("inbound", inbound);
        chart.put("outbound", outbound);
        return chart;
    }

    private static Map<String, Long> toDayCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || row.get("dayLabel") == null) {
                continue;
            }
            String day = String.valueOf(row.get("dayLabel"));
            Number cnt = row.get("cnt") instanceof Number n ? n : 0;
            map.put(day, cnt.longValue());
        }
        return map;
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
