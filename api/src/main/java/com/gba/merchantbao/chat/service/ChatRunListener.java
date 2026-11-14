package com.gba.merchantbao.chat.service;

import com.gba.merchantbao.agent.service.AgentModel;
import com.gba.merchantbao.knowledge.vo.KnowledgeSource;
import java.util.List;

/** Chat completion metadata, kept outside the model transport protocol. */
public interface ChatRunListener extends AgentModel.Listener {
    void complete(AgentModel.Usage usage, List<KnowledgeSource> sources);
    default void complete(AgentModel.Usage usage, List<KnowledgeSource> sources,
                          List<com.gba.merchantbao.chat.vo.ToolCallSummary> toolCalls) { complete(usage, sources); }
    @Override default void complete(AgentModel.Usage usage) { complete(usage, List.of()); }
}
