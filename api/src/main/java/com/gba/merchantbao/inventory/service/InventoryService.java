package com.gba.merchantbao.inventory.service;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.inventory.common.error.InventoryErrorCode;
import com.gba.merchantbao.inventory.common.exception.InventoryBizException;
import com.gba.merchantbao.inventory.common.exception.VersionConflictException;
import com.gba.merchantbao.inventory.dto.InventoryAdjustDTO;
import com.gba.merchantbao.inventory.dto.InventoryChangeDTO;
import com.gba.merchantbao.inventory.dto.InventoryVO;
import com.gba.merchantbao.inventory.mapper.InventoryMapper;
import com.gba.merchantbao.inventory.po.InventoryPO;
import com.gba.merchantbao.inventory.po.IdempotentRecordPO;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存模块业务编排：幂等控制 → 乐观锁重试 → 返回统一结果。
 * 本类方法本身不加 @Transactional，事务边界交给 InventoryStockTxService，
 * 保证每次重试都是全新事务、能读到最新数据。
 */
@Service
public class InventoryService {

    public static final String BIZ_RESERVE = "RESERVE";
    public static final String BIZ_RELEASE = "RELEASE";
    public static final String BIZ_DEDUCT = "DEDUCT";
    public static final String BIZ_ADJUST = "ADJUST";

    private static final int MAX_RETRY = 3;

    private final InventoryMapper inventoryMapper;
    private final InventoryStockTxService stockTxService;
    private final IdempotentService idempotentService;

    public InventoryService(InventoryMapper inventoryMapper, InventoryStockTxService stockTxService,
            IdempotentService idempotentService) {
        this.inventoryMapper = inventoryMapper;
        this.stockTxService = stockTxService;
        this.idempotentService = idempotentService;
    }

    /** 商户后台库存分页返回（分页参数默认值与上限沿 v1 约定）。 */
    public record Page(List<InventoryVO> records, long total, int current, int size) { }

    /** 预占：可售 -qty，预占 +qty（下单时调用） */
    public void reserve(Actor actor, InventoryChangeDTO dto) {
        requireChangeArgs(dto);
        execute(merchantIdOf(actor), operatorOf(actor), BIZ_RESERVE, dto.getIdempotentKey(), dto.getSkuId(),
                -dto.getQty(), dto.getQty(),
                InventoryErrorCode.AVAILABLE_NOT_ENOUGH, InventoryErrorCode.RESERVED_NOT_ENOUGH,
                dto.getBizId(), dto.getRemark());
    }

    /** 释放：可售 +qty，预占 -qty（取消订单时调用） */
    public void release(Actor actor, InventoryChangeDTO dto) {
        requireChangeArgs(dto);
        execute(merchantIdOf(actor), operatorOf(actor), BIZ_RELEASE, dto.getIdempotentKey(), dto.getSkuId(),
                dto.getQty(), -dto.getQty(),
                InventoryErrorCode.AVAILABLE_NOT_ENOUGH, InventoryErrorCode.RESERVED_NOT_ENOUGH,
                dto.getBizId(), dto.getRemark());
    }

    /** 扣减：预占 -qty（支付成功/发货后真正出库） */
    public void deduct(Actor actor, InventoryChangeDTO dto) {
        requireChangeArgs(dto);
        execute(merchantIdOf(actor), operatorOf(actor), BIZ_DEDUCT, dto.getIdempotentKey(), dto.getSkuId(),
                0, -dto.getQty(),
                InventoryErrorCode.AVAILABLE_NOT_ENOUGH, InventoryErrorCode.RESERVED_NOT_ENOUGH,
                dto.getBizId(), dto.getRemark());
    }

