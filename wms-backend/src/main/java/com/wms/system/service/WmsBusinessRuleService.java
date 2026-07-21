package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.system.entity.WmsBusinessRule;
import com.wms.system.mapper.WmsBusinessRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WmsBusinessRuleService {

    private final WmsBusinessRuleMapper ruleMapper;

    public List<WmsBusinessRule> listAll() {
        return ruleMapper.selectList(new LambdaQueryWrapper<WmsBusinessRule>()
                .orderByAsc(WmsBusinessRule::getRuleCode));
    }

    public void updateValue(String ruleCode, String ruleValue) {
        WmsBusinessRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<WmsBusinessRule>()
                .eq(WmsBusinessRule::getRuleCode, ruleCode));
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "规则不存在");
        }
        rule.setRuleValue(ruleValue);
        ruleMapper.updateById(rule);
    }

    public BigDecimal getTolerance(String ruleCode, BigDecimal defaultVal) {
        WmsBusinessRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<WmsBusinessRule>()
                .eq(WmsBusinessRule::getRuleCode, ruleCode)
                .eq(WmsBusinessRule::getEnabled, 1));
        if (rule == null || rule.getRuleValue() == null) {
            return defaultVal;
        }
        try {
            return new BigDecimal(rule.getRuleValue());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public void validateOverReceive(BigDecimal orderQty, BigDecimal receivedQty, BigDecimal incomingQty) {
        BigDecimal tolerance = getTolerance("OVER_RECEIVE_TOLERANCE", BigDecimal.valueOf(0.05));
        BigDecimal maxAllowed = orderQty.multiply(BigDecimal.ONE.add(tolerance));
        BigDecimal total = (receivedQty != null ? receivedQty : BigDecimal.ZERO).add(incomingQty);
        if (total.compareTo(maxAllowed) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "收货数量超过订单允许上限（含容差）", "OVER_RECEIVE");
        }
    }

    public boolean isMixBatchForbidden() {
        WmsBusinessRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<WmsBusinessRule>()
                .eq(WmsBusinessRule::getRuleCode, "MIX_BATCH_FORBID")
                .eq(WmsBusinessRule::getEnabled, 1));
        return rule != null && "1".equals(rule.getRuleValue());
    }
}
