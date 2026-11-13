package com.gba.merchantbao.knowledge.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;
import java.math.BigDecimal;
import java.util.*;

/** Frontend wire contract, generated and exported by the running Springdoc group. */
@Configuration(proxyBeanMethods = false)
public class KnowledgeOpenApiConfiguration {
    private static final String PATH = "/api/v1/knowledge/documents";
    @Bean GroupedOpenApi knowledgeV1OpenApi() {
        return GroupedOpenApi.builder().group("knowledge-v1")
                .packagesToScan("com.gba.merchantbao.knowledge.controller").pathsToMatch(PATH)
                .addOpenApiCustomizer(api -> {
                    api.getInfo().setTitle("Knowledge documents API"); api.getInfo().setVersion("v1");
                    api.getInfo().setDescription("仅 MERCHANT_STAFF 可上传/列出本商户资料；消费者403、未认证401，先鉴权再解析文件。身份来自 Bearer，拒绝客户端覆盖 merchantId/accountId/role。同步上传，无任务或重试接口；所有响应 no-store。RAG 关闭时上传503，列表仍可读。");
                    api.setServers(List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
                    var components = new Components().addSecuritySchemes("knowledgeBearer", new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP).scheme("bearer").description("现有员工登录返回的 access token"));
                    components.addSchemas("KnowledgeDocument", object().addProperty("documentId", id())
                            .addProperty("title", new StringSchema().maxLength(200))
                            .addProperty("status", new StringSchema()._enum(List.of("PROCESSING", "READY", "FAILED")))
                            .addProperty("chunkCount", new IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(200)))
                            .addProperty("createdAt", new StringSchema().format("date-time").description("UTC ISO-8601"))
                            .required(List.of("documentId", "title", "status", "chunkCount", "createdAt")));
                    components.addSchemas("KnowledgeDocumentEnvelope", envelope(ref("KnowledgeDocument")));
                    components.addSchemas("KnowledgePageEnvelope", envelope(object()
                            .addProperty("items", new ArraySchema().items(ref("KnowledgeDocument")))
                            .addProperty("nextCursor", new Schema<>().anyOf(List.of(new StringSchema(), nullType())))
                            .addProperty("hasMore", new BooleanSchema()).required(List.of("items", "nextCursor", "hasMore"))));
                    components.addSchemas("KnowledgeError", envelope(nullType())); api.setComponents(components);
                    var upload = operation("uploadKnowledgeDocument", "同步导入本商户 TXT/Markdown", "201", "KnowledgeDocumentEnvelope");
                    upload.setDescription("仅两个 multipart 字段 title 和 file，不接受查询参数或重复字段。UTF-8（可带BOM）.txt/.md，最大1 MiB、5万Unicode码点、200片，每片最多300码点。事务外向量化与入库，60秒总预算；确认可查询后才发布 READY。失败为 FAILED 且不参与聊天；重复上传产生新文档。");
                    upload.requestBody(new RequestBody().required(true).content(new Content().addMediaType("multipart/form-data",
                            new MediaType().schema(object().addProperty("title", new StringSchema().minLength(1).maxLength(200)
                                    .description("去除首尾空白后1–200个Unicode码点").example("售后政策"))
                                    .addProperty("file", new StringSchema().format("binary").description("单个UTF-8 TXT/MD文件，最大1 MiB"))
                                    .required(List.of("title", "file"))))));
                    upload.getResponses().get("201").getContent().get("application/json").example(Map.of("code", 0, "message", "success",
                            "requestId", "knowledge-example", "data", Map.of("documentId", "610000000000000001", "title", "售后政策",
                                    "status", "READY", "chunkCount", 1, "createdAt", "2026-11-13T10:00:00Z")));
                    upload.getResponses().addApiResponse("413", error("10001 文件、码点或切片超限"));
                    upload.getResponses().addApiResponse("415", error("10001 仅支持TXT/MD及multipart/form-data"));
                    upload.getResponses().addApiResponse("503", error("13501 RAG禁用；13502 Embedding/Milvus依赖失败或超时"));
                    var list = operation("listKnowledgeDocuments", "按createdAt/id倒序分页列出本商户文档摘要", "200", "KnowledgePageEnvelope");
                    list.setDescription("仅返回本商户 PROCESSING/READY/FAILED 摘要，不含正文。未知或重复查询参数、非法游标返回400。nextCursor非空时原样传入下一页。");
                    list.addParametersItem(new Parameter().in("query").name("size").schema(new IntegerSchema()
                            .minimum(BigDecimal.ONE).maximum(BigDecimal.valueOf(100))._default(20)));
                    list.addParametersItem(new Parameter().in("query").name("cursor").schema(new StringSchema().maxLength(128)
                            .pattern("^[A-Za-z0-9_-]+$")).description("省略为首页；不透明资源游标"));
                    for (var op : List.of(upload, list)) op.getResponses().values().forEach(response -> {
                        response.addHeaderObject("X-Request-Id", new Header().schema(new StringSchema()));
                        response.addHeaderObject("Cache-Control", new Header().schema(new StringSchema().example("no-store")));
                    });
                    api.setPaths(new Paths().addPathItem(PATH, new PathItem().post(upload).get(list)));
                }).build();
    }
    private static Operation operation(String id, String summary, String status, String response) {
        var op = new Operation().operationId(id).summary(summary).tags(List.of("Knowledge documents"))
                .security(List.of(new SecurityRequirement().addList("knowledgeBearer"))).responses(new ApiResponses());
        op.addParametersItem(new Parameter().in("header").name("X-Request-Id").schema(new StringSchema()
                .pattern("^[A-Za-z0-9._:-]{1,128}$")).description("缺失/非法时由服务器生成"));
        op.getResponses().addApiResponse(status, new ApiResponse().description("成功").content(json(ref(response))));
        op.getResponses().addApiResponse("400", error("10001 非法字段、文件、分页或游标"));
        op.getResponses().addApiResponse("401", error("10002 Bearer缺失/失效"));
        op.getResponses().addApiResponse("403", error("10003 仅员工可访问"));
        op.getResponses().addApiResponse("500", error("10500 内部错误"));
        return op;
    }
    private static ApiResponse error(String description) { return new ApiResponse().description(description).content(json(ref("KnowledgeError"))); }
    private static ObjectSchema object() { return (ObjectSchema) new ObjectSchema().additionalProperties(false); }
    private static Schema<?> ref(String name) { return new Schema<>().$ref("#/components/schemas/" + name); }
    private static Schema<?> nullType() { return new Schema<>().types(Set.of("null")); }
    private static Schema<?> id() { return new StringSchema().pattern("^[1-9][0-9]{0,18}$").description("正十进制BIGINT字符串"); }
    private static Content json(Schema<?> schema) { return new Content().addMediaType("application/json", new MediaType().schema(schema)); }
    private static Schema<?> envelope(Schema<?> data) { return object().addProperty("code", new IntegerSchema())
            .addProperty("message", new StringSchema()).addProperty("data", data).addProperty("requestId", new StringSchema())
            .required(List.of("code", "message", "data", "requestId")); }
}
