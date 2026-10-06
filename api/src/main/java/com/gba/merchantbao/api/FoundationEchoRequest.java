package com.gba.merchantbao.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FoundationEchoRequest(
        @NotBlank(message = "message is required")
        @Size(max = 128, message = "message must be at most 128 characters")
        String message) {
}
