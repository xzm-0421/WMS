package com.wms.pdabilllock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.auth.security.LoginUser;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.security.SecurityUtils;
import com.wms.pdabilllock.dto.PdaBillLockVo;
import com.wms.pdabilllock.entity.PdaBillLock;
import com.wms.pdabilllock.mapper.PdaBillLockMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PDA 单据排他锁：同一单据同一时间仅允许一个用户操作。
 * 超时未续租可被他人抢占；同用户可续租。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdaBillLockService {

    /** 锁租约时长（分钟） */
    public static final int LOCK_TTL_MINUTES = 3;

    private final PdaBillLockMapper lockMapper;

    /**
     * 进入单据详情时占用；若已被他人占用且未过期则抛出 BILL_LOCKED。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PdaBillLockVo acquire(String billType, String billNo) {
        return touch(billType, billNo, true);
    }

    /**
     * 扫码/改数量/提交前校验并续租。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PdaBillLockVo assertHeld(String billType, String billNo) {
        return touch(billType, billNo, true);
    }

    /**
     * 心跳续租（仅持锁人可续）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public PdaBillLockVo heartbeat(String billType, String billNo) {
        return touch(billType, billNo, true);
    }

    /**
     * 主动释放（离开页面）；仅持锁人可释放。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void release(String billType, String billNo) {
        String type = normalizeType(billType);
        String no = normalizeBillNo(billNo);
        LoginUser user = SecurityUtils.currentUser();
        String uid = String.valueOf(user.getUserId());
        PdaBillLock lock = findLock(type, no);
        if (lock == null) {
            return;
        }
        if (!uid.equals(lock.getLockUserId())) {
            // 已被他人占用或已超时被抢，忽略释放
            return;
        }
        lockMapper.deleteById(lock.getId());
        log.info("PDA单据锁已释放 billType={} billNo={} userId={}", type, no, uid);
    }

    /**
     * 单据完成等场景强制释放，不校验当前用户。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void forceRelease(String billType, String billNo) {
        String type = normalizeType(billType);
        String no = normalizeBillNo(billNo);
        PdaBillLock lock = findLock(type, no);
        if (lock != null) {
            lockMapper.deleteById(lock.getId());
            log.info("PDA单据锁强制释放 billType={} billNo={}", type, no);
        }
    }

    /**
     * 批量查询未过期锁（列表展示占用者）。
     */
    public Map<String, PdaBillLock> findActiveLocks(String billType, Collection<String> billNos) {
        if (billNos == null || billNos.isEmpty()) {
            return Collections.emptyMap();
        }
        String type = normalizeType(billType);
        List<String> nos = billNos.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (nos.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime now = LocalDateTime.now();
        List<PdaBillLock> locks = lockMapper.selectList(new LambdaQueryWrapper<PdaBillLock>()
                .eq(PdaBillLock::getBillType, type)
                .in(PdaBillLock::getBillNo, nos)
                .gt(PdaBillLock::getLockExpireTime, now));
        Map<String, PdaBillLock> map = new HashMap<>();
        for (PdaBillLock lock : locks) {
            if (lock != null && StringUtils.hasText(lock.getBillNo())) {
                map.put(lock.getBillNo().trim(), lock);
            }
        }
        return map;
    }

    private PdaBillLockVo touch(String billType, String billNo, boolean renew) {
        String type = normalizeType(billType);
        String no = normalizeBillNo(billNo);
        LoginUser user = SecurityUtils.currentUser();
        String uid = String.valueOf(user.getUserId());
        String userName = StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername();
        String deviceNo = resolveDeviceNo();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.plusMinutes(LOCK_TTL_MINUTES);

        PdaBillLock lock = findLock(type, no);
        if (lock == null) {
            lock = new PdaBillLock();
            lock.setBillType(type);
            lock.setBillNo(no);
            lock.setLockUserId(uid);
            lock.setLockUserName(userName);
            lock.setLockDeviceNo(deviceNo);
            lock.setLockExpireTime(expire);
            lock.setCreateTime(now);
            lock.setUpdateTime(now);
            try {
                lockMapper.insert(lock);
                log.info("PDA单据锁已占用 billType={} billNo={} userId={} userName={}",
                        type, no, uid, userName);
                return toVo(lock, true);
            } catch (DataIntegrityViolationException ex) {
                // 并发抢占：重新加载后按占用规则处理
                lock = findLock(type, no);
                if (lock == null) {
                    throw new BusinessException(ErrorCode.CONFLICT, "单据占用冲突，请重试", "BILL_LOCKED");
                }
            }
        }

        boolean expired = lock.getLockExpireTime() == null || !lock.getLockExpireTime().isAfter(now);
        boolean owned = uid.equals(lock.getLockUserId());
        if (!owned && !expired) {
            throwLocked(lock);
        }

        String prevUserId = lock.getLockUserId();
        // 本人续租，或超时后抢占
        lock.setLockUserId(uid);
        lock.setLockUserName(userName);
        if (StringUtils.hasText(deviceNo)) {
            lock.setLockDeviceNo(deviceNo);
        }
        if (renew || expired || owned) {
            lock.setLockExpireTime(expire);
        }
        lock.setUpdateTime(now);
        lockMapper.updateById(lock);
        if (!owned && expired) {
            log.info("PDA单据锁超时抢占 billType={} billNo={} userId={} prevUserId={}",
                    type, no, uid, prevUserId);
        }
        return toVo(lock, true);
    }

    private void throwLocked(PdaBillLock lock) {
        String holder = StringUtils.hasText(lock.getLockUserName())
                ? lock.getLockUserName()
                : lock.getLockUserId();
        Map<String, Object> details = new HashMap<>();
        details.put("lockUserId", lock.getLockUserId());
        details.put("lockUserName", lock.getLockUserName());
        details.put("lockExpireTime", lock.getLockExpireTime());
        throw new BusinessException(ErrorCode.CONFLICT,
                "单据正由【" + holder + "】操作中，请稍后再试",
                "BILL_LOCKED",
                details);
    }

    private PdaBillLock findLock(String billType, String billNo) {
        return lockMapper.selectOne(new LambdaQueryWrapper<PdaBillLock>()
                .eq(PdaBillLock::getBillType, billType)
                .eq(PdaBillLock::getBillNo, billNo));
    }

    private static String normalizeType(String billType) {
        if (!StringUtils.hasText(billType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单据类型不能为空");
        }
        return billType.trim().toUpperCase();
    }

    private static String normalizeBillNo(String billNo) {
        if (!StringUtils.hasText(billNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单据号不能为空");
        }
        return billNo.trim();
    }

    private static String resolveDeviceNo() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String header = request.getHeader("X-Device-ID");
                return StringUtils.hasText(header) ? header.trim() : null;
            }
        } catch (Exception ignored) {
            // 非 Web 请求场景忽略
        }
        return null;
    }

    private static PdaBillLockVo toVo(PdaBillLock lock, boolean ownedByMe) {
        PdaBillLockVo vo = new PdaBillLockVo();
        vo.setBillType(lock.getBillType());
        vo.setBillNo(lock.getBillNo());
        vo.setLockUserId(lock.getLockUserId());
        vo.setLockUserName(lock.getLockUserName());
        vo.setLockDeviceNo(lock.getLockDeviceNo());
        vo.setLockExpireTime(lock.getLockExpireTime());
        vo.setOwnedByMe(ownedByMe);
        return vo;
    }
}
