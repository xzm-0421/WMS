package com.wms.barcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.entity.BarcodeInstance;
import com.wms.barcode.mapper.BarcodeInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 扫码业务与条码追溯链关联。
 */
@Service
@RequiredArgsConstructor
public class BarcodeTraceLinkService {

    private final BarcodeInstanceMapper instanceMapper;
    private final BarcodeGenerateService generateService;

    public void linkInboundReceive(String barcodeContent, String orderNo, String transactionNo,
                                    String materialCode, String batchNo, String serialNo) {
        link(barcodeContent, "INBOUND", orderNo, transactionNo, materialCode, batchNo, serialNo,
                "入库单扫码收货");
    }

    public void linkPdaInbound(String barcodeContent, String recordNo, String transactionNo,
                                String materialCode, String batchNo, String serialNo) {
        link(barcodeContent, "PDA_INBOUND", recordNo, transactionNo, materialCode, batchNo, serialNo,
                "PDA快速入库");
    }

    public void linkOutboundIssue(String barcodeContent, String orderNo, String transactionNo,
                                   String materialCode, String batchNo, String serialNo) {
        link(barcodeContent, "OUTBOUND", orderNo, transactionNo, materialCode, batchNo, serialNo,
                "出库单扫码发料");
    }

    private void link(String barcodeContent, String refType, String refNo, String transactionNo,
                      String materialCode, String batchNo, String serialNo, String remark) {
        if (!StringUtils.hasText(barcodeContent)) {
            return;
        }
        Long instanceId = resolveInstanceId(barcodeContent.trim());
        generateService.linkTrace(barcodeContent.trim(), instanceId, refType, refNo, transactionNo,
                materialCode, batchNo, serialNo, remark);
    }

    private Long resolveInstanceId(String barcodeContent) {
        BarcodeInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<BarcodeInstance>()
                .eq(BarcodeInstance::getBarcodeContent, barcodeContent));
        return instance != null ? instance.getId() : null;
    }
}
