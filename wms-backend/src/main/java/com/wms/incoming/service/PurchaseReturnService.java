package com.wms.incoming.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.incoming.dto.PurchaseReturnCreateRequest;
import com.wms.incoming.entity.PurchaseReturn;
import com.wms.incoming.entity.PurchaseReturnLine;
import com.wms.incoming.mapper.PurchaseReturnLineMapper;
import com.wms.incoming.mapper.PurchaseReturnMapper;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.service.InventoryService;
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
public class PurchaseReturnService {

    private final PurchaseReturnMapper returnMapper;
    private final PurchaseReturnLineMapper lineMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InventoryService inventoryService;
    private final AuditTrailService auditTrailService;

    public PageResult<PurchaseReturn> page(String returnNo, String status, long current, long size) {
        LambdaQueryWrapper<PurchaseReturn> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(returnNo), PurchaseReturn::getReturnNo, returnNo)
                .eq(StringUtils.hasText(status), PurchaseReturn::getStatus, status)
                .eq(PurchaseReturn::getDeleted, 0)
                .orderByDesc(PurchaseReturn::getCreateTime);
        Page<PurchaseReturn> page = returnMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public Map<String, Object> detail(String returnNo) {
        PurchaseReturn ret = getByNo(returnNo);
        List<PurchaseReturnLine> lines = lineMapper.selectList(new LambdaQueryWrapper<PurchaseReturnLine>()
                .eq(PurchaseReturnLine::getReturnNo, returnNo)
                .orderByAsc(PurchaseReturnLine::getLineNo));
        Map<String, Object> data = new HashMap<>();
        data.put("order", ret);
        data.put("lines", lines);
        return data;
    }

    @Transactional
    public String create(PurchaseReturnCreateRequest request, String operatorName) {
        String returnNo = OrderNoGenerator.next("PR");
        String warehouseCode = "WH001";
        if (StringUtils.hasText(request.getReceiptRefNo())) {
            InboundOrder inbound = inboundOrderMapper.selectOne(new LambdaQueryWrapper<InboundOrder>()
                    .eq(InboundOrder::getOrderNo, request.getReceiptRefNo())
                    .eq(InboundOrder::getDeleted, 0));
            if (inbound != null) {
                warehouseCode = inbound.getWarehouseCode();
            }
        }
        int lineNo = 1;
        if (request.getLines() != null) {
            for (PurchaseReturnCreateRequest.LineItem item : request.getLines()) {
                PurchaseReturnLine line = new PurchaseReturnLine();
                line.setReturnNo(returnNo);
                line.setLineNo(lineNo++);
                line.setMaterialCode(item.getMaterialCode());
                line.setReturnQty(item.getReturnQty());
                line.setBatchNo(item.getBatchNo());
                lineMapper.insert(line);
            }
        }
        PurchaseReturn ret = new PurchaseReturn();
        ret.setReturnNo(returnNo);
        ret.setReceiptRefNo(request.getReceiptRefNo());
        ret.setSupplierCode(request.getSupplierCode());
        ret.setReason(request.getReason());
        ret.setStatus("DRAFT");
        ret.setCreatorName(operatorName);
        ret.setCreateTime(LocalDateTime.now());
        ret.setDeleted(0);
        returnMapper.insert(ret);
        auditTrailService.log("PURCHASE_RETURN", returnNo, "CREATE", operatorName, request.getReason());
        return returnNo;
    }

    @Transactional
    public void confirm(String returnNo, String warehouseCode, String locationCode, String operatorName) {
        PurchaseReturn ret = getByNo(returnNo);
        List<PurchaseReturnLine> lines = lineMapper.selectList(new LambdaQueryWrapper<PurchaseReturnLine>()
                .eq(PurchaseReturnLine::getReturnNo, returnNo));
        for (PurchaseReturnLine line : lines) {
            InventoryChangeCommand cmd = InventoryChangeCommand.builder()
                    .warehouseCode(warehouseCode)
                    .locationCode(locationCode)
                    .materialCode(line.getMaterialCode())
                    .batchNo(line.getBatchNo())
                    .quantity(line.getReturnQty())
                    .transactionType("PURCHASE_RETURN")
                    .sourceOrderNo(returnNo)
                    .operatorName(operatorName)
                    .build();
            inventoryService.decrease(cmd);
        }
        ret.setStatus("COMPLETED");
        returnMapper.updateById(ret);
        auditTrailService.log("PURCHASE_RETURN", returnNo, "CONFIRM", operatorName, null);
    }

    private PurchaseReturn getByNo(String returnNo) {
        PurchaseReturn ret = returnMapper.selectOne(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getReturnNo, returnNo)
                .eq(PurchaseReturn::getDeleted, 0));
        if (ret == null) {
            throw new com.wms.common.exception.BusinessException(
                    com.wms.common.constant.ErrorCode.NOT_FOUND, "采购退货单不存在");
        }
        return ret;
    }
}
