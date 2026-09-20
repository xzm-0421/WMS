package com.wms.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue Router history 模式回退：无扩展名的前端路由转发到 index.html。
 * 使用分段 PathPattern（不可在 ** 后再写路径变量，PathPattern 会启动失败）。
 * 注意：本包不走 /api/v1 前缀（见 WebMvcConfig）。
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
            "/",
            "/{path:[^.]*}",
            "/{path:[^.]*}/{sub:[^.]*}",
            "/{path:[^.]*}/{sub:[^.]*}/{tail:[^.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
