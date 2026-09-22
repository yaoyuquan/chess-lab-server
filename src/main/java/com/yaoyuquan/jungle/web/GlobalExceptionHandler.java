package com.yaoyuquan.jungle.web;

import com.yaoyuquan.jungle.ai.UnknownPlayerException;
import com.yaoyuquan.jungle.web.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理，把异常转成 {code, message} 结构。
 *
 * @author yaoyuquan
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 请求体校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", detail));
    }

    /**
     * 请求体不是合法 JSON，或编码有问题。这是调用方的错，不该报 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体无法解析：{}", e.getMessage());
        return ResponseEntity.badRequest().body(new ApiError("MALFORMED_BODY", "请求体不是合法的 JSON"));
    }

    /**
     * 棋手 id 不存在。
     */
    @ExceptionHandler(UnknownPlayerException.class)
    public ResponseEntity<ApiError> onUnknownPlayer(UnknownPlayerException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("UNKNOWN_PLAYER", e.getMessage()));
    }

    /**
     * 兜底处理。走到这里说明有没预料到的问题，记日志便于排查。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onOther(Exception e) {
        // Spring MVC 自己抛的异常（路径不存在、方法不支持等）都实现了 ErrorResponse 接口，
        // 已经带好了正确的状态码，不能一律吞成 500
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            String code = status == null ? "REQUEST_ERROR" : status.name();
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(new ApiError(code, detail == null ? "请求无法处理" : detail));
        }
        log.error("接口处理失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务内部错误"));
    }
}
