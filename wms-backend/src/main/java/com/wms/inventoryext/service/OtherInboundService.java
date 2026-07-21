package com.wms.inventoryext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.service.InventoryService;
import com.wms.inventoryext.dto.OtherInboundCreateRequest;
import com.wms.inventoryext.entity.OtherInbound;
import com.wms.inventoryext.entity.OtherInboundLine;
import com.wms.inventoryext.mapper.OtherInboundLineMapper;
import com.wms.inventoryext.mapper.OtherInboundMapper;
import com.wms.system.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OtherInboundService {

    private final OtherInboundMapper orderMapper;
    private final OtherInboundLineMapper lineMapper;
    private final InventoryService inventoryService;
    private final AuditTrailService auditTrailService;

    public PageResult<OtherInbound> page(String orderNo, String inboundType, long current, long size) {
        LambdaQueryWrapper<OtherInbound> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(orderNo), OtherInbound::getOrderNo, orderNo)
                .eq(StringUtils.hasText(inboundType), OtherInbound::getInboundType, inboundType)
                .eq(OtherInbound::getDeleted, 0)
                .orderByDesc(OtherInbound::getCreateTime);
        Page<OtherInbound> page = orderMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(String orderNo) {
        OtherInbound order = orderMapper.selectOne(new LambdaQueryWrapper<OtherInbound>()
                .eq(OtherInbound::getOrderNo, orderNo));
        List<OtherInboundLine> lines = lineMapper.selectList(new LambdaQueryWrapper<OtherInboundLine>()
                .eq(OtherInboundLine::getOrderNo, orderNo));
        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        data.put("lines", lines);
        return data;
    }

    @Transactional
    public String create(OtherInboundCreateRequest request, String operatorName) {
        String orderNo = OrderNoGenerator.next("OI");
        OtherInbound order = new OtherInbound();
        order.setOrderNo(orderNo);
        order.setInboundType(request.getInboundType());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setSourceDesc(request.getSourceDesc());
        order.setStatus("COMPLETED");
        order.setCreatorName(operatorName);
        order.setCreateTime(LocalDateTime.now());
        order.setDeleted(0);
        orderMapper.insert(order);
        if (request.getLines() != null) {
            for (OtherInboundCreateRequest.LineItem item : request.getLines()) {
                OtherInboundLine line = new OtherInboundLine();
                line.setOrderNo(orderNo);
                line.setMaterialCode(item.getMaterialCode());
                line.setMaterialName(item.getMaterialName());
                line.setQuantity(item.getQuantity());
                line.setLocationCode(item.getLocationCode());
                line.setBatchNo(item.getBatchNo());
                lineMapper.insert(line);
                InventoryChangeCommand cmd = InventoryChangeCommand.builder()
                        .warehouseCode(request.getWarehouseCode())
                        .locationCode(item.getLocationCode())
                        .materialCode(item.getMaterialCode())
                        .batchNo(item.getBatchNo())
                        .quantity(item.getQuantity())
                        .transactionType("OTHER_IN")
                        .sourceOrderNo(orderNo)
                        .operatorName(operatorName)
                        .build();
                inventoryService.increase(cmd);
            }
        }
        auditTrailService.log("OTHER_INBOUND", orderNo, "CREATE", operatorName, request.getInboundType());
        return orderNo;
    }
}
