package com.gba.merchantbao.chat.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChatToolCallsTest {
    @Test void legacyNullAndEmptyArrayHaveTheSameWireMeaning() {
        assertThat(ChatToolCalls.decode(null)).isEmpty();
        assertThat(ChatToolCalls.decode("[]")).isEmpty();
        assertThat(ChatToolCalls.decode(ChatToolCalls.encode(ChatToolCalls.orderLookupSucceeded())))
                .isEqualTo(ChatToolCalls.orderLookupSucceeded());
    }
    @Test void untrustedSnapshotFieldsAreRejectedWithoutEchoingPrivateData() {
        for(String value:java.util.List.of(
                "[{\"name\":\"lookup_order\",\"status\":\"SUCCEEDED\",\"orderNo\":\"SO-private\"}]",
                "[{\"name\":\"SO-private\",\"status\":\"SUCCEEDED\"}]",
                "[{\"name\":\"lookup_order\",\"name\":\"SO-private\",\"status\":\"SUCCEEDED\"}]",
                "[] SO-private")) {
            assertThatThrownBy(()->ChatToolCalls.decode(value)).hasMessage("Invalid tool summary snapshot").hasNoCause();
        }
    }
}
