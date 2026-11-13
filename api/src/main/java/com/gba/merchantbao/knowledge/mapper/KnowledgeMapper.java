package com.gba.merchantbao.knowledge.mapper;

import com.gba.merchantbao.knowledge.entity.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeMapper {
    @Insert("INSERT INTO knowledge_document(id,merchant_id,title,status,chunk_count,created_by,created_at) "
            + "VALUES(#{id},#{merchantId},#{title},#{status},#{chunkCount},#{createdBy},#{createdAt})")
    int insertDocument(KnowledgeDocument document);

    @Insert("INSERT INTO knowledge_chunk(id,merchant_id,document_id,chunk_no,content,created_at) "
            + "VALUES(#{id},#{merchantId},#{documentId},#{chunkNo},#{content},#{createdAt})")
    int insertChunk(KnowledgeChunk chunk);

    @Update("UPDATE knowledge_document SET status=#{status} WHERE merchant_id=#{merchantId} "
            + "AND id=#{documentId} AND status='PROCESSING'")
    int finish(@Param("merchantId") long merchantId, @Param("documentId") long documentId, @Param("status") String status);

    @Select("<script>SELECT id,merchant_id,title,status,chunk_count,created_by,created_at FROM knowledge_document "
            + "WHERE merchant_id=#{merchantId} <if test='before != null'>AND (created_at &lt; #{before} OR "
            + "(created_at=#{before} AND id &lt; #{beforeId}))</if> ORDER BY created_at DESC,id DESC LIMIT #{size}</script>")
    List<KnowledgeDocument> page(@Param("merchantId") long merchantId, @Param("before") LocalDateTime before,
                                  @Param("beforeId") Long beforeId, @Param("size") int size);

    /** Read by exact IDs only. No vector search here; publication gate for later retrieval. */
    @Select("<script>SELECT c.id,c.merchant_id,c.document_id,c.chunk_no,c.content,c.created_at FROM knowledge_chunk c "
            + "JOIN knowledge_document d ON d.merchant_id=c.merchant_id AND d.id=c.document_id "
            + "WHERE c.merchant_id=#{merchantId} AND d.status='READY' AND c.id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<KnowledgeChunk> readyChunks(@Param("merchantId") long merchantId, @Param("ids") List<Long> ids);

    @Select("<script>SELECT c.document_id,c.id AS chunk_id,d.title,c.chunk_no,c.content FROM knowledge_chunk c "
            + "JOIN knowledge_document d ON d.merchant_id=c.merchant_id AND d.id=c.document_id "
            + "WHERE c.merchant_id=#{merchantId} AND d.status='READY' AND c.id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<ReadyKnowledgeChunk> readyReferences(@Param("merchantId") long merchantId, @Param("ids") List<Long> ids);
}
