package com.hmdp.exception;

import lombok.Getter;

/**
 * 业务异常
 * 用于可预见的业务逻辑错误，如：参数校验失败、库存不足、未登录等
 * 与系统异常（NPE、数据库连接失败等）区分
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
