package com.jupin.common.exception;

public class UserNotLoginException extends BaseException {
    public UserNotLoginException() {
        super("用户未登录");
    }
}
