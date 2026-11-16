package com.gba.merchantbao.test.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.agent.config.AgentProperties;
import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.*;
import com.gba.merchantbao.chat.controller.ChatController;
import com.gba.merchantbao.chat.entity.ChatSessionRow;
import com.gba.merchantbao.chat.exception.ChatExceptionHandler;
import com.gba.merchantbao.chat.service.*;
import com.gba.merchantbao.chat.vo.ChatRunView;
import com.gba.merchantbao.infrastructure.web.RequestIdFilter;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

class ChatControllerTest {
    ChatService service; AgentProperties properties; ExecutorService writers; ScheduledExecutorService timer; MockMvc mvc; Actor actor;
    @BeforeEach void setUp(){
        service=mock(ChatService.class);properties=new AgentProperties();writers=Executors.newFixedThreadPool(2);timer=Executors.newSingleThreadScheduledExecutor();
        actor=new Actor(new AuthPrincipal(21,11,AccountRole.CONSUMER),"test");
        var mapper=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc=MockMvcBuilders.standaloneSetup(new ChatController(service,properties,mapper,writers,timer))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setControllerAdvice(new ChatExceptionHandler()).addFilters(new RequestIdFilter()).build();
    }
    @AfterEach void tearDown(){writers.shutdownNow();timer.shutdownNow();}
    @Test void createReturnsStringIdsUtcTimeAndRequestCorrelation() throws Exception {
        var at=LocalDateTime.of(2026,11,8,1,2,3,456_000_000);
        when(service.create(any(),eq("Help"))).thenReturn(new ChatSessionRow(300000000000000001L,11,21,"CONSUMER","Help","ACTIVE",at,at,null,null,1,null,null));
        mvc.perform(post("/api/v1/chat/sessions").principal(auth()).header("X-Request-Id","create-1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Help\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/chat/sessions/300000000000000001"))
                .andExpect(header().string("X-Request-Id","create-1")).andExpect(jsonPath("$.data.sessionId").value("300000000000000001"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-08T01:02:03.456Z"));
    }
    @Test void unknownIdentityAndModelOverridesAreRejectedBeforeServiceCalls() throws Exception {
        for(String body:List.of("{\"title\":\"x\",\"merchantId\":\"99\"}","{\"title\":\"x\",\"system\":\"owned\"}","null","[]"))
            mvc.perform(post("/api/v1/chat/sessions").principal(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
        mvc.perform(post("/api/v1/chat/sessions/300000000000000001/messages").principal(auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\",\"clientMessageId\":\"00000000-0000-4000-8000-000000000001\",\"role\":\"MERCHANT_STAFF\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void duplicateMessagesReturnJsonAndNeverOpenAStream() throws Exception {
        var run=new PreparedRun(new com.gba.merchantbao.chat.entity.ChatOwner(11,21,"CONSUMER"),300000000000000001L,5,6,7,"COMPLETED",true,null,List.of());
        when(service.accept(any(),anyLong(),anyString(),anyString())).thenReturn(run);
        mvc.perform(post("/api/v1/chat/sessions/300000000000000001/messages").principal(auth()).accept(MediaType.TEXT_EVENT_STREAM,MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello\",\"clientMessageId\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.data.runId").value("5"));
        verify(service,never()).execute(any(),anyString(),any());
    }
    @Test void strictTypesLengthsIdsAndMimeErrorsUseSafeJsonBeforeCallingTheService() throws Exception {
        for(String body:List.of("{\"title\":123}","{\"title\":null}","{\"title\":\""+"好".repeat(201)+"\"}"))
            mvc.perform(post("/api/v1/chat/sessions").principal(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
        String key="00000000-0000-4000-8000-000000000001";
        for(String body:List.of("{\"content\":7,\"clientMessageId\":\""+key+"\"}","{\"content\":\" \t\",\"clientMessageId\":\""+key+"\"}",
                "{\"content\":\""+"好".repeat(4001)+"\",\"clientMessageId\":\""+key+"\"}","{\"content\":\"hello\",\"clientMessageId\":7}"))
            mvc.perform(post("/api/v1/chat/sessions/1/messages").principal(auth()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10001));
        mvc.perform(get("/api/v1/chat/sessions/01").principal(auth())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/sessions").principal(auth()).param("size","x")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/chat/sessions").principal(auth()).param("size","1","2")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat/sessions/1/messages").principal(auth()).accept(MediaType.TEXT_PLAIN).contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"hello\",\"clientMessageId\":\""+key+"\"}"))
            .andExpect(status().isNotAcceptable()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.code").value(10001));
        mvc.perform(post("/api/v1/chat/sessions").principal(auth()).contentType(MediaType.TEXT_PLAIN).content("{}"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }
    @Test void newMessagesStreamOrderedPublicEventsAndNoNullExtensionFields() throws Exception {
        var run=new PreparedRun(new com.gba.merchantbao.chat.entity.ChatOwner(11,21,"CONSUMER"),300000000000000001L,5,6,7,"STARTED",false,Instant.now().plusSeconds(10),List.of());
        when(service.accept(any(),anyLong(),anyString(),anyString())).thenReturn(run);
        when(service.execute(eq(run),anyString(),any())).thenAnswer(invocation->{
            AgentModel.Listener listener=invocation.getArgument(2);listener.delta("Hi");listener.complete(null);return (AgentModel.Call)()->{};
        });
        MvcResult initial=mvc.perform(post("/api/v1/chat/sessions/300000000000000001/messages").principal(auth()).accept(MediaType.TEXT_EVENT_STREAM,MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hello\",\"clientMessageId\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        initial.getAsyncResult(5000);
        String body=mvc.perform(asyncDispatch(initial)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body.indexOf("event:start")).isLessThan(body.indexOf("event:token")).isLessThan(body.indexOf("event:done"));
        assertThat(body).contains("5:1","5:2","5:3","\"delta\":\"Hi\"","\"usage\":null","\"requestId\"");
        assertThat(body).doesNotContain("citation","tool_call","Last-Event-ID","\"delta\":null");
    }
    private UsernamePasswordAuthenticationToken auth(){return new UsernamePasswordAuthenticationToken(actor,null,List.of());}
}
