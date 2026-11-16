package com.gba.merchantbao.knowledge.service;

import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.knowledge.exception.KnowledgeError;
import org.springframework.web.multipart.MultipartFile;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.io.IOException;
import java.util.*;

public final class KnowledgeText {
    private KnowledgeText() { }
    public static final int MAX_BYTES = 1024 * 1024;
    public static String title(String title) {
        if (title == null) throw KnowledgeError.invalid();
        title = title.strip();
        if (title.isBlank() || title.codePointCount(0, title.length()) > 200) throw KnowledgeError.invalid();
        return title;
    }
    public static List<String> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw KnowledgeError.invalid();
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".txt") || name.endsWith(".md"))) throw new KnowledgeError(415, CommonErrorCode.INVALID_ARGUMENT);
        if (file.getSize() > MAX_BYTES) throw KnowledgeError.oversized();
        try (var in = file.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw KnowledgeError.oversized();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            if (text.codePointCount(0, text.length()) > 50_000) throw KnowledgeError.oversized();
            if (text.isBlank() || text.indexOf('\0') >= 0) throw KnowledgeError.invalid();
            return split(text.replace("\r\n", "\n").replace('\r', '\n').strip());
        } catch (CharacterCodingException e) { throw KnowledgeError.invalid(); }
        catch (IOException e) { throw KnowledgeError.invalid(); }
    }
    static List<String> split(String text) {
        int[] points = text.codePoints().toArray();
        var result = new ArrayList<String>();
        for (int start = 0; start < points.length;) {
            int end = Math.min(start + 300, points.length);
            if (end < points.length) {
                int paragraph = -1, sentence = -1;
                // Prefer paragraph/sentence ends; reserve room for useful new content after overlap.
                for (int i = start + 30; i < end; i++) {
                    if (points[i] == '\n' && points[i - 1] == '\n') paragraph = i + 1;
                    if ("。！？.!?；;\n".indexOf(points[i]) >= 0) sentence = i + 1;
                }
                if (paragraph > 0) end = paragraph;
                else if (sentence > 0) end = sentence;
            }
            String chunk = new String(points, start, end - start).strip();
            if (!chunk.isBlank()) result.add(chunk);
            if (result.size() > 200) throw KnowledgeError.oversized();
            if (end == points.length) break;
            start = end - Math.min(30, (end - start) / 2);
        }
        if (result.isEmpty()) throw KnowledgeError.invalid();
        return List.copyOf(result);
    }
}
