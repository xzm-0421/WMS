package com.wms.inventoryext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.service.InventoryService;
import com.wms.inventoryext.dto.OtherOutboundCreateRequest;
import com.wms.inventoryext.entity.OtherOutbound;
import com.wms.inventoryext.entity.OtherOutboundLine;
import com.wms.inventoryext.mapper.OtherOutboundLineMapper;
import com.wms.inventoryext.mapper.OtherOutboundMapper;
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
public class OtherOutboundService {

    private final OtherOutboundMapper orderMapper;
    private final OtherOutboundLineMapper lineMapper;
    private final InventoryService inventoryService;
    private final AuditTrailService auditTrailService;

    public PageResult<OtherOutbound> page(String orderNo, String outboundType, long current, long size) {
        LambdaQueryWrapper<OtherOutbound> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(orderNo), OtherOutbound::getOrderNo, orderNo)
                .eq(StringUtils.hasText(outboundType), OtherOutbound::getOutboundType, outboundType)
                .eq(OtherOutbound::getDeleted, 0)
                .orderByDesc(OtherOutbound::getCreateTime);
        Page<OtherOutbound> page = orderMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(String orderNo) {
        OtherOutbound order = orderMapper.selectOne(new LambdaQueryWrapper<OtherOutbound>()
                .eq(OtherOutbound::getOrderNo, orderNo));
        List<OtherOutboundLine> lines = lineMapper.selectList(new LambdaQueryWrapper<OtherOutboundLine>()
                .eq(OtherOutboundLine::getOrderNo, orderNo));
        Map<String, Object> data = new HashMap<>();
        data.put("order", order);
        data.put("lines", lines);
        return data;
    }

    @Transactional
    public String create(OtherOutboundCreateRequest request, String operatorName) {
        String orderNo = OrderNoGenerator.next("OO");
        OtherOutbound order = new OtherOutbound();
        order.setOrderNo(orderNo);
        order.setOutboundType(request.getOutboundType());
        order.setWarehouseCode(request.getWarehouseCode());
        order.setTargetDesc(request.getTargetDesc());
        order.setStatus("COMPLETED");
        order.setCreatorName(operatorName);
        order.setCreateTime(LocalDateTime.now());
        order.setDeleted(0);
        orderMapper.insert(order);
        if (request.getLines() != null) {
            for (OtherOutboundCreateRequest.LineItem item : request.getLines()) {
                OtherOutboundLine line = new OtherOutboundLine();
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
                        .transactionType("OTHER_OUT")
                        .sourceOrderNo(orderNo)
                        .operatorName(operatorName)
                        .build();
                inventoryService.decrease(cmd);
            }
        }
        auditTrailService.log("OTHER_OUTBOUND", orderNo, "CREATE", operatorName, request.getOutboundType());
        return orderNo;
    }
}
