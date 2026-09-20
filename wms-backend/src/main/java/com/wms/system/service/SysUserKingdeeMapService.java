package com.wms.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.system.entity.SysUserKingdeeMap;
import com.wms.system.mapper.SysUserKingdeeMapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * WMS 用户与金蝶用户映射（由用户管理维护，不再单独提供映射菜单）。
 */
@Service
@RequiredArgsConstructor
public class SysUserKingdeeMapService {

    private final SysUserKingdeeMapMapper mapMapper;

    public SysUserKingdeeMap findByExternalUserId(String externalUserId) {
        if (!StringUtils.hasText(externalUserId)) {
            return null;
        }
        return mapMapper.selectOne(new LambdaQueryWrapper<SysUserKingdeeMap>()
                .eq(SysUserKingdeeMap::getExternalUserId, externalUserId.trim()));
    }

    /**
     * 按金蝶用户编码查找启用中的映射（同一编码多绑定时取最新一条）。
     */
    public SysUserKingdeeMap findActiveByKdUserNumber(String kdUserNumber) {
        if (!StringUtils.hasText(kdUserNumber)) {
            return null;
        }
        return mapMapper.selectOne(new LambdaQueryWrapper<SysUserKingdeeMap>()
                .eq(SysUserKingdeeMap::getKdUserNumber, kdUserNumber.trim())
                .eq(SysUserKingdeeMap::getStatus, 1)
                .orderByDesc(SysUserKingdeeMap::getId)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
    }

    /**
     * 用户管理保存时同步映射：有绑定则 upsert，无绑定则删除已有映射。
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertBinding(Long wmsUserId, String kdUserNumber, Long kdUserId) {
        if (wmsUserId == null) {
            return;
        }
        String externalUserId = String.valueOf(wmsUserId);
        boolean hasBinding = StringUtils.hasText(kdUserNumber)
                || (kdUserId != null && kdUserId > 0);
        SysUserKingdeeMap existing = findByExternalUserId(externalUserId);
        if (!hasBinding) {
            if (existing != null) {
                mapMapper.deleteById(existing.getId());
            }
            return;
        }
        if (kdUserId == null || kdUserId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "请选择金蝶用户（需包含金蝶用户Id）", "KD_USER_ID_REQUIRED");
        }
        String number = StringUtils.hasText(kdUserNumber)
                ? kdUserNumber.trim()
                : String.valueOf(kdUserId);
        if (existing == null) {
            SysUserKingdeeMap map = new SysUserKingdeeMap();
            map.setExternalUserId(externalUserId);
            map.setKdUserNumber(number);
            map.setKdUserId(kdUserId);
            map.setStatus(1);
            mapMapper.insert(map);
            return;
        }
        existing.setKdUserNumber(number);
        existing.setKdUserId(kdUserId);
        existing.setStatus(1);
        mapMapper.updateById(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByExternalUserId(Long wmsUserId) {
        if (wmsUserId == null) {
            return;
        }
        SysUserKingdeeMap existing = findByExternalUserId(String.valueOf(wmsUserId));
        if (existing != null) {
            mapMapper.deleteById(existing.getId());
        }
    }

    /**
     * 按外部用户 Id 取可用映射；未绑定或缺少 FUserID 时抛业务异常。
     */
    public SysUserKingdeeMap requireBoundForWorkflow(String externalUserId) {
        if (!StringUtils.hasText(externalUserId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "缺少操作人，无法进行金蝶工作流审批", "OPERATOR_REQUIRED");
        }
        SysUserKingdeeMap map = mapMapper.selectOne(new LambdaQueryWrapper<SysUserKingdeeMap>()
                .eq(SysUserKingdeeMap::getExternalUserId, externalUserId.trim())
                .eq(SysUserKingdeeMap::getStatus, 1));
        if (map == null || map.getKdUserId() == null || map.getKdUserId() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "用户[" + externalUserId.trim() + "]未绑定金蝶用户，请在系统管理→用户管理中选择金蝶用户后重试",
                    "KINGDEE_USER_NOT_BOUND");
        }
        return map;
    }
}
