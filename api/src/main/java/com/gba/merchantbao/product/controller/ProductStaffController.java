package com.gba.merchantbao.product.controller;

import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.gba.merchantbao.product.dto.ProductRequests;
import com.gba.merchantbao.product.exception.ProductError;
import com.gba.merchantbao.product.service.ProductCommandService;
import com.gba.merchantbao.product.service.ProductQueryService;
import com.gba.merchantbao.product.vo.ProductViews.AttributeView;
import com.gba.merchantbao.product.vo.ProductViews.CategoryView;
import com.gba.merchantbao.product.vo.ProductViews.PageView;
import com.gba.merchantbao.product.vo.ProductViews.ProductView;
import com.gba.merchantbao.product.vo.ProductViews.SkuView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Authenticated catalog reads (any role, visibility filtered by role) and merchant staff
 * maintenance writes. Controllers never touch Mappers and never accept client identity fields.
 */
@RestController
public class ProductStaffController {
    private final ProductQueryService query;
    private final ProductCommandService commands;
    public ProductStaffController(ProductQueryService query, ProductCommandService commands) {
        this.query = query;
        this.commands = commands;
    }

    // ---------- reads ----------

    @GetMapping("/api/v1/product-categories")
    public ApiResponse<List<CategoryView>> categories(java.security.Principal principal, HttpServletRequest request) {
        return ApiResponse.success(query.staffCategories(actor(principal)), requestId(request));
    }

    @GetMapping("/api/v1/products")
    public ApiResponse<PageView<ProductView>> products(java.security.Principal principal,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       HttpServletRequest request) {
        queryParameters(request);
        var actor = actor(principal);
        return ApiResponse.success(query.staffOrConsumerPage(actor, categoryId, cursor, ProductPublicController.pageSize(size)), requestId(request));
    }

    @GetMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductView> product(java.security.Principal principal, @PathVariable long productId,
                                            HttpServletRequest request) {
        return ApiResponse.success(query.staffOrConsumerProduct(actor(principal), productId), requestId(request));
    }

    @GetMapping("/api/v1/skus/{skuId}")
    public ApiResponse<SkuView> sku(java.security.Principal principal, @PathVariable long skuId,
                                    HttpServletRequest request) {
        return ApiResponse.success(query.staffOrConsumerSku(actor(principal), skuId), requestId(request));
    }

    // ---------- writes (MERCHANT_STAFF only; role gate runs earlier in the security filter) ----------

    @PostMapping("/api/v1/product-categories")
    public ResponseEntity<ApiResponse<CategoryView>> createCategory(java.security.Principal principal,
                                                                    @RequestBody(required = false) ProductRequests.CategoryCreate body,
                                                                    HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var row = commands.createCategory(actor(principal), body);
        return created("/api/v1/product-categories/" + row.id(), CategoryView.from(row), request);
    }

    @PutMapping("/api/v1/product-categories/{categoryId}")
    public ApiResponse<CategoryView> updateCategory(java.security.Principal principal, @PathVariable long categoryId,
                                                    @RequestBody(required = false) ProductRequests.CategoryUpdate body,
                                                    HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var row = commands.updateCategory(actor(principal), categoryId, body);
        return ApiResponse.success(CategoryView.from(row), requestId(request));
    }

    @PostMapping("/api/v1/products")
    public ResponseEntity<ApiResponse<ProductView>> createProduct(java.security.Principal principal,
                                                                  @RequestBody(required = false) ProductRequests.ProductCreate body,
                                                                  HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var row = commands.createProduct(actor(principal), body);
        return created("/api/v1/products/" + row.id(), ProductView.from(row), request);
    }

    @PutMapping("/api/v1/products/{productId}")
    public ApiResponse<ProductView> updateProduct(java.security.Principal principal, @PathVariable long productId,
                                                  @RequestBody(required = false) ProductRequests.ProductUpdate body,
                                                  HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var row = commands.updateProduct(actor(principal), productId, body);
        return ApiResponse.success(ProductView.from(row), requestId(request));
    }

    @PostMapping("/api/v1/products/{productId}/publish")
    public ApiResponse<ProductView> publish(java.security.Principal principal, @PathVariable long productId,
                                            HttpServletRequest request) {
        return ApiResponse.success(ProductView.from(commands.publish(actor(principal), productId)), requestId(request));
    }

    @PostMapping("/api/v1/products/{productId}/unpublish")
    public ApiResponse<ProductView> unpublish(java.security.Principal principal, @PathVariable long productId,
                                              HttpServletRequest request) {
        return ApiResponse.success(ProductView.from(commands.unpublish(actor(principal), productId)), requestId(request));
    }

    @PostMapping("/api/v1/products/{productId}/discontinue")
    public ApiResponse<ProductView> discontinue(java.security.Principal principal, @PathVariable long productId,
                                                HttpServletRequest request) {
        return ApiResponse.success(ProductView.from(commands.discontinue(actor(principal), productId)), requestId(request));
    }

    @PutMapping("/api/v1/products/{productId}/attributes")
    public ApiResponse<List<AttributeView>> replaceAttributes(java.security.Principal principal,
                                                              @PathVariable long productId,
                                                              @RequestBody(required = false) ProductRequests.AttributesUpdate body,
                                                              HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var rows = commands.replaceAttributes(actor(principal), productId, body);
        return ApiResponse.success(rows.stream().map(AttributeView::from).toList(), requestId(request));
    }

    @PostMapping("/api/v1/products/{productId}/skus")
    public ResponseEntity<ApiResponse<SkuView>> createSku(java.security.Principal principal, @PathVariable long productId,
                                                          @RequestBody(required = false) ProductRequests.SkuCreate body,
                                                          HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var row = commands.createSku(actor(principal), productId, body);
        var attributes = ProductQueryService.parseSpecAttributes(row.attributesJson());
        return created("/api/v1/skus/" + row.id(), SkuView.from(row, attributes), request);
    }

    @PutMapping("/api/v1/skus/{skuId}")
    public ApiResponse<SkuView> updateSku(java.security.Principal principal, @PathVariable long skuId,
                                          @RequestBody(required = false) ProductRequests.SkuUpdate body,
                                          HttpServletRequest request) {
        requireBody(body, request);
        body.validate();
        var row = commands.updateSku(actor(principal), skuId, body);
        var attributes = ProductQueryService.parseSpecAttributes(row.attributesJson());
        return ApiResponse.success(SkuView.from(row, attributes), requestId(request));
    }

    // ---------- helpers ----------

    private static Actor actor(java.security.Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Actor actor) return actor;
        throw new ProductError(401, com.gba.merchantbao.common.error.CommonErrorCode.UNAUTHORIZED);
    }

    private static void requireBody(Object body, HttpServletRequest request) {
        if (body == null && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null))
            throw ProductError.invalid();
        if (body == null) throw ProductError.invalid();
    }

    private static void queryParameters(HttpServletRequest request) {
        request.getParameterMap().forEach((name, values) -> {
            if (!Set.of("categoryId", "cursor", "size").contains(name) || values.length != 1)
                throw ProductError.invalid();
        });
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(String location, T data, HttpServletRequest request) {
        return ResponseEntity.created(java.net.URI.create(location))
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(data, requestId(request)));
    }

    private static String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
