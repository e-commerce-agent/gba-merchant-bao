package com.gba.merchantbao.test;

import com.gba.merchantbao.common.privacy.PhoneMasker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FoundationContractTest {

    @Test
    void phoneIsMaskedAtPresentationBoundary() {
        assertThat(PhoneMasker.maskPhone("13800138000")).isEqualTo("138****8000");
        assertThat(PhoneMasker.maskPhone(" 13800138000 ")).isEqualTo("138****8000");
        assertThat(PhoneMasker.maskPhone(null)).isNull();
    }

    @Test
    void specialProtocolContractIsDocumented() throws IOException {
        Path contractPath = Path.of("../docs/initialization/2026-10-06-产出文档/API契约交付记录.md");
        String contract = Files.readString(contractPath, StandardCharsets.UTF_8);
        assertThat(contract).contains("413", "202", "token", "citation", "tool_call", "done", "error");
    }
}
