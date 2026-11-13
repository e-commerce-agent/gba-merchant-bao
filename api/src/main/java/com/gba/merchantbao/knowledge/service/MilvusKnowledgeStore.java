package com.gba.merchantbao.knowledge.service;

import com.gba.merchantbao.knowledge.config.RagProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.grpc.Context;
import io.milvus.v2.client.*;
import io.milvus.v2.common.*;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.vector.request.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Only IDs and vectors live here. MySQL is authoritative for text and READY visibility. */
public class MilvusKnowledgeStore implements AutoCloseable {
    private final RagProperties properties;
    private MilvusClientV2 client;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "rag-rpc-deadline"); t.setDaemon(true); return t;
    });
    public MilvusKnowledgeStore(RagProperties properties) { this.properties = properties; }

    public void initialize() {
        var deadline = new ImportDeadline();
        client = new MilvusClientV2(ConnectConfig.builder().uri(properties.getMilvusUri())
                .token(properties.getMilvusToken()).connectTimeoutMs(5000).rpcDeadlineMs(10000).build());
        client.retryConfig(RetryConfig.builder().maxRetryTimes(1).build());
        rpc(deadline, () -> {
            String name = properties.getCollection();
            if (!client.hasCollection(HasCollectionReq.builder().collectionName(name).build())) {
                var schema = client.createSchema();
                schema.setEnableDynamicField(false);
                schema.addField(AddFieldReq.builder().fieldName("chunk_id").dataType(DataType.Int64).isPrimaryKey(true).autoID(false).build());
                schema.addField(AddFieldReq.builder().fieldName("merchant_id").dataType(DataType.Int64).build());
                schema.addField(AddFieldReq.builder().fieldName("document_id").dataType(DataType.Int64).build());
                schema.addField(AddFieldReq.builder().fieldName("embedding").dataType(DataType.FloatVector)
                        .dimension(properties.getEmbeddingDimension()).build());
                client.createCollection(CreateCollectionReq.builder().collectionName(name).collectionSchema(schema)
                        .indexParams(List.of(IndexParam.builder().fieldName("embedding").indexName("embedding_cosine")
                                .indexType(IndexParam.IndexType.AUTOINDEX).metricType(IndexParam.MetricType.COSINE).build())).build());
            }
            var description = client.describeCollection(DescribeCollectionReq.builder().collectionName(name).build());
            var schema = description.getCollectionSchema();
            if (Boolean.TRUE.equals(description.getAutoID()) || schema.isEnableDynamicField()
                    || !"chunk_id".equals(description.getPrimaryFieldName()) || schema.getFieldSchemaList().size() != 4)
                throw new IllegalStateException("Incompatible RAG collection schema");
            for (String field : List.of("chunk_id", "merchant_id", "document_id")) {
                if (schema.getField(field) == null || schema.getField(field).getDataType() != DataType.Int64)
                    throw new IllegalStateException("Incompatible RAG collection identifiers");
            }
            var vector = schema.getField("embedding");
            if (vector == null || vector.getDataType() != DataType.FloatVector
                    || !Objects.equals(vector.getDimension(), properties.getEmbeddingDimension()))
                throw new IllegalStateException("Incompatible RAG collection dimension");
            var indexes = client.describeIndex(DescribeIndexReq.builder().collectionName(name).fieldName("embedding").build());
            if (indexes.getIndexDescriptions().stream().noneMatch(i -> i.getMetricType() == IndexParam.MetricType.COSINE))
                throw new IllegalStateException("Incompatible RAG collection metric");
            client.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
            return null;
        });
    }

    public void insertAndConfirm(long merchantId, long documentId, List<Long> chunkIds,
                                 List<List<Float>> vectors, ImportDeadline deadline) {
        if (vectors.size() != chunkIds.size() || chunkIds.isEmpty()) throw new IllegalArgumentException("Invalid vectors");
        var data = new ArrayList<JsonObject>();
        for (int i = 0; i < chunkIds.size(); i++) {
            var row = new JsonObject();
            row.addProperty("chunk_id", chunkIds.get(i)); row.addProperty("merchant_id", merchantId);
            row.addProperty("document_id", documentId);
            var vector = new JsonArray(); vectors.get(i).forEach(vector::add); row.add("embedding", vector); data.add(row);
        }
        rpc(deadline, () -> client.insert(InsertReq.builder().collectionName(properties.getCollection()).data(data).build()));
        var rows = rpc(deadline, () -> client.query(QueryReq.builder().collectionName(properties.getCollection())
                .filter("merchant_id == " + merchantId + " && document_id == " + documentId)
                .outputFields(List.of("chunk_id")).limit(201).consistencyLevel(ConsistencyLevel.STRONG).build()));
        var actual = new HashSet<Long>();
        rows.getQueryResults().forEach(row -> actual.add(((Number) row.getEntity().get("chunk_id")).longValue()));
        if (!actual.equals(new HashSet<>(chunkIds))) throw new IllegalStateException("Vectors not queryable");
        deadline.remaining();
    }
    private <T> T rpc(ImportDeadline deadline, Supplier<T> action) {
        try (var context = Context.current().withDeadlineAfter(deadline.remaining().toNanos(), TimeUnit.NANOSECONDS, timer)) {
            return context.call(action::get);
        } catch (RuntimeException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Vector operation failed", e); }
    }
    @Override public void close() {
        if (client != null) client.close();
        timer.shutdownNow();
    }
}
