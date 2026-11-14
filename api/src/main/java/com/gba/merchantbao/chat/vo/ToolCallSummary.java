package com.gba.merchantbao.chat.vo;

/** Fixed public summary only; parameters and business results have no representation here. */
public record ToolCallSummary(String name,String status) {
    public ToolCallSummary {
        if(!"lookup_order".equals(name)||!"SUCCEEDED".equals(status))
            throw new IllegalArgumentException("Invalid tool summary");
    }
}
