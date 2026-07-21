package com.wms.noticebill;

import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NoticeBillProviderRegistry {

    private final Map<NoticeBillType, NoticeBillProvider> providers = new EnumMap<>(NoticeBillType.class);

    public NoticeBillProviderRegistry(List<NoticeBillProvider> providerList) {
        for (NoticeBillProvider provider : providerList) {
            providers.put(provider.type(), provider);
        }
    }

    public NoticeBillProvider require(NoticeBillType type) {
        NoticeBillProvider provider = providers.get(type);
        if (provider == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "未注册单据类型适配器: " + type.getCode());
        }
        return provider;
    }

    public NoticeBillProvider require(String typeCode) {
        return require(NoticeBillType.fromCode(typeCode));
    }
}
