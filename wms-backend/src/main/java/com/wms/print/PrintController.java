package com.wms.print;

import com.wms.common.result.ApiResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "套打打印")
@RestController
@RequestMapping("/print")
@RequiredArgsConstructor
public class PrintController {

    private final PrintDataService printDataService;

    /**
     * 统一打印数据接口
     *
     * @param biz   业务类型，如 pick_issue、inbound_order
     * @param docNo 单据编号
     */
    @GetMapping("/data")
    public ApiResult<Map<String, Object>> printData(
            @RequestParam String biz,
            @RequestParam String docNo) {
        return ApiResult.ok(printDataService.build(biz, docNo));
    }
}
