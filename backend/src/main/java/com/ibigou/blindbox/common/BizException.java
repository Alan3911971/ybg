package com.ibigou.blindbox.common;

/**
 * 业务异常：message 直接展示给前端。
 */
public class BizException extends RuntimeException {

    public BizException(String message) {
        super(message);
    }
}
