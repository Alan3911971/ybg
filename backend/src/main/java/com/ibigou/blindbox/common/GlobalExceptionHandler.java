package com.ibigou.blindbox.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理：业务异常返回 code=-1 + 提示文案；参数校验/未知异常兜底。
 */
@RestControllerAdvice
@lombok.extern.slf4j.Slf4j
public class GlobalExceptionHandler {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.ibigou.blindbox.service.AlertService alertService;

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleIllegalArg(IllegalArgumentException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception e) {
        String exClass = e.getClass().getName();
        String exMsg = e.getMessage() == null ? "" : e.getMessage();
        log.error("未处理异常 type={} msg={}", exClass, exMsg, e);
        if (alertService != null) {
            try {
                alertService.record("exception", exClass + ": " + exMsg);
            } catch (Exception alertEx) {
                log.error("alertService.record swallow: {}", alertEx.getMessage());
            }
        }
        // BUG-001 修复: 暴露异常类名 + 第一帧位置给响应, 便于 test-env / SSH 不通时定位
        String firstFrame = "";
        StackTraceElement[] st = e.getStackTrace();
        if (st != null && st.length > 0) {
            firstFrame = st[0].getClassName() + "." + st[0].getMethodName() + "(" + st[0].getFileName() + ":" + st[0].getLineNumber() + ")";
        }
        String shortCls = exClass;
        int dot = exClass.lastIndexOf(".");
        if (dot >= 0) shortCls = exClass.substring(dot + 1);
        String shortMsg = exMsg;
        if (shortMsg.length() > 100) shortMsg = shortMsg.substring(0, 100);
        return Result.fail(500, "[" + shortCls + "] " + shortMsg + " @ " + firstFrame);
    }
}
