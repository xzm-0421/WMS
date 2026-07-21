package com.wms.system.controller;

import com.wms.common.result.ApiResult;
import com.wms.system.entity.WmsBusinessRule;
import com.wms.system.service.WmsBusinessRuleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统规则")
@RestController
@RequestMapping("/system/rules")
@RequiredArgsConstructor
public class WmsBusinessRuleController {

    private final WmsBusinessRuleService ruleService;

    @GetMapping
    public ApiResult<List<WmsBusinessRule>> list() {
        return ApiResult.ok(ruleService.listAll());
    }

    @PutMapping("/{ruleCode}")
    public ApiResult<Void> update(@PathVariable String ruleCode, @RequestBody Map<String, String> body) {
        ruleService.updateValue(ruleCode, body.get("ruleValue"));
        return ApiResult.ok("更新成功", null);
    }
}
