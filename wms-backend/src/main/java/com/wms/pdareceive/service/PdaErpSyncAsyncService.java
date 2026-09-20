package com.wms.pdareceive.service;

import com.wms.integration.kingdee.KingdeePickMtrlRequest;
import com.wms.integration.kingdee.KingdeeReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubPickMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubReturnMtrlRequest;
import com.wms.noticebill.NoticeBillType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提交后异步同步金蝶（避免 PDA 阻塞在 Save/Submit/Audit）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdaErpSyncAsyncService {

    private final PdaReceiveSubmitBatchService submitBatchService;

    @Async("erpSyncExecutor")
    public void syncPurchaseInStock(String batchNo) {
        run(batchNo, NoticeBillType.PURCHASE_RECEIVE, () -> submitBatchService.syncBatchToErp(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncPrdInStock(String batchNo) {
        run(batchNo, NoticeBillType.PRODUCTION_IN, () -> submitBatchService.syncPrdInStockBatch(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncPrdRetStock(String batchNo) {
        run(batchNo, NoticeBillType.PRODUCTION_RET_STOCK, () -> submitBatchService.syncPrdRetStockBatch(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncReturnMtrl(String batchNo, List<KingdeeReturnMtrlRequest.Line> lines) {
        run(batchNo, NoticeBillType.PRODUCTION_RETURN,
                () -> submitBatchService.syncReturnMtrlBatch(batchNo, lines));
    }

    @Async("erpSyncExecutor")
    public void syncSubReturnMtrl(String batchNo, List<KingdeeSubReturnMtrlRequest.Line> lines) {
        run(batchNo, NoticeBillType.OUTSOURCE_RETURN,
                () -> submitBatchService.syncSubReturnMtrlBatch(batchNo, lines));
    }

    @Async("erpSyncExecutor")
    public void syncPickMtrl(String batchNo, List<KingdeePickMtrlRequest.Line> lines) {
        run(batchNo, NoticeBillType.PRODUCTION_ISSUE,
                () -> submitBatchService.syncPickMtrlBatch(batchNo, lines));
    }

    @Async("erpSyncExecutor")
    public void syncFeedMtrl(String batchNo) {
        run(batchNo, NoticeBillType.PRODUCTION_FEED,
                () -> submitBatchService.syncFeedMtrlBatch(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncSubFeedMtrl(String batchNo) {
        run(batchNo, NoticeBillType.OUTSOURCE_FEED,
                () -> submitBatchService.syncSubFeedMtrlBatch(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncSubPickMtrl(String batchNo, List<KingdeeSubPickMtrlRequest.Line> lines) {
        run(batchNo, NoticeBillType.OUTSOURCE_ISSUE,
                () -> submitBatchService.syncSubPickMtrlBatch(batchNo, lines));
    }

    @Async("erpSyncExecutor")
    public void syncSalReturnStock(String batchNo) {
        run(batchNo, NoticeBillType.SALES_RETURN, () -> submitBatchService.syncSalReturnStockBatch(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncAuditExistingBill(String batchNo) {
        run(batchNo, null, () -> submitBatchService.syncAuditExistingBillBatch(batchNo));
    }

    @Async("erpSyncExecutor")
    public void syncPurMrbActualQty(String batchNo) {
        run(batchNo, NoticeBillType.PURCHASE_RETURN, () -> submitBatchService.syncPurMrbActualQtyBatch(batchNo));
    }

    private void run(String batchNo, NoticeBillType billType, Runnable action) {
        String typeCode = billType != null ? billType.getCode() : "AUDIT_EXISTING";
        try {
            log.info("Async ERP sync start batchNo={} billType={}", batchNo, typeCode);
            action.run();
            log.info("Async ERP sync finished batchNo={} billType={}", batchNo, typeCode);
        } catch (Exception e) {
            log.error("Async ERP sync failed batchNo={} billType={}: {}",
                    batchNo, typeCode, e.getMessage(), e);
        }
    }
}
