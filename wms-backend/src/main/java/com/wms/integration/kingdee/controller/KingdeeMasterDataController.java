package com.wms.integration.kingdee.controller;

import com.wms.common.result.ApiResult;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeMasterDataSyncService;
import com.wms.integration.kingdee.KingdeeOpenApiTokenService;
import com.wms.integration.kingdee.dto.KingdeeMasterDataSyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "金蝶主数据同步")
@RestController
@RequestMapping("/integration/kingdee/master-data")
@RequiredArgsConstructor
public class KingdeeMasterDataController {

    private final KingdeeMasterDataSyncService masterDataSyncService;
    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeOpenApiTokenService openApiTokenService;

    @Operation(summary = "从金蝶同步物料主数据")
    @PostMapping("/sync/materials")
    public ApiResult<KingdeeMasterDataSyncResult> syncMaterials(
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(masterDataSyncService.syncMaterials(keyword));
    }

    @Operation(summary = "从金蝶同步仓库主数据")
    @PostMapping("/sync/warehouses")
    public ApiResult<KingdeeMasterDataSyncResult> syncWarehouses(
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(masterDataSyncService.syncWarehouses(keyword));
    }

    @Operation(summary = "从金蝶同步物料与仓库")
    @PostMapping("/sync/all")
    public ApiResult<Map<String, Object>> syncAll(
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(masterDataSyncService.syncAll(keyword));
    }

    @Operation(summary = "金蝶只读探测（验证认证与字段键，不写库）")
    @PostMapping("/probe")
    public ApiResult<List<List<String>>> probe(
            @RequestParam String formId,
            @RequestParam String fieldKeys,
            @RequestParam(required = false) String filterString) {
        return ApiResult.ok(kingdeeCloudService.executeBillQuery(
                formId, fieldKeys, filterString == null ? "" : filterString, "", 0, 1));
    }

    @Operation(summary = "校验当前金蝶 access_token（OAuth2 模式）")
    @GetMapping("/token/verify")
    public ApiResult<Map<String, Object>> verifyToken() {
        return ApiResult.ok(openApiTokenService.verifyCurrentToken());
    }
}
