package com.gba.merchantbao.product.mapper;

import com.gba.merchantbao.product.entity.ProductSkuRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductSkuMapper {
    /** Caller must construct the row from a verified actor, not client identity fields. */
    int insert(ProductSkuRow row);
    int update(@Param("skuId") long skuId, @Param("merchantId") long merchantId,
               @Param("title") String title, @Param("price") BigDecimal price,
               @Param("attributesJson") String attributesJson, @Param("status") String status,
               @Param("now") LocalDateTime now);
    ProductSkuRow find(@Param("merchantId") long merchantId, @Param("skuId") long skuId);
    List<ProductSkuRow> listForProduct(@Param("merchantId") long merchantId, @Param("productId") long productId,
                                       @Param("status") String status);
    boolean codeTaken(@Param("merchantId") long merchantId, @Param("skuCode") String skuCode,
                      @Param("excludeId") Long excludeId);
}
