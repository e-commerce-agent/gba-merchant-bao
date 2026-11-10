package com.gba.merchantbao.inventory.service;

import com.gba.merchantbao.inventory.common.error.InventoryErrorCode;
import com.gba.merchantbao.inventory.common.exception.InventoryBizException;
import com.gba.merchantbao.inventory.common.exception.VersionConflictException;
import com.gba.merchantbao.inventory.mapper.InventoryMapper;
import com.gba.merchantbao.inventory.mapper.InventoryTransactionMapper;
import com.gba.merchantbao.inventory.po.InventoryPO;
import com.gba.merchantbao.inventory.po.InventoryTransactionPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存变更的单个事务单元。
 *
 * <p>为什么单独抽一个 Bean：乐观锁重试必须在“新事务”里重新读取最新数据，
 * 否则在 MySQL 默认 RR 隔离级别下，同一事务内重复 SELECT 读到的仍是旧快照，
 * 重试永远不会成功（这是第一版 updateWithRetry 失效的另一层原因）。
 * 所以每一轮重试都调用本类的 REQUIRES_NEW 方法，冲突时整体回滚、下一轮重新读。</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryStockTxService {

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;

    /**
     * 执行一次库存变更（单事务）。
     * 每次进入都重新 SELECT → 重新计算 → 重新校验 → 带旧 version 条件更新。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void applyDelta(Long merchantId,
                          Long skuId,
                          int changeAvailable,
                          int changeReserved,
                          String bizType,
                          Long bizId,
                          String idempotentKey,
                          String operatorId,
                          String remark,
                          int availableShortageCode,
                          int reservedShortageCode) {

        InventoryPO current = inventoryMapper.selectByMerchantAndSku(merchantId, skuId);
        if (current == null) {
            throw new InventoryBizException(InventoryErrorCode.STOCK_NOT_FOUND,
                    "该商户下不存在该 SKU 的库存记录");
        }

        int newAvailable = current.getAvailableQty() + changeAvailable;
        int newReserved = current.getReservedQty() + changeReserved;

        // 每一轮重试都重新校验，防止并发下卖出超过实际可售量
        if (newAvailable < 0) {
            throw new InventoryBizException(availableShortageCode, "可售库存不足，操作失败");
        }
        if (newReserved < 0) {
            throw new InventoryBizException(reservedShortageCode, "预占库存不足，操作失败");
        }

        int rows = inventoryMapper.updateStockWithVersion(
                merchantId, skuId, newAvailable, newReserved, current.getVersion());

        if (rows == 0) {
            // 版本冲突：本事务回滚，由外层开启新事务重试
            throw new VersionConflictException();
        }

        InventoryTransactionPO log = new InventoryTransactionPO();
        log.setMerchantId(merchantId);
        log.setSkuId(skuId);
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setChangeAvailable(changeAvailable);
        log.setChangeReserved(changeReserved);
        log.setBeforeAvailable(current.getAvailableQty());
        log.setAfterAvailable(newAvailable);
        log.setBeforeReserved(current.getReservedQty());
        log.setAfterReserved(newReserved);
        log.setIdempotentKey(idempotentKey);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        transactionMapper.insert(log);
    }
}
