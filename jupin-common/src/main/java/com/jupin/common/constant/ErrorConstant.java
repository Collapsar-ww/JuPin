package com.jupin.common.constant;

public class ErrorConstant {
    public static final String ACCOUNT_DISABLED = "账号已被禁用";
    public static final String ACCOUNT_OR_PASSWORD_ERROR = "手机号或密码错误";
    public static final String ACCESS_DENIED = "无权限访问";
    public static final String ALREADY_CONFIRMED = "你已经确认过";
    public static final String ALREADY_IN_POOL_OR_PENDING = "你已在拼车中或等待审核";
    public static final String ALREADY_REVIEWED = "你已经评价过";
    public static final String CURRENT_POOL_STATUS_CANNOT_PAY_DEPOSIT = "当前拼车状态不允许支付押金";
    public static final String CURRENT_STATUS_NO_CONFIRM_REQUIRED = "当前状态无需确认";
    public static final String DM_CANNOT_TRANSFER_AFTER_COMPLETED = "成团后不能转让DM";
    public static final String INVALID_ORDER_TYPE = "订单类型不合法";
    public static final String INVALID_ROLE = "角色不合法，仅支持 player/shop";
    public static final String MEMBER_STATUS_CHANGED = "成员状态已变化，请刷新后重试";
    public static final String MEMBER_STATUS_CANNOT_CREATE_DEPOSIT_ORDER = "成员状态不允许创建押金订单";
    public static final String MEMBER_STATUS_CANNOT_PAY_DEPOSIT = "成员状态不允许支付押金";
    public static final String NOT_IN_POOL = "你不在该拼车中";
    public static final String NOT_FORMAL_MEMBER_CANNOT_CREATE_FEE_ORDER = "只有正式成员才能创建车费订单";
    public static final String POOL_MEMBER_NOT_FOUND = "拼车成员不存在";
    public static final String NOT_POOL_FORMAL_MEMBER = "你不是该拼车的正式成员";
    public static final String NOT_POOL_MEMBER_CANNOT_CREATE_ORDER = "你不是该拼车成员，不能创建订单";
    public static final String ONLY_OWNER_CAN_CONFIRM = "仅发布人可发起确认";
    public static final String ONLY_OWNER_CAN_FINISH_CONFIRM = "仅发布人可发起完成确认";
    public static final String ORDER_ALREADY_CREATED = "已创建过该类型的订单";
    public static final String ORDER_NOT_FOUND = "订单不存在";
    public static final String ORDER_NOT_OWNED = "无权限操作他人订单";
    public static final String ORDER_STATUS_INVALID = "订单状态异常";
    public static final String PHONE_REGISTERED = "手机号已注册";
    public static final String POOL_ALREADY_FULL = "拼车已满员";
    public static final String POOL_CANNOT_JOIN = "该拼车已无法加入";
    public static final String POOL_CANNOT_LEAVE = "当前状态下不允许退出";
    public static final String POOL_NOT_COMPLETED = "拼车未成功";
    public static final String POOL_NOT_COMPLETED_CANNOT_CREATE_FEE_ORDER = "拼车成功后才能创建车费订单";
    public static final String POOL_NOT_FINISHED_CANNOT_REVIEW = "拼车未完成，不能评价";
    public static final String REVIEW_TARGET_INVALID = "评价对象不属于该拼车";
    public static final String REVIEW_TYPE_INVALID = "评价类型不合法";
    public static final String REVIEWER_NOT_POOL_MEMBER = "只有正式参与成员才能评价";
    public static final String POOL_NOT_FOUND = "拼车不存在";
    public static final String POOL_NOT_FULL = "拼车未满员";
    public static final String REFRESH_TOKEN_INVALID = "RefreshToken无效或已过期";
    public static final String REFRESH_TOKEN_REVOKED = "RefreshToken已被吊销";
    public static final String REFUND_REASON_POOL_CANCELLED = "拼车取消自动退款";
    public static final String SCRIPT_NOT_FOUND = "剧本不存在";
    public static final String SCRIPT_NOT_FOUND_OR_OFFLINE = "剧本不存在或已下架";
    public static final String SHOP_POOL_MUST_SPECIFY_SHOP = "店家局必须指定店铺";
    public static final String SHOP_SCRIPT_NOT_IN_LIBRARY = "该剧本不在店铺剧本库中";
    public static final String SHOP_ROLE_REQUIRED = "你不是该店铺的店长或管理员";
    public static final String SYSTEM_BUSY = "系统繁忙，请稍后再试";
    public static final String USER_NOT_FOUND = "用户不存在";

    private ErrorConstant() {
    }
}
