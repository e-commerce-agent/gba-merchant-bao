package com.gba.merchantbao.test.knowledge;

import com.gba.merchantbao.knowledge.service.KnowledgeText;
import com.gba.merchantbao.knowledge.exception.KnowledgeError;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class KnowledgeTextTest {
    static MockMultipartFile file(String name, String text) {
        return new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
    }
    @Test void validUtf8MarkdownKeepsUnicodeBoundariesAndLimitsOverlap() {
        String text = "😀".repeat(750);
        var chunks = KnowledgeText.parse(file("规则.MD", text));
        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(s -> {
            assertThat(s.codePointCount(0, s.length())).isLessThanOrEqualTo(300);
            assertThat(s.codePoints()).allMatch(p -> p == 0x1f600);
        });
        assertThat(chunks.stream().mapToInt(s -> s.codePointCount(0, s.length())).sum()).isEqualTo(810);
        assertThat(KnowledgeText.parse(file("政策.txt", "第一段。\n\n第二段！"))).containsExactly("第一段。\n\n第二段！");
        assertThat(KnowledgeText.title("😀".repeat(200))).hasSize(400);
    }
    @Test void rejectsWrongFormatEmptyMalformedAndOversizedInputs() {
        for (var f : new MockMultipartFile[]{file("a.pdf", "有效"), file("a.txt", "  \n\t"),
                file("a.txt", "中".repeat(50_001)), file("a.txt", "a".repeat(1_048_577)),
                new MockMultipartFile("file", "a.md", "text/plain", new byte[]{(byte)0xc3, (byte)0x28})}) {
            assertThatThrownBy(() -> KnowledgeText.parse(f)).isInstanceOf(KnowledgeError.class);
        }
        assertThatThrownBy(() -> KnowledgeText.title("😀".repeat(201))).isInstanceOf(KnowledgeError.class);
        // Short paragraphs can produce many small chunks; the count is bounded independently of bytes.
        assertThatThrownBy(() -> KnowledgeText.parse(file("a.md", ("a".repeat(235) + "\n\n").repeat(210))))
                .isInstanceOf(KnowledgeError.class);
    }
}
