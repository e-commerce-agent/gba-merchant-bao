package com.gba.merchantbao.chat.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.examples.Example;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.*;
import java.util.*;

/** Explicit wire schemas: IDs remain strings and SSE is not a JSON SseEmitter object. */
@Configuration(proxyBeanMethods=false)
public class ChatOpenApiConfiguration {
    private static final String BASE="/api/v1/chat/sessions";
    @Bean GroupedOpenApi agentConversationV1OpenApi() {
        return GroupedOpenApi.builder().group("agent-conversation-v1")
            .packagesToScan("com.gba.merchantbao.chat.controller").pathsToMatch(BASE,BASE+"/**")
            .addOpenApiCustomizer(api->{
                api.getInfo().setTitle("Agent conversation API");api.getInfo().setVersion("v1");
                api.getInfo().setDescription("消费者和员工仅访问本商户本人会话。Bearer 来自既有认证；禁用模型时仍可读历史。MySQL 为运行终态来源。所有响应 no-store。");
                api.setServers(List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
                if(api.getComponents()==null)api.setComponents(new Components());
                var components=api.getComponents();
                components.setSchemas(new LinkedHashMap<>());
                components.addSecuritySchemes("chatBearer",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").description("对应商户、消费者或员工的 access token；不是模型 API key"));
                components.addSchemas("ChatSession",session());components.addSchemas("ChatMessage",message());components.addSchemas("ChatRun",run());
                components.addSchemas("ToolCallSummary",toolSummary());components.addSchemas("Source",source());components.addSchemas("ChatDone",done());
                components.addSchemas("ChatError",envelope(nullType()));
                components.addSchemas("ChatSessionEnvelope",envelope(ref("ChatSession")));
                components.addSchemas("ChatRunEnvelope",envelope(ref("ChatRun")));
                components.addSchemas("ChatSessionPageEnvelope",envelope(page(ref("ChatSession"))));
                components.addSchemas("ChatMessagePageEnvelope",envelope(page(ref("ChatMessage"))));
                var title=new StringSchema().description("可省略；先去除首尾空白，最多 200 Unicode 字符；空白使用新对话").maxLength(200);
                var create=operation("createChatSession","创建本人会话","201","ChatSessionEnvelope");
                create.requestBody(new RequestBody().required(false).content(json(object().addProperty("title",title))));
                create.getResponses().get("201").addHeaderObject("Location",new Header().schema(new StringSchema()).description(BASE+"/{sessionId}"));
                var list=operation("listChatSessions","分页列出本人会话","200","ChatSessionPageEnvelope");pagination(list);
                var detail=operation("getChatSession","查询本人会话及有效运行状态","200","ChatSessionEnvelope");sessionId(detail);
                var archive=operation("archiveChatSession","幂等归档本人会话；运行中返回 busy","200","ChatSessionEnvelope");sessionId(archive);
                archive.requestBody(new RequestBody().required(false).content(json(object().description("允许无 body 或空对象；拒绝 null/未知字段"))));
                var history=operation("listChatMessages","按 messageSeq 升序读取本人消息；含失败轮次与部分文本","200","ChatMessagePageEnvelope");sessionId(history);pagination(history);
                var send=operation("sendChatMessage","提交消息并生成 SSE，或返回同 key 同正文的原 run JSON","200","ChatRunEnvelope");sessionId(send);
                send.setDescription("必须同时处理 text/event-stream 和 application/json。幂等先于 busy/归档判断；key 相同正文不同返回 409，不重跑检索或生成。RAG 仅检索本商户 READY 资料；无匹配时空 sources 与原问题继续进入模型，可选择唯一只读订单查询工具；检索或工具依赖失败为 error。done.data 对应 ChatDone，正文、实际入模来源和非敏感工具摘要同事务提交；历史保留相同快照。客户端不能覆盖身份、system、role 或模型。断开后查历史，不支持 Last-Event-ID 重放。本期仅发送 start/token/done/error，不发送 citation/tool_call 事件。");
                send.requestBody(new RequestBody().required(true).content(json(object()
                    .addProperty("content",new StringSchema().minLength(1).maxLength(4000).description("非空白；保留原文，限制按 Unicode 字符计数"))
                    .addProperty("clientMessageId",new StringSchema().pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").example("00000000-0000-4000-8000-000000000001"))
                    .required(List.of("content","clientMessageId")))));
                send.getResponses().get("200").setDescription("新 run 为 SSE；重复提交为 JSON 原标识。SSE 首帧 start，随后非空 token，最后仅 done 或 error。done 前终态已提交。注释 heartbeat 不续期认证。");
                send.getResponses().get("200").getContent().addMediaType("text/event-stream",new MediaType().schema(new StringSchema())
                    .addExamples("completed",new Example().summary("start → token → done；usage 未提供为 null").value(SUCCESS))
                    .addExamples("noMatch",new Example().summary("无匹配仍由模型生成回答；sources=[]，未调用工具时 toolCalls=[]").value(NO_MATCH))
                    .addExamples("orderLookup",new Example().summary("完成一次订单查询；摘要不含订单号、参数或原始结果").value(ORDER_LOOKUP))
                    .addExamples("timeout",new Example().summary("start 后的超时终态；HTTP 已为 200").value(TIMEOUT)));
                send.getResponses().get("200").getContent().get("text/event-stream")
                    .addExtension("x-sse-data-schemas",Map.of("done",ref("ChatDone")));
                api.setPaths(new Paths().addPathItem(BASE,new PathItem().post(create).get(list))
                    .addPathItem(BASE+"/{sessionId}",new PathItem().get(detail))
                    .addPathItem(BASE+"/{sessionId}/archive",new PathItem().post(archive))
                    .addPathItem(BASE+"/{sessionId}/messages",new PathItem().get(history).post(send)));
            }).build();
    }
    private static Operation operation(String id,String summary,String status,String response) {
        var op=new Operation().operationId(id).summary(summary).tags(List.of("Agent conversation"))
            .security(List.of(new SecurityRequirement().addList("chatBearer"))).responses(new ApiResponses());
        op.addParametersItem(new Parameter().in("header").name("X-Request-Id").required(false)
            .schema(new StringSchema().pattern("^[A-Za-z0-9._:-]{1,128}$")).description("非法/缺失时服务器生成；所有帧与响应头使用相同值"));
        op.getResponses().addApiResponse(status,new ApiResponse().description("成功").content(json(ref(response))));
        Map<String,String> errors=new LinkedHashMap<>();
        errors.put("400","10001 参数无效；13001 上下文预算不足");errors.put("401","10002 Bearer 缺失/失效");
        errors.put("403","10003 角色越权");errors.put("404","10004 不存在、跨商户或他人资源，统一不可见");
        errors.put("409","13002 运行中 busy；13003 幂等正文冲突；13004 已归档");
        errors.put("406","10001 无法协商成功 MIME");errors.put("415","10001 不支持请求 MIME");
        errors.put("503","13501 模型禁用；13502 模型依赖不可用；13504 MySQL 不可用");errors.put("504","13503 超时（建流前）");errors.put("500","10500 内部错误");
        errors.forEach((code,description)->{
            int business=switch(code){case "401"->10002;case "403"->10003;case "404"->10004;case "409"->13002;case "503"->13502;case "504"->13503;case "500"->10500;default->10001;};
            var example=new LinkedHashMap<String,Object>();example.put("code",business);example.put("message",description);example.put("data",null);example.put("requestId","chat-example");
            op.getResponses().addApiResponse(code,new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json",new MediaType().schema(ref("ChatError")).example(example))));
        });
        op.getResponses().values().forEach(r->{
            r.addHeaderObject("X-Request-Id",new Header().schema(new StringSchema()));
            r.addHeaderObject("Cache-Control",new Header().description("包含 no-store").schema(new StringSchema().example("no-store")));
        });return op;
    }
    private static void sessionId(Operation op){op.addParametersItem(new Parameter().in("path").name("sessionId").required(true).schema(id()));}
    private static void pagination(Operation op){
        op.addParametersItem(new Parameter().in("query").name("size").schema(new IntegerSchema().minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(100))._default(20)));
        op.addParametersItem(new Parameter().in("query").name("cursor").schema(new StringSchema().maxLength(512).pattern("^[A-Za-z0-9_-]+$")).description("不透明、资源绑定的游标；省略为首页，未知/重复参数拒绝"));
    }
    private static ObjectSchema object(){return (ObjectSchema)new ObjectSchema().additionalProperties(false);}
    private static Schema<?> nullType(){var schema=new Schema<>();schema.setTypes(Set.of("null"));return schema;}
    private static Schema<?> nullable(Schema<?> schema){return new Schema<>().anyOf(List.of(schema,nullType()));}
    private static Schema<?> ref(String name){return new Schema<>().$ref("#/components/schemas/"+name);}
    private static Content json(Schema<?> schema){return new Content().addMediaType("application/json",new MediaType().schema(schema));}
    private static Schema<?> id(){return new StringSchema().pattern("^[1-9][0-9]{0,18}$").description("正十进制 BIGINT 字符串，最大 9223372036854775807").example("300000000000000001");}
    private static Schema<?> time(){return new StringSchema().format("date-time").description("UTC ISO-8601，Z 后缀").example("2026-11-08T01:02:03.456Z");}
    private static StringSchema values(String... values){return new StringSchema()._enum(List.of(values));}
    private static Schema<?> envelope(Schema<?> data){return object().addProperty("code",new IntegerSchema()).addProperty("message",new StringSchema()).addProperty("data",data)
            .addProperty("requestId",new StringSchema()).required(List.of("code","message","data","requestId"));}
    private static Schema<?> page(Schema<?> item){return object().addProperty("items",new ArraySchema().items(item)).addProperty("nextCursor",nullable(new StringSchema()))
            .addProperty("hasMore",new BooleanSchema()).required(List.of("items","nextCursor","hasMore"));}
    private static Schema<?> session(){return object().addProperty("sessionId",id()).addProperty("merchantId",id()).addProperty("title",new StringSchema())
            .addProperty("status",values("ACTIVE","ARCHIVED")).addProperty("createdAt",time()).addProperty("updatedAt",time())
            .addProperty("archivedAt",nullable(time())).addProperty("activeRunId",nullable(id())).addProperty("activeRunStatus",nullable(values("STARTED","STREAMING")))
            .required(List.of("sessionId","merchantId","title","status","createdAt","updatedAt","archivedAt","activeRunId","activeRunStatus"));}
    private static Schema<?> run(){return object().addProperty("sessionId",id()).addProperty("runId",id()).addProperty("userMessageId",id()).addProperty("messageId",id())
            .addProperty("status",values("STARTED","STREAMING","COMPLETED","CANCELLED","TIMED_OUT","FAILED"))
            .required(List.of("sessionId","runId","userMessageId","messageId","status"));}
    private static Schema<?> message(){return object().addProperty("messageId",id()).addProperty("messageSeq",new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ONE))
            .addProperty("runId",id()).addProperty("clientMessageId",nullable(new StringSchema())).addProperty("senderType",values("USER","ASSISTANT"))
            .addProperty("content",new StringSchema()).addProperty("status",values("STARTED","STREAMING","COMPLETED","CANCELLED","TIMED_OUT","FAILED"))
            .addProperty("errorCode",nullable(new StringSchema())).addProperty("inputTokens",nullable(new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ZERO)))
            .addProperty("outputTokens",nullable(new IntegerSchema().format("int64").minimum(java.math.BigDecimal.ZERO))).addProperty("createdAt",time()).addProperty("completedAt",nullable(time()))
            .addProperty("sources",sources()).addProperty("toolCalls",toolCalls())
            .required(List.of("messageId","messageSeq","runId","clientMessageId","senderType","content","status","errorCode","inputTokens","outputTokens","createdAt","completedAt","sources","toolCalls"));}
    private static Schema<?> source(){return object().description("实际入模的参考片段快照；不代表逐句核验回答")
            .addProperty("documentId",id()).addProperty("chunkId",id()).addProperty("title",new StringSchema().maxLength(200))
            .addProperty("chunkNo",new IntegerSchema().minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(200)))
            .addProperty("excerpt",new StringSchema().maxLength(300).description("最多300个 Unicode 码点，与实际入模片段一致"))
            .required(List.of("documentId","chunkId","title","chunkNo","excerpt"));}
    private static Schema<?> sources(){return new ArraySchema().items(ref("Source")).maxItems(5)
            .description("成功助手消息的来源快照；旧消息、USER、失败、取消、超时、无资料均为空数组");}
    private static Schema<?> toolSummary(){return object().description("唯一只读订单工具的固定成功摘要；不含调用 id、参数、订单号或原始结果")
            .addProperty("name",values("lookup_order")).addProperty("status",values("SUCCEEDED"))
            .required(List.of("name","status"));}
    private static Schema<?> toolCalls(){return new ArraySchema().items(ref("ToolCallSummary")).maxItems(1)
            .description("已成功完成回答的工具摘要；旧消息、USER、未调用、失败、取消、超时均为空数组");}
    private static Schema<?> done(){return object().description("SSE event: done 的 data JSON；仅在成功正文、sources 及 toolCalls 同事务提交后发送")
            .addProperty("version",values("v1")).addProperty("runId",id()).addProperty("sessionId",id()).addProperty("messageId",id())
            .addProperty("requestId",new StringSchema()).addProperty("status",values("COMPLETED"))
            .addProperty("usage",nullable(object().addProperty("inputTokens",nullable(new IntegerSchema().format("int64")))
                    .addProperty("outputTokens",nullable(new IntegerSchema().format("int64"))).required(List.of("inputTokens","outputTokens"))))
            .addProperty("sources",sources()).addProperty("toolCalls",toolCalls()).required(List.of("version","runId","sessionId","messageId","requestId","status","usage","sources","toolCalls"));}
    private static final String START="id: 41:1\nevent: start\ndata: {\"version\":\"v1\",\"runId\":\"41\",\"sessionId\":\"31\",\"messageId\":\"61\",\"requestId\":\"chat-example\",\"userMessageId\":\"51\"}\n\n";
    private static final String SUCCESS=START+"id: 41:2\nevent: token\ndata: {\"version\":\"v1\",\"runId\":\"41\",\"sessionId\":\"31\",\"messageId\":\"61\",\"requestId\":\"chat-example\",\"delta\":\"购买后七日内，未拆封商品可申请退货。\"}\n\nid: 41:3\nevent: done\ndata: {\"version\":\"v1\",\"runId\":\"41\",\"sessionId\":\"31\",\"messageId\":\"61\",\"requestId\":\"chat-example\",\"status\":\"COMPLETED\",\"usage\":null,\"sources\":[{\"documentId\":\"610000000000000001\",\"chunkId\":\"620000000000000001\",\"title\":\"售后政策\",\"chunkNo\":1,\"excerpt\":\"购买后七日内，未拆封商品可申请退货。\"}],\"toolCalls\":[]}\n\n";
    private static final String NO_MATCH=START+"id: 41:2\nevent: token\ndata: {\"version\":\"v1\",\"runId\":\"41\",\"sessionId\":\"31\",\"messageId\":\"61\",\"requestId\":\"chat-example\",\"delta\":\"当前资料中没有找到相关信息，请补充说明或联系店铺确认\"}\n\nid: 41:3\nevent: done\ndata: {\"version\":\"v1\",\"runId\":\"41\",\"sessionId\":\"31\",\"messageId\":\"61\",\"requestId\":\"chat-example\",\"status\":\"COMPLETED\",\"usage\":null,\"sources\":[],\"toolCalls\":[]}\n\n";
    private static final String ORDER_LOOKUP=NO_MATCH.replace("当前资料中没有找到相关信息，请补充说明或联系店铺确认","订单查询完成")
            .replace("\"toolCalls\":[]","\"toolCalls\":[{\"name\":\"lookup_order\",\"status\":\"SUCCEEDED\"}]");
    private static final String TIMEOUT=START+"id: 41:2\nevent: error\ndata: {\"version\":\"v1\",\"runId\":\"41\",\"sessionId\":\"31\",\"messageId\":\"61\",\"requestId\":\"chat-example\",\"code\":\"TIMEOUT\",\"message\":\"模型响应超时\",\"terminalStatus\":\"TIMED_OUT\"}\n\n";
}
