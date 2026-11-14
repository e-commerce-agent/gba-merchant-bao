package com.gba.merchantbao.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.order.service.OrderQueryService;
import org.springframework.stereotype.Component;

/** Only the accepted authentication Actor may read business data; never accepts model identity fields. */
@Component
public final class AgentOrderLookup {
    private final OrderQueryService orders;
    private final ObjectMapper json;
    public AgentOrderLookup(OrderQueryService orders,ObjectMapper json){this.orders=orders;this.json=json;}
    public String query(Actor actor,OrderLookupCall call) {
        var detail=orders.detailByOrderNo(actor,call.orderNo());
        try {return json.writeValueAsString(detail);}
        catch(JsonProcessingException error){throw new AgentModel.ProtocolException();}
    }
}
