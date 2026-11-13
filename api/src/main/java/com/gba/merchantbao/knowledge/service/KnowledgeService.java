package com.gba.merchantbao.knowledge.service;

import com.gba.merchantbao.auth.enums.AccountRole;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.service.AccountService;
import com.gba.merchantbao.common.error.CommonErrorCode;
import com.gba.merchantbao.common.id.IdGenerator;
import com.gba.merchantbao.knowledge.config.RagProperties;
import com.gba.merchantbao.knowledge.entity.*;
import com.gba.merchantbao.knowledge.exception.KnowledgeError;
import com.gba.merchantbao.knowledge.mapper.KnowledgeMapper;
import com.gba.merchantbao.knowledge.vo.KnowledgeViews.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class KnowledgeService {
    private final KnowledgeMapper mapper;
    private final IdGenerator ids;
    private final RagProperties properties;
    private final KnowledgeEmbedding embedding;
    private final MilvusKnowledgeStore vectors;
    private final PlatformTransactionManager transactions;
    public KnowledgeService(KnowledgeMapper mapper, IdGenerator ids, RagProperties properties,
                            KnowledgeEmbedding embedding, MilvusKnowledgeStore vectors, PlatformTransactionManager transactions) {
        this.mapper = mapper; this.ids = ids; this.properties = properties; this.embedding = embedding;
        this.vectors = vectors; this.transactions = transactions;
    }

    public Document upload(Actor actor, String title, MultipartFile file) {
        AccountService.requireRole(actor, AccountRole.MERCHANT_STAFF);
        if (!properties.isEnabled()) throw new KnowledgeError(503, CommonErrorCode.AGENT_DISABLED);
        var deadline = new ImportDeadline();
        title = KnowledgeText.title(title);
        var texts = KnowledgeText.parse(file);
        var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        long merchant = actor.principal().merchantId();
        var doc = new KnowledgeDocument(ids.nextId(), merchant, title, "PROCESSING", texts.size(), actor.principal().accountId(), now);
        var chunks = new ArrayList<KnowledgeChunk>();
        for (int i = 0; i < texts.size(); i++) chunks.add(new KnowledgeChunk(ids.nextId(), merchant, doc.id(), i + 1, texts.get(i), now));
        // No network calls inside transactions. Failed imports deliberately retain their diagnostic summary.
        transaction(deadline).executeWithoutResult(s -> {
            mapper.insertDocument(doc); chunks.forEach(mapper::insertChunk); deadline.remaining();
        });
        try {
            var embeddings = embedding.embed(texts, deadline);
            vectors.insertAndConfirm(merchant, doc.id(), chunks.stream().map(KnowledgeChunk::id).toList(), embeddings, deadline);
            transaction(deadline).executeWithoutResult(s -> {
                deadline.remaining();
                if (mapper.finish(merchant, doc.id(), "READY") != 1) throw new IllegalStateException("Document publication failed");
                deadline.remaining();
            });
            return Document.from(new KnowledgeDocument(doc.id(), merchant, doc.title(), "READY", texts.size(), doc.createdBy(), now));
        } catch (RuntimeException error) {
            try {
                var failureTx = new TransactionTemplate(transactions); failureTx.setTimeout(5);
                failureTx.executeWithoutResult(s -> mapper.finish(merchant, doc.id(), "FAILED"));
            } catch (RuntimeException storageError) { error.addSuppressed(storageError); }
            // Even if the database cannot mark FAILED, PROCESSING remains invisible.
            throw new KnowledgeError(503, CommonErrorCode.DEPENDENCY_ERROR);
        }
    }
    private TransactionTemplate transaction(ImportDeadline deadline) {
        var tx = new TransactionTemplate(transactions);
        tx.setTimeout(Math.max(1, (int) Math.ceil(deadline.remaining().toMillis() / 1000.0)));
        return tx;
    }
    public Page list(Actor actor, int size, String cursor) {
        AccountService.requireRole(actor, AccountRole.MERCHANT_STAFF);
        if (size < 1 || size > 100) throw KnowledgeError.invalid();
        LocalDateTime before = null; Long beforeId = null;
        if (cursor != null) {
            try {
                if (!cursor.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException();
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII).split(":");
                if (parts.length != 3 || !parts[0].equals("knowledge") || !parts[1].matches("[0-9]{1,15}")
                        || !parts[2].matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
                before = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(parts[1])), ZoneOffset.UTC);
                beforeId = Long.parseLong(parts[2]);
            } catch (RuntimeException e) { throw KnowledgeError.invalid(); }
        }
        var rows = mapper.page(actor.principal().merchantId(), before, beforeId, size + 1);
        boolean more = rows.size() > size;
        var items = rows.subList(0, Math.min(size, rows.size())).stream().map(Document::from).toList();
        String next = null;
        if (more) {
            var last = items.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(("knowledge:" + last.createdAt().toEpochMilli()
                    + ":" + last.documentId().value()).getBytes(StandardCharsets.US_ASCII));
        }
        return new Page(items, next, more);
    }
}
