package com.gba.merchantbao.product.mapper;

import com.gba.merchantbao.product.entity.ProductRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductMapper {
    /** Caller must construct the row from a verified actor, not client identity fields. */
    int insert(ProductRow row);
    int updateBasics(@Param("productId") long productId, @Param("merchantId") long merchantId,
                     @Param("categoryId") Long categoryId, @Param("name") String name,
                     @Param("brand") String brand, @Param("description") String description,
                     @Param("now") LocalDateTime now);
    int updateStatus(@Param("productId") long productId, @Param("merchantId") long merchantId,
                     @Param("expected") String expected, @Param("target") String target,
                     @Param("now") LocalDateTime now);
    ProductRow find(@Param("merchantId") long merchantId, @Param("productId") long productId);
    /** status == null means every status (staff view); public callers pass PUBLISHED. */
    List<ProductRow> page(@Param("merchantId") long merchantId, @Param("status") String status,
                          @Param("categoryId") Long categoryId,
                          @Param("beforeTime") LocalDateTime beforeTime, @Param("beforeId") Long beforeId,
                          @Param("size") int size);
    boolean codeTaken(@Param("merchantId") long merchantId, @Param("productCode") String productCode,
                      @Param("excludeId") Long excludeId);
    boolean skuExistsForProduct(@Param("merchantId") long merchantId, @Param("productId") long productId,
                                @Param("statuses") List<String> statuses);
}
