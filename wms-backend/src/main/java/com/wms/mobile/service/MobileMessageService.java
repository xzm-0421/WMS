package com.wms.mobile.service;

import com.wms.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.mapper.OutboundOrderMapper;
import com.wms.stockcheck.entity.StockcheckTask;
import com.wms.stockcheck.mapper.StockcheckTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class MobileMessageService {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final StockcheckTaskMapper stockcheckTaskMapper;

    public PageResult<Map<String, Object>> listMessages(String messageType, Boolean isRead, long current, long size) {
        List<Map<String, Object>> all = buildTaskMessages();
        if (messageType != null && !messageType.isBlank()) {
            all = all.stream().filter(m -> messageType.equalsIgnoreCase(String.valueOf(m.get("messageType")))).toList();
        }
        if (isRead != null) {
            all = all.stream().filter(m -> isRead.equals(m.get("isRead"))).toList();
        }
        long total = all.size();
        int from = (int) Math.max(0, (current - 1) * size);
        int to = (int) Math.min(all.size(), from + size);
        List<Map<String, Object>> page = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResult.of(page, total, current, size);
    }

    public long unreadCount() {
        return buildTaskMessages().stream().filter(m -> Boolean.FALSE.equals(m.get("isRead"))).count();
    }

    public void markRead(Long messageId) {
        // 任务消息为动态生成，标记已读存客户端即可
    }

    public void markAllRead() {
        // 任务消息为动态生成，标记已读存客户端即可
    }

    private List<Map<String, Object>> buildTaskMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        long pendingInbound = inboundOrderMapper.selectCount(new LambdaQueryWrapper<InboundOrder>()
                .in(InboundOrder::getStatus, "PENDING", "INBOUND").eq(InboundOrder::getDeleted, 0));
        long pendingOutbound = outboundOrderMapper.selectCount(new LambdaQueryWrapper<OutboundOrder>()
                .in(OutboundOrder::getStatus, "PENDING", "PICKING", "OUTBOUND").eq(OutboundOrder::getDeleted, 0));
        long pendingStockcheck = stockcheckTaskMapper.selectCount(new LambdaQueryWrapper<StockcheckTask>()
                .eq(StockcheckTask::getStatus, "PENDING"));

        if (pendingInbound > 0) {
            messages.add(buildMessage("TASK", "待入库任务", "您有 " + pendingInbound + " 个入库任务待处理", false));
        }
        if (pendingOutbound > 0) {
            messages.add(buildMessage("TASK", "待出库任务", "您有 " + pendingOutbound + " 个出库任务待处理", false));
        }
        if (pendingStockcheck > 0) {
            messages.add(buildMessage("TASK", "待盘点任务", "您有 " + pendingStockcheck + " 个盘点任务待处理", false));
        }
        messages.add(buildMessage("SYSTEM", "系统通知", "WMS PDA 移动端已连接", true));
        return messages;
    }

    private Map<String, Object> buildMessage(String type, String title, String content, boolean read) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("messageId", ID_SEQ.getAndIncrement());
        msg.put("messageType", type);
        msg.put("title", title);
        msg.put("content", content);
        msg.put("isRead", read);
        msg.put("createTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return msg;
    }
}
