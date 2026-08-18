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
        log.error("未处理异常", e);
        if (alertService != null) {
            alertService.record("exception", "未处理异常: " + String.valueOf(e.getMessage()));
        }
        return Result.fail(500, "系统繁忙，请稍后再试");
    }
}
