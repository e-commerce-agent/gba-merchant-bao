package com.gba.merchantbao.product.mapper;

import com.gba.merchantbao.product.entity.ProductCategoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductCategoryMapper {
    /** Caller must construct the row from a verified actor, not client identity fields. */
    int insert(ProductCategoryRow row);
    int update(@Param("categoryId") long categoryId, @Param("merchantId") long merchantId,
               @Param("parentId") Long parentId, @Param("name") String name,
               @Param("sortNo") int sortNo, @Param("status") String status, @Param("now") LocalDateTime now);
    ProductCategoryRow find(@Param("merchantId") long merchantId, @Param("categoryId") long categoryId);
    /** Direct children only; parent chains are limited to two levels. */
    List<ProductCategoryRow> findChildren(@Param("merchantId") long merchantId, @Param("parentId") long parentId);
    List<ProductCategoryRow> listAll(@Param("merchantId") long merchantId);
    boolean existsChild(@Param("merchantId") long merchantId, @Param("parentId") long parentId);
    boolean existsActiveProduct(@Param("merchantId") long merchantId, @Param("categoryId") long categoryId);
    boolean nameTaken(@Param("merchantId") long merchantId, @Param("parentId") Long parentId,
                      @Param("name") String name, @Param("excludeId") Long excludeId);
}
