package com.gba.merchantbao.product.mapper;

import com.gba.merchantbao.product.entity.ProductAttributeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductAttributeMapper {
    /** Caller must construct the row from a verified actor, not client identity fields. */
    int insert(ProductAttributeRow row);
    int deleteForProduct(@Param("merchantId") long merchantId, @Param("productId") long productId);
    List<ProductAttributeRow> listForProduct(@Param("merchantId") long merchantId, @Param("productId") long productId);
    /** Only published products leak into public searchable reads. */
    List<ProductAttributeRow> listPublicForProduct(@Param("merchantId") long merchantId, @Param("productId") long productId);
}
