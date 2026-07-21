package com.wms.mobile.controller;

import com.wms.common.result.ApiResult;
import com.wms.common.result.PageResult;
import com.wms.mobile.service.MobileMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "PDA-消息")
@RestController
@RequestMapping("/mobile/messages")
@RequiredArgsConstructor
public class MobileMessageController {

    private final MobileMessageService messageService;

    @Operation(summary = "消息列表")
    @GetMapping
    public ApiResult<PageResult<Map<String, Object>>> list(
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(messageService.listMessages(messageType, isRead, current, size));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public ApiResult<Map<String, Object>> unreadCount() {
        return ApiResult.ok(Map.of("count", messageService.unreadCount()));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{messageId}/read")
    public ApiResult<Void> markRead(@PathVariable Long messageId) {
        messageService.markRead(messageId);
        return ApiResult.ok("已标记", null);
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public ApiResult<Void> markAllRead() {
        messageService.markAllRead();
        return ApiResult.ok("全部已读", null);
    }
}
