package com.wms.noticebill.config;

import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.noticebill.NoticeBillProvider;
import com.wms.noticebill.NoticeBillType;
import com.wms.noticebill.provider.ConfigurableNoticeBillProvider;
import com.wms.noticebill.provider.KingdeeConfigurableNoticeBillService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoticeBillProviderConfig {

    @Bean
    public NoticeBillProvider productionInNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                         KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.PRODUCTION_IN);
    }

    @Bean
    public NoticeBillProvider productionReturnNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                             KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.PRODUCTION_RETURN);
    }

    @Bean
    public NoticeBillProvider outsourceReturnNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                            KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.OUTSOURCE_RETURN);
    }

    @Bean
    public NoticeBillProvider otherInNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                    KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.OTHER_IN);
    }

    @Bean
    public NoticeBillProvider salesDeliveryNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                          KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.SALES_DELIVERY);
    }

    @Bean
    public NoticeBillProvider productionIssueNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                            KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.PRODUCTION_ISSUE);
    }

    @Bean
    public NoticeBillProvider outsourceIssueNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                           KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.OUTSOURCE_ISSUE);
    }

    @Bean
    public NoticeBillProvider otherOutNoticeProvider(KingdeeConfigurableNoticeBillService service,
                                                     KingdeeCloudService cloudService) {
        return new ConfigurableNoticeBillProvider(service, cloudService, NoticeBillType.OTHER_OUT);
    }
}
