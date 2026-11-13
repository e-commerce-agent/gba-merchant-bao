package com.gba.merchantbao.knowledge.exception;

import com.gba.merchantbao.common.error.CommonErrorCode;

public class KnowledgeError extends RuntimeException {
    private final int status;
    private final CommonErrorCode code;
    public KnowledgeError(int status, CommonErrorCode code) { super(code.getMessage()); this.status = status; this.code = code; }
    public int status() { return status; }
    public CommonErrorCode code() { return code; }
    public static KnowledgeError invalid() { return new KnowledgeError(400, CommonErrorCode.INVALID_ARGUMENT); }
    public static KnowledgeError oversized() { return new KnowledgeError(413, CommonErrorCode.INVALID_ARGUMENT); }
}
