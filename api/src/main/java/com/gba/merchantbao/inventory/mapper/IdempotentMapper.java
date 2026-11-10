package com.gba.merchantbao.inventory.mapper;

import com.gba.merchantbao.inventory.po.IdempotentRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IdempotentMapper {

    /**
     * 插入幂等占位记录，唯一索引 uk_idempotent_key 兜底防重
     */
    int insert(IdempotentRecordPO po);

    IdempotentRecordPO selectByIdempotentKey(@Param("idempotentKey") String idempotentKey);

    int updateStatus(@Param("idempotentKey") String idempotentKey,
                     @Param("status") String status,
                     @Param("errorMsg") String errorMsg);

    /**
     * CAS 抢占：只允许把 FAILED 记录重置为 PROCESSING，返回 1 表示抢占成功
     */
    int casFailedToProcessing(@Param("idempotentKey") String idempotentKey);
}
