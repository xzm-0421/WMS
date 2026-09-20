package com.wms.pdabilllock.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PdaBillLockVo {
    private String billType;
    private String billNo;
    private String lockUserId;
    private String lockUserName;
    private String lockDeviceNo;
    private LocalDateTime lockExpireTime;
    /** 当前登录用户是否持有该锁 */
    private Boolean ownedByMe;
}
