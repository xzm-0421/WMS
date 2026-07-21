package com.wms.barcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.entity.BarcodeSerialRegistry;
import com.wms.barcode.mapper.BarcodeSerialRegistryMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.util.OrderNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BarcodeSerialService {

    private final BarcodeSerialRegistryMapper serialMapper;

    /**
     * 分配全局唯一序列号；若传入序列号则校验唯一性后注册。
     */
    @Transactional
    public String allocateSerial(String requestedSerial, String materialCode, String batchNo, Long instanceId) {
        String serial = StringUtils.hasText(requestedSerial)
                ? requestedSerial.trim()
                : OrderNoGenerator.next("SN");
        if (exists(serial)) {
            if (StringUtils.hasText(requestedSerial)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "序列号「" + serial + "」已存在，不允许重复", "SERIAL_DUPLICATE");
            }
            serial = OrderNoGenerator.next("SN");
        }
        BarcodeSerialRegistry registry = new BarcodeSerialRegistry();
        registry.setSerialNo(serial);
        registry.setMaterialCode(materialCode);
        registry.setBatchNo(batchNo);
        registry.setBarcodeInstanceId(instanceId);
        registry.setStatus("AVAILABLE");
        try {
            serialMapper.insert(registry);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "序列号「" + serial + "」已存在，不允许重复", "SERIAL_DUPLICATE");
        }
        return serial;
    }

    @Transactional
    public void bindInstance(String serialNo, Long instanceId) {
        BarcodeSerialRegistry registry = serialMapper.selectOne(new LambdaQueryWrapper<BarcodeSerialRegistry>()
                .eq(BarcodeSerialRegistry::getSerialNo, serialNo));
        if (registry != null && instanceId != null) {
            registry.setBarcodeInstanceId(instanceId);
            serialMapper.updateById(registry);
        }
    }

    public boolean exists(String serialNo) {
        if (!StringUtils.hasText(serialNo)) {
            return false;
        }
        return serialMapper.selectCount(new LambdaQueryWrapper<BarcodeSerialRegistry>()
                .eq(BarcodeSerialRegistry::getSerialNo, serialNo.trim())) > 0;
    }

    @Transactional
    public void markUsed(String serialNo) {
        BarcodeSerialRegistry registry = serialMapper.selectOne(new LambdaQueryWrapper<BarcodeSerialRegistry>()
                .eq(BarcodeSerialRegistry::getSerialNo, serialNo));
        if (registry != null) {
            registry.setStatus("USED");
            serialMapper.updateById(registry);
        }
    }
}
