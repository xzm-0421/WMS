package com.wms.mobile.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.mapper.OutboundOrderMapper;
import com.wms.pdastockcount.dto.StockCountListItemVo;
import com.wms.pdastockcount.service.PdaStockCountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "PDA-待办任务")
@RestController
@RequestMapping("/mobile/tasks")
@RequiredArgsConstructor
public class MobileTaskController {

    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final PdaStockCountService stockCountService;

    @Operation(summary = "待办任务看板")
    @GetMapping
    public ApiResult<Map<String, Object>> tasks(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status) {
        List<InboundOrder> inboundTasks = inboundOrderMapper.selectList(new LambdaQueryWrapper<InboundOrder>()
                .in(InboundOrder::getStatus, "PENDING", "INBOUND")
                .eq(InboundOrder::getDeleted, 0)
                .orderByAsc(InboundOrder::getPlanDate));

        List<OutboundOrder> outboundTasks = outboundOrderMapper.selectList(new LambdaQueryWrapper<OutboundOrder>()
                .in(OutboundOrder::getStatus, "PENDING", "PICKING", "OUTBOUND")
                .eq(OutboundOrder::getDeleted, 0)
                .orderByAsc(OutboundOrder::getPlanDate));

        List<Map<String, Object>> stockcheckTasks = loadKingdeeStockCountTasks();

        Map<String, Object> data = new HashMap<>();
        data.put("inbound", Map.of("count", inboundTasks.size(), "tasks", inboundTasks));
        data.put("outbound", Map.of("count", outboundTasks.size(), "tasks", outboundTasks));
        data.put("stockcheck", Map.of("count", stockcheckTasks.size(), "tasks", stockcheckTasks));
        return ApiResult.ok(data);
    }

    /**
     * 待盘点角标：金蝶未审核物料盘点作业（STK_StockCountInput，状态 A/B）。
     */
    private List<Map<String, Object>> loadKingdeeStockCountTasks() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            PageResult<StockCountListItemVo> page = stockCountService.list(null, 1, 100);
            List<StockCountListItemVo> records = page.getRecords() != null ? page.getRecords() : List.of();
            for (StockCountListItemVo item : records) {
                if (item == null || item.getBillNo() == null || item.getBillNo().isBlank()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("billNo", item.getBillNo());
                // 兼容旧 PDA 字段名
                row.put("taskNo", item.getBillNo());
                row.put("warehouseCode", item.getWarehouseCode());
                row.put("billDate", item.getBillDate());
                row.put("totalLines", item.getTotalLines());
                row.put("countedLines", item.getCountedLines());
                row.put("documentStatus", item.getDocumentStatus());
                row.put("status", item.isInProgress() ? "COUNTING" : "PENDING");
                row.put("remark", item.getRemark());
                rows.add(row);
            }
        } catch (Exception e) {
            log.warn("加载金蝶盘点作业待办失败: {}", e.getMessage());
        }
        return rows;
    }
}
