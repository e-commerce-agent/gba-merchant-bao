package com.gba.merchantbao.knowledge.controller;

import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.common.api.ApiResponse;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import com.gba.merchantbao.knowledge.exception.KnowledgeError;
import com.gba.merchantbao.knowledge.service.KnowledgeService;
import com.gba.merchantbao.knowledge.vo.KnowledgeViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
public class KnowledgeController {
    private final KnowledgeService service;
    public KnowledgeController(KnowledgeService service) { this.service = service; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Document>> upload(Principal principal, @RequestParam String title,
                                                        @RequestParam MultipartFile file, HttpServletRequest request) throws Exception {
        var parts = request.getParts();
        if (request.getQueryString() != null && !request.getQueryString().isEmpty()) throw KnowledgeError.invalid();
        if (parts.size() != 2 || parts.stream().filter(p -> p.getName().equals("file")).count() != 1
                || parts.stream().filter(p -> p.getName().equals("title")).count() != 1) throw KnowledgeError.invalid();
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.upload(actor(principal), title, file), requestId(request)));
    }
    @GetMapping
    public ApiResponse<Page> list(Principal principal, @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String cursor, HttpServletRequest request) {
        request.getParameterMap().forEach((name, values) -> {
            if (!Set.of("size", "cursor").contains(name) || values.length != 1) throw KnowledgeError.invalid();
        });
        return ApiResponse.success(service.list(actor(principal), size, cursor), requestId(request));
    }
    private static Actor actor(Principal principal) {
        if (principal instanceof Authentication authentication && authentication.getPrincipal() instanceof Actor actor) return actor;
        throw new KnowledgeError(401, CommonErrorCode.UNAUTHORIZED);
    }
    private static String requestId(HttpServletRequest request) { return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE); }
}
