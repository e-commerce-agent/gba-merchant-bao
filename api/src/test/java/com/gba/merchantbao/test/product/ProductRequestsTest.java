package com.gba.merchantbao.test.product;

import com.gba.merchantbao.product.dto.ProductRequests;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure validation rules; no container or database dependency. */
class ProductRequestsTest {

    private static String json(String body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T parse(Class<T> type, String body) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readerFor(type)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(body);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    @Test void categoryCreateRejectsUnknownFieldsAndRequiresName() {
        assertThatThrownBy(() -> parse(ProductRequests.CategoryCreate.class, json("{\"name\":\"充电器\",\"evil\":1}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parse(ProductRequests.CategoryCreate.class, json("{\"name\":\"  \"}")).validate())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void productCreateValidatesCodeNameAndCategory() {
        var ok = parse(ProductRequests.ProductCreate.class, json(
                "{\"categoryId\":1,\"productCode\":\"P-CHG-65W\",\"name\":\" 65W 充电器 \",\"brand\":\"GbaPower\",\"description\":\"x\"}"));
        ok.validate();
        assertThat(ok.name()).isEqualTo("65W 充电器");
        assertThat(ok.productCode()).isEqualTo("P-CHG-65W");

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.ProductCreate.class, json("{\"productCode\":\"非法 编码!\",\"name\":\"n\"}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.ProductCreate.class, json("{\"productCode\":\"OK-CODE\",\"name\":\"\"}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.ProductCreate.class, json("{\"productCode\":\"OK-CODE\",\"name\":\"n\",\"categoryId\":0}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void skuCreateValidatesPriceScaleAndStatus() {
        var ok = parse(ProductRequests.SkuCreate.class, json(
                "{\"skuCode\":\"SKU-1\",\"title\":\" 黑色 \",\"price\":19.90,\"attributes\":{\"颜色\":\"黑色\"},\"status\":\"ACTIVE\"}"));
        ok.validate();
        assertThat(ok.price().toPlainString()).isEqualTo("19.90");
        assertThat(ok.attributes()).containsEntry("颜色", "黑色");

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.SkuCreate.class, json(
                    "{\"skuCode\":\"SKU-1\",\"title\":\"t\",\"price\":19.999}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.SkuCreate.class, json(
                    "{\"skuCode\":\"SKU-1\",\"title\":\"t\",\"price\":-1}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.SkuCreate.class, json(
                    "{\"skuCode\":\"SKU-1\",\"title\":\"t\",\"price\":1,\"status\":\"PUBLISHED\"}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void attributesUpdateRequiresEntriesAndMapsRows() {
        var ok = parse(ProductRequests.AttributesUpdate.class, json(
                "{\"attributes\":{\"接口类型\":{\"value\":\"USB-C\",\"searchable\":true}}}"));
        ok.validate();
        assertThat(ok.rows()).containsExactly(new ProductRequests.AttributesUpdate.AttributeRow("接口类型", "USB-C", true));

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.AttributesUpdate.class, json("{\"attributes\":{}}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.AttributesUpdate.class, json(
                    "{\"attributes\":{\"接口类型\":{\"value\":\"  \"}}}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void categoryUpdateOnlyAllowsKnownStatuses() {
        var ok = parse(ProductRequests.CategoryUpdate.class, json("{\"status\":\"INACTIVE\",\"sortNo\":3}"));
        ok.validate();
        assertThat(ok.status()).isEqualTo("INACTIVE");
        assertThatThrownBy(() -> {
            var bad = parse(ProductRequests.CategoryUpdate.class, json("{\"status\":\"DELETED\"}"));
            bad.validate();
        }).isInstanceOf(IllegalArgumentException.class);
    }
}
