package com.gba.merchantbao.inventory.common.exception;

/**
 * 乐观锁版本冲突异常，仅用于模块内部的重试控制，不对外暴露。
 * 抛出后当前事务回滚，由上层在新事务中重新读取最新数据后重试。
 */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException() {
        super("version conflict");
    }
}
