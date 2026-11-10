package com.gba.merchantbao.inventory.mapper;

import com.gba.merchantbao.inventory.po.InventoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryMapper {

    /**
     * 按租户 + SKU 查库存（所有读写都必须带 merchantId，禁止跨租户访问）
     */
    InventoryPO selectByMerchantAndSku(@Param("merchantId") Long merchantId,
                                       @Param("skuId") Long skuId);

    /**
     * 乐观锁更新：只有 version 与查询时一致才会更新成功
     * 返回受影响行数，0 表示版本冲突，需要重新读取后重试
     */
    int updateStockWithVersion(@Param("merchantId") Long merchantId,
                              @Param("skuId") Long skuId,
                              @Param("availableQty") Integer availableQty,
                              @Param("reservedQty") Integer reservedQty,
                              @Param("version") Integer version);

    /**
     * 商户后台库存列表
     */
    List<InventoryPO> selectPageByMerchant(@Param("merchantId") Long merchantId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countByMerchant(@Param("merchantId") Long merchantId);
}
