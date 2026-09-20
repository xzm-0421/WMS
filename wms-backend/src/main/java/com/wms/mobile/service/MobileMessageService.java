package com.wms.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wms.auth.security.LoginUser;
import com.wms.common.result.PageResult;
import com.wms.common.security.SecurityUtils;
import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.mapper.InboundOrderMapper;
import com.wms.mobile.entity.PdaUserMessage;
import com.wms.mobile.mapper.PdaUserMessageMapper;
import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.mapper.OutboundOrderMapper;
import com.wms.stockcheck.entity.StockcheckTask;
import com.wms.stockcheck.mapper.StockcheckTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MobileMessageService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PdaUserMessageMapper messageMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final StockcheckTaskMapper stockcheckTaskMapper;

    public PageResult<Map<String, Object>> listMessages(String messageType, Boolean isRead, long current, long size) {
        List<Map<String, Object>> all = new ArrayList<>();
        all.addAll(listPersistedMessages());
        all.addAll(buildTaskMessages());
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
        LoginUser user = SecurityUtils.currentUser();
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<PdaUserMessage>()
                .eq(PdaUserMessage::getUserId, String.valueOf(user.getUserId()))
                .eq(PdaUserMessage::getIsRead, 0));
        return count == null ? 0 : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long messageId) {
        if (messageId == null || messageId <= 0) {
            return;
        }
        LoginUser user = SecurityUtils.currentUser();
        messageMapper.update(null, new LambdaUpdateWrapper<PdaUserMessage>()
                .eq(PdaUserMessage::getId, messageId)
                .eq(PdaUserMessage::getUserId, String.valueOf(user.getUserId()))
                .set(PdaUserMessage::getIsRead, 1)
                .set(PdaUserMessage::getUpdateTime, LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllRead() {
        LoginUser user = SecurityUtils.currentUser();
        messageMapper.update(null, new LambdaUpdateWrapper<PdaUserMessage>()
                .eq(PdaUserMessage::getUserId, String.valueOf(user.getUserId()))
                .eq(PdaUserMessage::getIsRead, 0)
                .set(PdaUserMessage::getIsRead, 1)
                .set(PdaUserMessage::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 向指定 WMS 用户推送 PDA 待办消息。
     */
    @Transactional(rollbackFor = Exception.class)
    public void pushBizMessage(String wmsUserId, String title, String content, String bizType, String bizNo) {
        if (!StringUtils.hasText(wmsUserId) || !StringUtils.hasText(title)) {
            return;
        }
        PdaUserMessage message = new PdaUserMessage();
        message.setUserId(wmsUserId.trim());
        message.setMessageType("TASK");
        message.setTitle(title.trim());
        message.setContent(content);
        message.setBizType(bizType);
        message.setBizNo(bizNo);
        message.setIsRead(0);
        messageMapper.insert(message);
    }

    private List<Map<String, Object>> listPersistedMessages() {
        LoginUser user = SecurityUtils.currentUser();
        List<PdaUserMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<PdaUserMessage>()
                .eq(PdaUserMessage::getUserId, String.valueOf(user.getUserId()))
                .orderByDesc(PdaUserMessage::getCreateTime)
                .last("OFFSET 0 ROWS FETCH NEXT 100 ROWS ONLY"));
        List<Map<String, Object>> list = new ArrayList<>();
        for (PdaUserMessage row : rows) {
            if (row == null) {
                continue;
            }
            Map<String, Object> msg = new HashMap<>();
            msg.put("messageId", row.getId());
            msg.put("messageType", row.getMessageType());
            msg.put("title", row.getTitle());
            msg.put("content", row.getContent());
            msg.put("bizType", row.getBizType());
            msg.put("bizNo", row.getBizNo());
            msg.put("isRead", row.getIsRead() != null && row.getIsRead() == 1);
            msg.put("createTime", row.getCreateTime() == null ? "" : row.getCreateTime().format(TIME_FMT));
            list.add(msg);
        }
        return list;
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
            messages.add(buildMessage("TASK", "待入库任务", "您有 " + pendingInbound + " 个入库任务待处理", true));
        }
        if (pendingOutbound > 0) {
            messages.add(buildMessage("TASK", "待出库任务", "您有 " + pendingOutbound + " 个出库任务待处理", true));
        }
        if (pendingStockcheck > 0) {
            messages.add(buildMessage("TASK", "待盘点任务", "您有 " + pendingStockcheck + " 个盘点任务待处理", true));
        }
        return messages;
    }

    private Map<String, Object> buildMessage(String type, String title, String content, boolean read) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("messageId", 0);
        msg.put("messageType", type);
        msg.put("title", title);
        msg.put("content", content);
        msg.put("isRead", read);
        msg.put("createTime", LocalDateTime.now().format(TIME_FMT));
        return msg;
    }
}