    /** 人工调整：直接增减可售库存，正数入库、负数盘亏 */
    public void adjust(Actor actor, InventoryAdjustDTO dto) {
        requireSkuId(dto.getSkuId());
        if (dto.getAdjustQty() == null || dto.getAdjustQty() == 0) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "调整数量不能为0");
        }
        execute(merchantIdOf(actor), operatorOf(actor), BIZ_ADJUST, dto.getIdempotentKey(), dto.getSkuId(),
                dto.getAdjustQty(), 0,
                InventoryErrorCode.ADJUST_BELOW_ZERO, InventoryErrorCode.RESERVED_NOT_ENOUGH,
                null, dto.getRemark());
    }

    /** 商户后台查询单个 SKU 库存 */
    public InventoryVO query(Actor actor, long skuId) {
        return getByMerchantAndSku(merchantIdOf(actor), skuId);
    }

    /** 商户后台库存分页列表 */
    public Page pageList(Actor actor, int pageNo, int pageSize) {
        Long merchantId = merchantIdOf(actor);
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryMapper.countByMerchant(merchantId);
        var records = inventoryMapper.selectPageByMerchant(merchantId, offset, safePageSize).stream()
                .map(po -> InventoryVO.of(po.getSkuId(), po.getAvailableQty(), po.getReservedQty()))
                .toList();
        return new Page(records, total, safePageNo, safePageSize);
    }

    /**
     * 内部只读接口：供订单模块与 Agent Tool 调用（Java 服务调用，不走 HTTP）。
     * 调用方必须传入从 token/上下文解析出的 merchantId，禁止前端透传。
     */
    public InventoryVO getByMerchantAndSku(Long merchantId, Long skuId) {
        InventoryPO po = inventoryMapper.selectByMerchantAndSku(merchantId, skuId);
        if (po == null) {
            throw new InventoryBizException(InventoryErrorCode.STOCK_NOT_FOUND,
                    "该商户下不存在该 SKU 的库存记录");
        }
        return InventoryVO.of(po.getSkuId(), po.getAvailableQty(), po.getReservedQty());
    }

    /** 公开端只需要"有货/无货"布尔，不暴露精确库存数量 */
    public boolean inStock(Long merchantId, Long skuId) {
        InventoryPO po = inventoryMapper.selectByMerchantAndSku(merchantId, skuId);
        return po != null && po.getAvailableQty() != null && po.getAvailableQty() > 0;
    }

    // ---------------------------------------------------------------- 内部实现

    private void requireChangeArgs(InventoryChangeDTO dto) {
        requireSkuId(dto.getSkuId());
        if (dto.getQty() == null || dto.getQty() < 1) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "变更数量必须大于0");
        }
    }

    private void requireSkuId(Long skuId) {
        if (skuId == null) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "SKU ID不能为空");
        }
    }

    private void execute(Long merchantId,
                        String operatorId,
                        String bizType,
                        String idempotentKey,
                        Long skuId,
                        int changeAvailable,
                        int changeReserved,
                        int availableShortageCode,
                        int reservedShortageCode,
                        Long bizId,
                        String remark) {

        if (merchantId == null) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "登录身份缺失，无法确定商户");
        }
        if (idempotentKey == null || idempotentKey.isBlank()) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "幂等键不能为空");
        }

        // 1. 幂等抢占：唯一索引兜底，重复请求不重复执行
        if (!idempotentService.tryAcquire(idempotentKey, merchantId, bizType)) {
            String status = idempotentService.getStatus(idempotentKey);
            if (IdempotentRecordPO.STATUS_SUCCESS.equals(status)) {
                // 重复请求按成功返回（对齐文档口径：重复视为成功）
                return;
            }
            if (IdempotentRecordPO.STATUS_FAILED.equals(status)
                    && idempotentService.resetFailedToProcessing(idempotentKey)) {
                // 失败记录被本次调用抢回，继续执行
            } else {
                throw new InventoryBizException(InventoryErrorCode.REQUEST_PROCESSING,
                        "同一请求正在处理中，请稍后重试");
            }
        }

        // 2. 乐观锁重试：每轮独立事务、重新读取、重新校验
        try {
            doWithRetry(merchantId, skuId, changeAvailable, changeReserved, bizType,
                    bizId, idempotentKey, operatorId, remark,
                    availableShortageCode, reservedShortageCode);
            idempotentService.markSuccess(idempotentKey);
        } catch (RuntimeException e) {
            idempotentService.markFailed(idempotentKey, e.getMessage());
            throw e;
        }
    }

    private void doWithRetry(Long merchantId,
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

        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                stockTxService.applyDelta(merchantId, skuId, changeAvailable, changeReserved,
                        bizType, bizId, idempotentKey, operatorId, remark,
                        availableShortageCode, reservedShortageCode);
                return;
            } catch (VersionConflictException ignored) {
                // 版本冲突：事务已回滚，下一轮新事务重新读取最新 version
            }
        }
        throw new InventoryBizException(InventoryErrorCode.CONFLICT_RETRY_EXHAUSTED,
                "库存更新冲突，重试 " + MAX_RETRY + " 次仍失败，请稍后再试");
    }

    /**
     * 主仓 Actor 适配：租户与账号都来自 token 解析出的 AuthPrincipal（long 基本类型）。
     */
    private Long merchantIdOf(Actor actor) {
        if (actor == null) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "未获取到登录身份");
        }
        return actor.principal().merchantId();
    }

    /**
     * 主仓 Actor 适配：AuthPrincipal 没有 staffId，操作人记为 accountId；
     * 消费者不是库存操作主体，直接拒绝。
     */
    private String operatorOf(Actor actor) {
        if (actor == null) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "未获取到登录身份");
        }
        var principal = actor.principal();
        if (principal.role() != AccountRole.MERCHANT_STAFF) {
            throw new InventoryBizException(InventoryErrorCode.INVALID_ARGUMENT, "库存操作仅限商户员工");
        }
        return String.valueOf(principal.accountId());
    }
}
