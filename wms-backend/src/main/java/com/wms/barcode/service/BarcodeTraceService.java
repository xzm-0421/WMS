package com.wms.barcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.barcode.dto.BarcodeTraceResult;
import com.wms.barcode.entity.BarcodeInstance;
import com.wms.barcode.entity.BarcodeTraceLink;
import com.wms.barcode.mapper.BarcodeInstanceMapper;
import com.wms.barcode.mapper.BarcodeTraceLinkMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BarcodeTraceService {

    private final BarcodeInstanceMapper instanceMapper;
    private final BarcodeTraceLinkMapper traceLinkMapper;

    public BarcodeTraceResult trace(String barcodeContent) {
        if (!StringUtils.hasText(barcodeContent)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码不能为空");
        }
        String content = barcodeContent.trim();
        BarcodeInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<BarcodeInstance>()
                .eq(BarcodeInstance::getBarcodeContent, content));

        List<BarcodeTraceLink> links = traceLinkMapper.selectList(new LambdaQueryWrapper<BarcodeTraceLink>()
                .eq(BarcodeTraceLink::getBarcodeContent, content)
                .orderByDesc(BarcodeTraceLink::getCreateTime));

        List<BarcodeTraceResult.TraceLinkVo> linkVos = links.stream()
                .map(l -> BarcodeTraceResult.TraceLinkVo.builder()
                        .refType(l.getRefType())
                        .refNo(l.getRefNo())
                        .transactionNo(l.getTransactionNo())
                        .materialCode(l.getMaterialCode())
                        .batchNo(l.getBatchNo())
                        .serialNo(l.getSerialNo())
                        .remark(l.getRemark())
                        .createTime(l.getCreateTime())
                        .build())
                .toList();

        return BarcodeTraceResult.builder()
                .barcodeContent(content)
                .materialCode(instance != null ? instance.getMaterialCode() : firstOrNull(links, BarcodeTraceLink::getMaterialCode))
                .batchNo(instance != null ? instance.getBatchNo() : firstOrNull(links, BarcodeTraceLink::getBatchNo))
                .serialNo(instance != null ? instance.getSerialNo() : firstOrNull(links, BarcodeTraceLink::getSerialNo))
                .packBarcode(instance != null ? instance.getPackBarcode() : null)
                .ruleCode(instance != null ? instance.getRuleCode() : null)
                .versionNo(instance != null ? instance.getVersionNo() : null)
                .links(linkVos)
                .build();
    }

    private static String firstOrNull(List<BarcodeTraceLink> links,
                                      java.util.function.Function<BarcodeTraceLink, String> getter) {
        return links.stream().map(getter).filter(StringUtils::hasText).findFirst().orElse(null);
    }
}
