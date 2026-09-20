package com.wms.mes.controller;

import com.wms.base.entity.BaseMaterial;
import com.wms.base.service.BaseMaterialService;
import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.integration.kingdee.KingdeeBomService;
import com.wms.mes.dto.MesRouteVo;
import com.wms.mes.dto.MesSyncResult;
import com.wms.mes.entity.MesEquipment;
import com.wms.mes.entity.MesProcess;
import com.wms.mes.entity.MesRoute;
import com.wms.mes.service.MesKingdeePullService;
import com.wms.mes.service.MesMasterDataService;
import com.wms.production.dto.BomVo;
import com.wms.production.entity.BomHeader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "轻MES-基础资料")
@RestController
@RequestMapping("/mes/master")
@RequiredArgsConstructor
public class MesMasterDataController {

    private final MesMasterDataService masterDataService;
    private final MesKingdeePullService pullService;
    private final BaseMaterialService materialService;
    private final KingdeeBomService kingdeeBomService;

    @Operation(summary = "物料列表（本地，来源金蝶 BD_MATERIAL）")
    @GetMapping("/materials")
    public ApiResult<PageResult<BaseMaterial>> materials(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(materialService.page(materialCode, materialName, null, status, current, size));
    }

    @Operation(summary = "物料详情")
    @GetMapping("/materials/{materialCode}")
    public ApiResult<BaseMaterial> materialDetail(@PathVariable String materialCode) {
        return ApiResult.ok(materialService.getByCode(materialCode));
    }

    @Operation(summary = "从金蝶同步物料到本地")
    @PostMapping("/materials/refresh")
    public ApiResult<MesSyncResult> refreshMaterials() {
        return ApiResult.ok(pullService.syncMaterials());
    }

    @Operation(summary = "BOM列表（本地，来源金蝶 ENG_BOM）")
    @GetMapping("/boms")
    public ApiResult<PageResult<BomHeader>> boms(
            @RequestParam(required = false) String bomCode,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(kingdeeBomService.pageLocal(bomCode, productCode, current, size));
    }

    @Operation(summary = "BOM详情")
    @GetMapping("/boms/{bomCode}")
    public ApiResult<BomVo> bomDetail(@PathVariable String bomCode) {
        return ApiResult.ok(kingdeeBomService.getLocal(bomCode));
    }

    @Operation(summary = "从金蝶同步 BOM 到本地")
    @PostMapping("/boms/refresh")
    public ApiResult<MesSyncResult> refreshBoms() {
        return ApiResult.ok(pullService.syncBoms());
    }

    @Operation(summary = "工序列表")
    @GetMapping("/processes")
    public ApiResult<PageResult<MesProcess>> processes(
            @RequestParam(required = false) String processCode,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(masterDataService.pageProcess(processCode, processName, status, current, size));
    }

    @Operation(summary = "可选工序列表（启用）")
    @GetMapping("/processes/options")
    public ApiResult<List<MesProcess>> processOptions() {
        return ApiResult.ok(masterDataService.listActiveProcesses());
    }

    @Operation(summary = "工序详情")
    @GetMapping("/processes/{processCode}")
    public ApiResult<MesProcess> processDetail(@PathVariable String processCode) {
        return ApiResult.ok(masterDataService.getProcess(processCode));
    }

    @Operation(summary = "设备列表")
    @GetMapping("/equipment")
    public ApiResult<PageResult<MesEquipment>> equipment(
            @RequestParam(required = false) String equipmentCode,
            @RequestParam(required = false) String equipmentName,
            @RequestParam(required = false) String processCode,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(masterDataService.pageEquipment(
                equipmentCode, equipmentName, processCode, status, current, size));
    }

    @Operation(summary = "可选设备")
    @GetMapping("/equipment/options")
    public ApiResult<List<MesEquipment>> equipmentOptions(@RequestParam(required = false) String processCode) {
        return ApiResult.ok(masterDataService.listActiveEquipment(processCode));
    }

    @Operation(summary = "设备详情")
    @GetMapping("/equipment/{equipmentCode}")
    public ApiResult<MesEquipment> equipmentDetail(@PathVariable String equipmentCode) {
        return ApiResult.ok(masterDataService.getEquipment(equipmentCode));
    }

    @Operation(summary = "工艺路线列表")
    @GetMapping("/routes")
    public ApiResult<PageResult<MesRoute>> routes(
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(masterDataService.pageRoute(productCode, productName, current, size));
    }

    @Operation(summary = "工艺路线详情")
    @GetMapping("/routes/{id}")
    public ApiResult<MesRouteVo> routeDetail(@PathVariable Long id) {
        return ApiResult.ok(masterDataService.getRoute(id));
    }

    @Operation(summary = "手动刷新全部基础资料")
    @PostMapping("/refresh")
    public ApiResult<List<MesSyncResult>> refresh() {
        return ApiResult.ok("刷新完成", pullService.syncMasterData());
    }

    @Operation(summary = "从金蝶同步工序")
    @PostMapping("/processes/refresh")
    public ApiResult<MesSyncResult> refreshProcesses() {
        return ApiResult.ok(pullService.syncProcesses());
    }

    @Operation(summary = "从金蝶同步设备到本地")
    @PostMapping("/equipment/refresh")
    public ApiResult<MesSyncResult> refreshEquipment() {
        return ApiResult.ok(pullService.syncEquipment());
    }

    @Operation(summary = "从金蝶同步工艺路线")
    @PostMapping("/routes/refresh")
    public ApiResult<MesSyncResult> refreshRoutes() {
        return ApiResult.ok(pullService.syncRoutes());
    }
}
