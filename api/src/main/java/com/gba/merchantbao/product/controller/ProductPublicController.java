package com.gba.merchantbao.product.controller;

import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.gba.merchantbao.product.service.ProductQueryService;
import com.gba.merchantbao.product.vo.ProductViews.PageView;
import com.gba.merchantbao.product.vo.ProductViews.PublicProductView;
import com.gba.merchantbao.product.vo.ProductViews.PublicSkuView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/** Anonymous public mall reads; the store slug is the only tenant locator. */
@RestController
@RequestMapping("/api/v1/stores/{storeSlug}")
public class ProductPublicController {
    private final ProductQueryService query;
    public ProductPublicController(ProductQueryService query) { this.query = query; }

    @GetMapping("/products")
    public ApiResponse<PageView<PublicProductView>> products(@PathVariable String storeSlug,
                                                             @RequestParam(required = false) Long categoryId,
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             HttpServletRequest request) {
        queryParameters(request);
        return ApiResponse.success(query.publicPage(storeSlug, categoryId, cursor, pageSize(size)), requestId(request));
    }

    @GetMapping("/products/{productId}")
    public ApiResponse<PublicProductView> product(@PathVariable String storeSlug, @PathVariable long productId,
                                                  HttpServletRequest request) {
        queryParameters(request);
        return ApiResponse.success(query.publicProduct(storeSlug, productId), requestId(request));
    }

    @GetMapping("/skus/{skuId}")
    public ApiResponse<PublicSkuView> sku(@PathVariable String storeSlug, @PathVariable long skuId,
                                          HttpServletRequest request) {
        queryParameters(request);
        return ApiResponse.success(query.publicSku(storeSlug, skuId), requestId(request));
    }

    static int pageSize(int size) { return Math.max(1, Math.min(size, 100)); }

    private static void queryParameters(HttpServletRequest request) {
        request.getParameterMap().forEach((name, values) -> {
            if (!Set.of("categoryId", "cursor", "size").contains(name) || values.length != 1)
                throw com.gba.merchantbao.product.exception.ProductError.invalid();
        });
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
