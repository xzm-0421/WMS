package com.wms.mobile.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppUpdateCheckVo {
    /** 是否有可用更新 */
    private boolean hasUpdate;
    /** 当前客户端 versionCode（回显） */
    private Integer currentVersionCode;
    /** 服务端最新 versionName */
    private String latestVersionName;
    /** 服务端最新 versionCode */
    private Integer latestVersionCode;
    /** 更新说明 */
    private String changelog;
    /** 是否强制更新 */
    private boolean force;
    /** wgt / apk */
    private String packageType;
    /** 下载地址（已尽量解析为绝对路径语义，相对路径由客户端补全） */
    private String downloadUrl;
    /** 提示文案 */
    private String message;
}
