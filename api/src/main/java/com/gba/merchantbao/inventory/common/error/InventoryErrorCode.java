package com.gba.merchantbao.inventory.common.error;

/**
 * 库存模块错误码，段位 13xxx，与其他业务模块码段隔离
 */
public final class InventoryErrorCode {

    /** 库存记录不存在 */
    public static final int STOCK_NOT_FOUND = 13001;
    /** 可售库存不足 */
    public static final int AVAILABLE_NOT_ENOUGH = 13002;
    /** 并发冲突，乐观锁重试耗尽 */
    public static final int CONFLICT_RETRY_EXHAUSTED = 13003;
    /** 同一幂等请求处理中，请稍后重试 */
    public static final int REQUEST_PROCESSING = 13004;
    /** 预占库存不足 */
    public static final int RESERVED_NOT_ENOUGH = 13005;
    /** 请求参数不合法 */
    public static final int INVALID_ARGUMENT = 13006;
    /** 库存不足，无法完成调整（调整后为负） */
    public static final int ADJUST_BELOW_ZERO = 13007;
    /** 内部异常 */
    public static final int INTERNAL_ERROR = 13999;

    private InventoryErrorCode() {
    }
}
