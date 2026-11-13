package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.service.*;
import com.gba.merchantbao.test.support.DockerRequired;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MilvusKnowledgeStoreIT {
    @Test void createsExplicitSchemaConfirmsVisibilityAndRejectsDimensionMismatch() {
        DockerRequired.check();
        try (var fixture = new MilvusFixture()) {
            fixture.start();
            var p = new RagProperties(); p.setMilvusUri(fixture.uri()); p.setEmbeddingDimension(3);
            try (var store = new MilvusKnowledgeStore(p)) {
                store.initialize();
                store.insertAndConfirm(1, 11, List.of(111L, 112L), List.of(List.of(1f, 0f, 0f), List.of(0f, 1f, 0f)), new ImportDeadline());
                store.insertAndConfirm(2, 22, List.of(222L), List.of(List.of(1f, 0f, 0f)), new ImportDeadline());
                assertThat(store.search(1, List.of(1f, 0f, 0f), new ImportDeadline()))
                        .extracting(MilvusKnowledgeStore.Hit::chunkId).containsExactly(111L);
                assertThat(store.search(2, List.of(1f, 0f, 0f), new ImportDeadline()))
                        .extracting(MilvusKnowledgeStore.Hit::chunkId).containsExactly(222L);
                assertThat(store.search(3, List.of(1f, 0f, 0f), new ImportDeadline())).isEmpty();
                assertThat(store.search(1, List.of(0f, 0f, 1f), new ImportDeadline())).isEmpty();
            }
            p.setEmbeddingDimension(4);
            try (var incompatible = new MilvusKnowledgeStore(p)) {
                assertThatThrownBy(incompatible::initialize).hasMessageContaining("dimension");
            }
        }
    }
}
