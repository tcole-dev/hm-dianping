package com.hmdp.dto;

import com.hmdp.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;
    private Integer code;

    public static Result ok(){
        return new Result(true, null, null, null, 0);
    }
    public static Result ok(Object data){
        return new Result(true, null, data, null, 0);
    }
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total, 0);
    }
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null, -1);
    }
    public static Result fail(ErrorCode errorCode){
        return new Result(false, errorCode.getMsg(), null, null, errorCode.getCode());
    }
    public static Result fail(int code, String errorMsg){
        return new Result(false, errorMsg, null, null, code);
    }
}
