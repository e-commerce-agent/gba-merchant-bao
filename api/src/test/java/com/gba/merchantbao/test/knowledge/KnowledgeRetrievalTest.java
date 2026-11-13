package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.entity.ReadyKnowledgeChunk;
import com.gba.merchantbao.knowledge.mapper.KnowledgeMapper;
import com.gba.merchantbao.knowledge.service.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KnowledgeRetrievalTest {
    final RagProperties p = new RagProperties();
    final KnowledgeEmbedding embedding = mock(KnowledgeEmbedding.class);
    final MilvusKnowledgeStore vectors = mock(MilvusKnowledgeStore.class);
    final KnowledgeMapper documents = mock(KnowledgeMapper.class);
    final KnowledgeRetrieval retrieval = new KnowledgeRetrieval(p, embedding, vectors, documents);
    final List<Float> vector = List.of(1f, 0f, 0f);

    @Test void embedsOriginalQuestionAndUsesOnlyAuthoritativeReadyTextInRankOrder() {
        String question = "  原始问题😀\n退货？  ";
        when(embedding.embed(eq(List.of(question)), any())).thenReturn(List.of(vector));
        when(vectors.search(eq(71L), eq(vector), any())).thenReturn(List.of(
                new MilvusKnowledgeStore.Hit(2, 12, .8f), new MilvusKnowledgeStore.Hit(1, 11, .9f),
                new MilvusKnowledgeStore.Hit(3, 13, .7f), new MilvusKnowledgeStore.Hit(4, 14, .1f)));
        when(documents.readyReferences(71, List.of(1L, 2L, 3L))).thenReturn(List.of(
                new ReadyKnowledgeChunk(12, 2, "店铺规则", 2, "实际正文二"),
                new ReadyKnowledgeChunk(11, 1, "售后政策", 1, "实际正文一")));
        var sources = retrieval.retrieve(71, question, new ImportDeadline());
        assertThat(sources).extracting(s -> s.chunkId().value()).containsExactly(1L, 2L);
        assertThat(sources).extracting(s -> s.excerpt()).containsExactly("实际正文一", "实际正文二");
        assertThat(sources.getFirst().title()).isEqualTo("售后政策");
        verify(embedding).embed(eq(List.of(question)), any());
        verify(documents).readyReferences(71, List.of(1L, 2L, 3L));
    }
    @Test void noMatchAndFilteredOutReadyRowsAreEmptyButDependenciesRemainErrors() {
        when(embedding.embed(anyList(), any())).thenReturn(List.of(vector));
        when(vectors.search(anyLong(), anyList(), any())).thenReturn(List.of());
        assertThat(retrieval.retrieve(71, "问题", new ImportDeadline())).isEmpty();
        verifyNoInteractions(documents);
        when(vectors.search(anyLong(), anyList(), any())).thenReturn(List.of(new MilvusKnowledgeStore.Hit(1, 11, .9f)));
        when(documents.readyReferences(anyLong(), anyList())).thenReturn(List.of());
        assertThat(retrieval.retrieve(71, "问题", new ImportDeadline())).isEmpty();
        when(documents.readyReferences(anyLong(), anyList())).thenThrow(new IllegalStateException("db unavailable"));
        assertThatThrownBy(() -> retrieval.retrieve(71, "问题", new ImportDeadline())).hasMessage("db unavailable");
        when(vectors.search(anyLong(), anyList(), any())).thenThrow(new IllegalStateException("milvus unavailable"));
        assertThatThrownBy(() -> retrieval.retrieve(71, "问题", new ImportDeadline())).hasMessage("milvus unavailable");
        when(embedding.embed(anyList(), any())).thenThrow(new IllegalStateException("embedding unavailable"));
        assertThatThrownBy(() -> retrieval.retrieve(71, "问题", new ImportDeadline())).hasMessage("embedding unavailable");
    }
}
