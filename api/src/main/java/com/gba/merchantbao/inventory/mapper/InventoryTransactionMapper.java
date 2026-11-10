package com.gba.merchantbao.inventory.mapper;

import com.gba.merchantbao.inventory.po.InventoryTransactionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryTransactionMapper {

    int insert(InventoryTransactionPO po);

    List<InventoryTransactionPO> selectByMerchantAndSku(@Param("merchantId") Long merchantId,
                                                       @Param("skuId") Long skuId,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);
}
