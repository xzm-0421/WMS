package com.wms.mobile.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.result.ApiResult;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.mapper.OutboundOrderMapper;
import com.wms.quality.entity.QcOrder;
import com.wms.quality.mapper.QcOrderMapper;
import com.wms.stockcheck.entity.StockcheckTask;
import com.wms.stockcheck.mapper.StockcheckTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "PDA-待办任务")
@RestController
@RequestMapping("/mobile/tasks")
@RequiredArgsConstructor
public class MobileTaskController {

    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final StockcheckTaskMapper stockcheckTaskMapper;
    private final QcOrderMapper qcOrderMapper;

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

        List<StockcheckTask> stockcheckTasks = stockcheckTaskMapper.selectList(new LambdaQueryWrapper<StockcheckTask>()
                .eq(StockcheckTask::getStatus, "PENDING")
                .orderByAsc(StockcheckTask::getCreateTime));

        List<QcOrder> qcTasks = qcOrderMapper.selectList(new LambdaQueryWrapper<QcOrder>()
                .eq(QcOrder::getStatus, "PENDING")
                .orderByAsc(QcOrder::getCreateTime));

        Map<String, Object> data = new HashMap<>();
        data.put("inbound", Map.of("count", inboundTasks.size(), "tasks", inboundTasks));
        data.put("outbound", Map.of("count", outboundTasks.size(), "tasks", outboundTasks));
        data.put("stockcheck", Map.of("count", stockcheckTasks.size(), "tasks", stockcheckTasks));
        data.put("qc", Map.of("count", qcTasks.size(), "tasks", qcTasks));
        return ApiResult.ok(data);
    }
}
