package com.wms.common.result;

import com.wms.common.constant.ErrorCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class ApiResult<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResult<T> ok(T data) {
        return ok("操作成功", data);
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(ErrorCode.SUCCESS);
        result.setMessage(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> ApiResult<T> fail(int code, String message, T data) {
        ApiResult<T> result = fail(code, message);
        result.setData(data);
        return result;
    }
}
