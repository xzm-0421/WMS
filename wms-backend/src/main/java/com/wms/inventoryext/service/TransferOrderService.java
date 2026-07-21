package com.wms.inventoryext.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.common.util.OrderNoGenerator;
import com.wms.base.entity.BaseLocation;
import com.wms.base.mapper.BaseLocationMapper;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.service.InventoryService;
import com.wms.inventoryext.dto.TransferOrderCreateRequest;
import com.wms.inventoryext.entity.TransferOrder;
import com.wms.inventoryext.mapper.TransferOrderMapper;
import com.wms.system.service.AuditTrailService;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.mobile.dto.MobileTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransferOrderService {

    private final TransferOrderMapper transferMapper;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final BaseLocationMapper locationMapper;
    private final AuditTrailService auditTrailService;

    public PageResult<TransferOrder> page(String transferNo, String status, long current, long size) {
        LambdaQueryWrapper<TransferOrder> w = new LambdaQueryWrapper<>();
        w.like(StringUtils.hasText(transferNo), TransferOrder::getTransferNo, transferNo)
                .eq(StringUtils.hasText(status), TransferOrder::getStatus, status)
                .eq(TransferOrder::getDeleted, 0)
                .orderByDesc(TransferOrder::getCreateTime);
        Page<TransferOrder> page = transferMapper.selectPage(new Page<>(current, size), w);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Transactional
    public String create(TransferOrderCreateRequest request, String operatorName) {
        String transferNo = OrderNoGenerator.next("YK");
        TransferOrder order = new TransferOrder();
        order.setTransferNo(transferNo);
        order.setTransferType(StringUtils.hasText(request.getTransferType()) ? request.getTransferType() : "INTERNAL");
        order.setSourceWarehouse(request.getSourceWarehouse());
        order.setSourceLocation(request.getSourceLocation());
        order.setTargetWarehouse(request.getTargetWarehouse());
        order.setTargetLocation(request.getTargetLocation());
        order.setMaterialCode(request.getMaterialCode());
        order.setBatchNo(request.getBatchNo());
        order.setTransferQty(request.getTransferQty());
        order.setStatus("PENDING");
        order.setCreatorName(operatorName);
        order.setRemark(request.getRemark());
        order.setCreateTime(LocalDateTime.now());
        order.setDeleted(0);
        transferMapper.insert(order);
        auditTrailService.log("TRANSFER", transferNo, "CREATE", operatorName, null);
        return transferNo;
    }

    @Transactional
    public void approve(String transferNo, String operatorName) {
        TransferOrder order = getByNo(transferNo);
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审批状态可审批");
        }
        order.setStatus("EXECUTING");
        transferMapper.updateById(order);
        auditTrailService.log("TRANSFER", transferNo, "APPROVE", operatorName, null);
    }

    @Transactional
    public void execute(String transferNo, String operatorName) {
        TransferOrder order = getByNo(transferNo);
        if (!"EXECUTING".equals(order.getStatus()) && !"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可执行");
        }
        InventoryChangeCommand out = InventoryChangeCommand.builder()
                .warehouseCode(order.getSourceWarehouse())
                .locationCode(order.getSourceLocation())
                .materialCode(order.getMaterialCode())
                .batchNo(order.getBatchNo())
                .quantity(order.getTransferQty())
                .transactionType("TRANSFER_OUT")
                .sourceOrderNo(transferNo)
                .operatorName(operatorName)
                .build();
        inventoryService.decrease(out);
        InventoryChangeCommand in = InventoryChangeCommand.builder()
                .warehouseCode(order.getTargetWarehouse())
                .locationCode(order.getTargetLocation())
                .materialCode(order.getMaterialCode())
                .batchNo(order.getBatchNo())
                .quantity(order.getTransferQty())
                .transactionType("TRANSFER_IN")
                .sourceOrderNo(transferNo)
                .operatorName(operatorName)
                .build();
        inventoryService.increase(in);
        order.setStatus("COMPLETED");
        order.setOperatorId(operatorName);
        order.setOperationTime(LocalDateTime.now());
        transferMapper.updateById(order);
        auditTrailService.log("TRANSFER", transferNo, "EXECUTE", operatorName, null);
    }

    @Transactional
    public void cancel(String transferNo, String operatorName) {
        TransferOrder order = getByNo(transferNo);
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已完成调拨不可取消");
        }
        order.setStatus("CANCELLED");
        transferMapper.updateById(order);
        auditTrailService.log("TRANSFER", transferNo, "CANCEL", operatorName, null);
    }

    /**
     * PDA 即时移库：创建并完成调拨单，与 Web 调拨共用 transfer_order 与库存流水。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createAndExecuteFromPda(MobileTransferRequest req, String operatorName) {
        if (!StringUtils.hasText(req.getSourceLocation())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源库位不能为空", "SOURCE_LOCATION_REQUIRED");
        }
        if (!StringUtils.hasText(req.getTargetLocation())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标库位不能为空", "TARGET_LOCATION_REQUIRED");
        }
        if (!StringUtils.hasText(req.getMaterialCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "物料编码不能为空", "MATERIAL_REQUIRED");
        }
        if (req.getTransferQty() == null || req.getTransferQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "移库数量必须大于0", "QTY_INVALID");
        }
        String sourceLocation = req.getSourceLocation().trim();
        String targetLocation = req.getTargetLocation().trim();
        if (sourceLocation.equalsIgnoreCase(targetLocation)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "源库位与目标库位不能相同", "SAME_LOCATION");
        }
        requireLocation(sourceLocation, "源库位不存在: " + sourceLocation, "SOURCE_LOCATION_NOT_FOUND");
        requireLocation(targetLocation, "目标库位无效: " + targetLocation, "TARGET_LOCATION_INVALID");

        String batchNo = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo().trim() : "";
        Inventory source = inventoryMapper.selectOne(new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getLocationCode, sourceLocation)
                .eq(Inventory::getMaterialCode, req.getMaterialCode().trim())
                .eq(StringUtils.hasText(batchNo), Inventory::getBatchNo, batchNo)
                .gt(Inventory::getStockQty, 0)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (source == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "源库位库存不存在", "SOURCE_STOCK_NOT_FOUND");
        }
        BigDecimal available = source.getAvailableQty() != null ? source.getAvailableQty() : source.getStockQty();
        if (available == null || available.compareTo(req.getTransferQty()) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "移库数量超过可用库存（可用 " + (available == null ? "0" : available) + "）",
                    "QTY_EXCEED_AVAILABLE");
        }
        String warehouseCode = source.getWarehouseCode();
        String transferNo = OrderNoGenerator.next("YK");
        TransferOrder order = new TransferOrder();
        order.setTransferNo(transferNo);
        order.setTransferType("PDA");
        order.setSourceWarehouse(warehouseCode);
        order.setSourceLocation(sourceLocation);
        order.setTargetWarehouse(warehouseCode);
        order.setTargetLocation(targetLocation);
        order.setMaterialCode(req.getMaterialCode().trim());
        order.setBatchNo(batchNo);
        order.setTransferQty(req.getTransferQty());
        order.setStatus("PENDING");
        order.setCreatorName(operatorName);
        order.setRemark("PDA移库");
        order.setCreateTime(LocalDateTime.now());
        order.setDeleted(0);
        transferMapper.insert(order);
        execute(transferNo, operatorName);
        auditTrailService.log("TRANSFER", transferNo, "PDA_EXECUTE", operatorName, req.getDeviceNo());

        Map<String, Object> data = new HashMap<>();
        data.put("transferNo", transferNo);
        data.put("sourceLocation", sourceLocation);
        data.put("targetLocation", targetLocation);
        data.put("materialCode", req.getMaterialCode().trim());
        data.put("batchNo", batchNo);
        data.put("transferQty", req.getTransferQty());
        return data;
    }

    private void requireLocation(String locationCode, String message, String errorType) {
        BaseLocation location = locationMapper.selectOne(new LambdaQueryWrapper<BaseLocation>()
                .eq(BaseLocation::getLocationCode, locationCode)
                .eq(BaseLocation::getDeleted, 0)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
        if (location == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, message, errorType);
        }
        if (location.getStatus() != null && location.getStatus() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库位已停用: " + locationCode, errorType);
        }
    }

    private TransferOrder getByNo(String transferNo) {
        TransferOrder order = transferMapper.selectOne(new LambdaQueryWrapper<TransferOrder>()
                .eq(TransferOrder::getTransferNo, transferNo)
                .eq(TransferOrder::getDeleted, 0));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "调拨单不存在");
        }
        return order;
    }
}
