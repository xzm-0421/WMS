package com.wms.mobile.service;

import com.wms.common.result.PageResult;
import com.wms.mes.dto.MesCancelRequest;
import com.wms.mes.dto.MesDefectCreateRequest;
import com.wms.mes.dto.MesReportContextVo;
import com.wms.mes.dto.MesReportSubmitRequest;
import com.wms.mes.dto.MesReworkSequenceVo;
import com.wms.mes.dto.MesSyncPanelVo;
import com.wms.mes.dto.MesTransferSubmitRequest;
import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesReport;
import com.wms.mes.entity.MesTransfer;
import com.wms.mes.service.MesReworkService;
import com.wms.mes.service.MesReportService;
import com.wms.mes.service.MesSyncWorkerService;
import com.wms.mobile.dto.MobileMesSyncRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 移动端 MES 现场作业门面：报工、工序转移、返工、同步。
 */
@Service
@RequiredArgsConstructor
public class MobileMesService {

    private final MesReportService reportService;
    private final MesReworkService reworkService;
    private final MesSyncWorkerService syncWorkerService;

    public MesReportContextVo context(String moNo) {
        return reportService.context(moNo);
    }

    public MesReport submitReport(MesReportSubmitRequest request) {
        return reportService.submit(request);
    }

    public PageResult<MesReport> pageReports(String reportNo, String moNo, String syncStatus, long current, long size) {
        return reportService.page(reportNo, moNo, syncStatus, true, true, current, size);
    }

    public MesReport reportDetail(String reportNo) {
        return reportService.getByNo(reportNo);
    }

    public void retryReport(String reportNo) {
        reportService.retryReport(reportNo);
    }

    public void cancelReport(String reportNo, MesCancelRequest request) {
        reportService.cancelReport(reportNo, request);
    }

    public MesTransfer submitTransfer(MesTransferSubmitRequest request) {
        return reportService.submitTransfer(request);
    }

    public PageResult<MesTransfer> pageTransfers(String transferNo, String moNo, String syncStatus, long current, long size) {
        return reportService.pageTransfer(transferNo, moNo, syncStatus, current, size);
    }

    public void retryTransfer(String transferNo) {
        reportService.retryTransfer(transferNo);
    }

    public MesReworkSequenceVo createDefect(MesDefectCreateRequest request) {
        return reworkService.create(request);
    }

    public PageResult<MesDefect> pageDefects(String moNo, String reworkStatus, long current, long size) {
        return reworkService.page(moNo, reworkStatus, current, size);
    }

    public MesReworkSequenceVo defectSequence(String defectNo) {
        return reworkService.sequence(defectNo);
    }

    public MesSyncPanelVo panel() {
        return syncWorkerService.panel();
    }

    /**
     * 离线批量补传报工：逐条提交，按 clientReportNo 幂等去重。
     */
    public Map<String, Object> syncReports(MobileMesSyncRequest request) {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int fail = 0;
        if (request != null && request.getItems() != null) {
            for (MobileMesSyncRequest.Item item : request.getItems()) {
                Map<String, Object> row = new HashMap<>();
                row.put("clientId", item.getClientId());
                try {
                    MesReportSubmitRequest submit = toSubmitRequest(item);
                    MesReport report = reportService.submit(submit);
                    row.put("success", true);
                    row.put("message", "同步成功");
                    row.put("reportNo", report.getReportNo());
                    success++;
                } catch (Exception e) {
                    row.put("success", false);
                    row.put("message", e.getMessage());
                    fail++;
                }
                results.add(row);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("totalSynced", results.size());
        data.put("successCount", success);
        data.put("failCount", fail);
        data.put("results", results);
        data.put("serverTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return data;
    }

    private MesReportSubmitRequest toSubmitRequest(MobileMesSyncRequest.Item item) {
        MesReportSubmitRequest submit = new MesReportSubmitRequest();
        submit.setMoNo(item.getMoNo());
        submit.setProcessCode(item.getProcessCode());
        submit.setReportType(item.getReportType());
        submit.setQty(item.getQty());
        submit.setWeightKg(item.getWeightKg());
        submit.setEquipmentCode(item.getEquipmentCode());
        submit.setDefectNo(item.getDefectNo());
        submit.setRemark(item.getRemark());
        submit.setClientTime(item.getClientTime());
        submit.setClientReportNo(StringUtils.hasText(item.getClientReportNo())
                ? item.getClientReportNo() : item.getClientId());
        return submit;
    }
}
