package com.hmdp.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码枚举
 * 规则：5位数字，前2位表示模块，后3位表示具体错误
 *   10xxx: 通用/系统
 *   20xxx: 用户模块
 *   30xxx: 商铺模块
 *   40xxx: 优惠券/秒杀模块
 *   50xxx: 博客/社交模块
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(0, "ok"),
    SYSTEM_ERROR(10000, "服务器内部错误"),
    PARAM_ERROR(10001, "参数错误"),
    NOT_LOGIN(10002, "未登录"),
    FORBIDDEN(10003, "无权限"),
    NOT_FOUND(10004, "资源不存在"),
    METHOD_NOT_ALLOWED(10005, "请求方法不支持"),
    RATE_LIMIT(10006, "请求过于频繁，请稍后再试"),

    // 用户模块 20xxx
    USER_NOT_FOUND(20001, "用户不存在"),
    LOGIN_CODE_ERROR(20002, "验证码错误"),
    PHONE_INVALID(20003, "手机号格式错误"),

    // 商铺模块 30xxx
    SHOP_NOT_FOUND(30001, "店铺不存在"),

    // 优惠券/秒杀模块 40xxx
    VOUCHER_NOT_FOUND(40001, "优惠券不存在"),
    SECKILL_NOT_START(40002, "秒杀尚未开始"),
    SECKILL_ENDED(40003, "秒杀已经结束"),
    STOCK_NOT_ENOUGH(40004, "库存不足"),
    DUPLICATE_ORDER(40005, "请勿重复下单"),

    // 博客/社交模块 50xxx
    BLOG_NOT_FOUND(50001, "博客不存在"),
    BLOG_SAVE_FAIL(50002, "保存博客失败"),
    FOLLOW_FAIL(50003, "关注失败"),
    ;

    private final int code;
    private final String msg;
}
