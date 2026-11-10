package com.gba.merchantbao.inventory.controller;

import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.gba.merchantbao.inventory.dto.InventoryAdjustDTO;
import com.gba.merchantbao.inventory.dto.InventoryChangeDTO;
import com.gba.merchantbao.inventory.dto.InventoryVO;
import com.gba.merchantbao.inventory.service.InventoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户后台库存接口。
 *
 * <p>整改要点：
 * 1. 不从客户端自定义请求头取商户/员工身份，全部由 @AuthenticationPrincipal Actor 从 token 解析；
 * 2. 不提供匿名公开端点，公开端"有货/无货"并入商品详情聚合（由商品模块调用 Service.inStock）；
 * 3. 统一返回主仓 ApiResponse{code, message, data, requestId}，错误码走 13xxx 段。</p>
 *
 * <p>路径 /api/v1/merchant/inventory 沿用模块交付稿；资源路径规范若统一调整需整体替换。</p>
 */
@RestController
@RequestMapping("/api/v1/merchant/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** 预占库存（下单时调用）：可售 -qty、预占 +qty */
    @PostMapping("/reserve")
    public ApiResponse<Void> reserve(@AuthenticationPrincipal Actor actor,
                                     @Valid @RequestBody InventoryChangeDTO dto,
                                     HttpServletRequest request) {
        inventoryService.reserve(actor, dto);
        return ApiResponse.success(null, requestId(request));
    }

    /** 释放预占（取消订单时调用）：可售 +qty、预占 -qty */
    @PostMapping("/release")
    public ApiResponse<Void> release(@AuthenticationPrincipal Actor actor,
                                     @Valid @RequestBody InventoryChangeDTO dto,
                                     HttpServletRequest request) {
        inventoryService.release(actor, dto);
        return ApiResponse.success(null, requestId(request));
    }

    /** 扣减预占（支付成功/发货后出库）：预占 -qty */
    @PostMapping("/deduct")
    public ApiResponse<Void> deduct(@AuthenticationPrincipal Actor actor,
                                    @Valid @RequestBody InventoryChangeDTO dto,
                                    HttpServletRequest request) {
        inventoryService.deduct(actor, dto);
        return ApiResponse.success(null, requestId(request));
    }

    /** 人工调整库存（入库为正、盘亏为负） */
    @PostMapping("/adjust")
    public ApiResponse<Void> adjust(@AuthenticationPrincipal Actor actor,
                                    @Valid @RequestBody InventoryAdjustDTO dto,
                                    HttpServletRequest request) {
        inventoryService.adjust(actor, dto);
        return ApiResponse.success(null, requestId(request));
    }

    /** 查询单个 SKU 库存 */
    @GetMapping("/{skuId}")
    public ApiResponse<InventoryVO> query(@AuthenticationPrincipal Actor actor,
                                          @PathVariable("skuId") long skuId,
                                          HttpServletRequest request) {
        return ApiResponse.success(inventoryService.query(actor, skuId), requestId(request));
    }

    /** 商户后台库存分页列表（pageSize 上限 100） */
    @GetMapping("/page")
    public ApiResponse<InventoryService.Page> page(@AuthenticationPrincipal Actor actor,
                                                   @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                   @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
                                                   HttpServletRequest request) {
        return ApiResponse.success(inventoryService.pageList(actor, pageNo, pageSize), requestId(request));
    }

    /** requestId 由 RequestIdFilter 注入，此处只做透传 */
    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
