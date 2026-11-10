package com.gba.merchantbao.inventory.service;

import com.gba.merchantbao.inventory.mapper.IdempotentMapper;
import com.gba.merchantbao.inventory.po.IdempotentRecordPO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 幂等控制。
 *
 * <p>实现要点（对应组长整改意见第 9 条）：
 * 1. 用唯一索引 uk_idempotent_key 兜底，靠 INSERT 抢占，而不是“先查后写”——后者并发下会双双通过；
 * 2. 这里的 INSERT 刻意不加 @Transactional：走自动提交，唯一键冲突抛出后不会污染任何业务事务，
 *    也就不需要处理 rollback-only 的坑；
 * 3. 重复请求的处理规则：SUCCESS 直接视为成功返回；PROCESSING 返回“处理中”；
 *    FAILED 允许用同一幂等键重试（CAS 抢回 PROCESSING）。</p>
 */
@Service
@RequiredArgsConstructor
public class IdempotentService {

    private final IdempotentMapper idempotentMapper;

    /**
     * 尝试占领该幂等键。
     *
     * @return true 表示本次调用拿到了执行权；false 表示该键已存在
     */
    public boolean tryAcquire(String idempotentKey, Long merchantId, String bizType) {
        IdempotentRecordPO record = new IdempotentRecordPO();
        record.setIdempotentKey(idempotentKey);
        record.setMerchantId(merchantId);
        record.setBizType(bizType);
        record.setStatus(IdempotentRecordPO.STATUS_PROCESSING);
        try {
            idempotentMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public String getStatus(String idempotentKey) {
        IdempotentRecordPO record = idempotentMapper.selectByIdempotentKey(idempotentKey);
        return record == null ? null : record.getStatus();
    }

    public boolean isSuccess(String idempotentKey) {
        return IdempotentRecordPO.STATUS_SUCCESS.equals(getStatus(idempotentKey));
    }

    public boolean resetFailedToProcessing(String idempotentKey) {
        return idempotentMapper.casFailedToProcessing(idempotentKey) == 1;
    }

    public void markSuccess(String idempotentKey) {
        idempotentMapper.updateStatus(idempotentKey, IdempotentRecordPO.STATUS_SUCCESS, null);
    }

    public void markFailed(String idempotentKey, String errorMsg) {
        String msg = errorMsg == null ? null
                : errorMsg.substring(0, Math.min(errorMsg.length(), 255));
        idempotentMapper.updateStatus(idempotentKey, IdempotentRecordPO.STATUS_FAILED, msg);
    }
}
