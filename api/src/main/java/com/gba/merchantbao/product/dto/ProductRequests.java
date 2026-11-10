package com.gba.merchantbao.product.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Product module request bodies. Identity fields (merchantId/productId/skuId) are rejected:
 * ownership always comes from the authenticated Actor or the URL.
 */
public final class ProductRequests {
    private ProductRequests() { }

    static void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown field: " + name);
    }

    static void requireText(String value, int max, String field) {
        if (value == null || value.strip().isEmpty()) throw new IllegalArgumentException("Missing " + field);
        if (value.strip().codePointCount(0, value.strip().length()) > max) throw new IllegalArgumentException("Too long " + field);
    }

    static void requireCode(String value, String field) {
        requireText(value, 64, field);
        if (!value.strip().matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid " + field);
    }

    public static final class CategoryCreate {
        private Long parentId;
        private String name;
        private Integer sortNo;
        @JsonSetter("parentId") public void setParentId(Long parentId) { this.parentId = parentId; }
        @JsonSetter("name") public void setName(String name) { this.name = name; }
        @JsonSetter("sortNo") public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            requireText(name, 64, "name");
            if (sortNo != null && sortNo < 0) throw new IllegalArgumentException("Invalid sortNo");
        }
        public Long parentId() { return parentId; }
        public String name() { return name == null ? null : name.strip(); }
        public int sortNo() { return sortNo == null ? 0 : sortNo; }
    }

    public static final class CategoryUpdate {
        private String name;
        private Integer sortNo;
        private String status;
        @JsonSetter("name") public void setName(String name) { this.name = name; }
        @JsonSetter("sortNo") public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
        @JsonSetter("status") public void setStatus(String status) { this.status = status; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            if (name != null) requireText(name, 64, "name");
            if (sortNo != null && sortNo < 0) throw new IllegalArgumentException("Invalid sortNo");
            if (status != null && !status.equals("ACTIVE") && !status.equals("INACTIVE"))
                throw new IllegalArgumentException("Invalid status");
        }
        public String name() { return name == null ? null : name.strip(); }
        public Integer sortNo() { return sortNo; }
        public String status() { return status; }
    }

    public static final class ProductCreate {
        private Long categoryId;
        private String productCode;
        private String name;
        private String brand;
        private String description;
        @JsonSetter("categoryId") public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        @JsonSetter("productCode") public void setProductCode(String productCode) { this.productCode = productCode; }
        @JsonSetter("name") public void setName(String name) { this.name = name; }
        @JsonSetter("brand") public void setBrand(String brand) { this.brand = brand; }
        @JsonSetter("description") public void setDescription(String description) { this.description = description; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            requireCode(productCode, "productCode");
            requireText(name, 128, "name");
            if (brand != null) {
                requireText(brand, 64, "brand");
            }
            if (description != null && description.codePointCount(0, description.length()) > 5000)
                throw new IllegalArgumentException("Too long description");
            if (categoryId != null && categoryId <= 0) throw new IllegalArgumentException("Invalid categoryId");
        }
        public Long categoryId() { return categoryId; }
        public String productCode() { return productCode; }
        public String name() { return name == null ? null : name.strip(); }
        public String brand() { return brand == null ? null : brand.strip(); }
        public String description() { return description; }
    }

    public static final class ProductUpdate {
        private Long categoryId;
        private String name;
        private String brand;
        private String description;
        @JsonSetter("categoryId") public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        @JsonSetter("name") public void setName(String name) { this.name = name; }
        @JsonSetter("brand") public void setBrand(String brand) { this.brand = brand; }
        @JsonSetter("description") public void setDescription(String description) { this.description = description; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            if (name != null) requireText(name, 128, "name");
            if (brand != null) requireText(brand, 64, "brand");
            if (description != null && description.codePointCount(0, description.length()) > 5000)
                throw new IllegalArgumentException("Too long description");
            if (categoryId != null && categoryId <= 0) throw new IllegalArgumentException("Invalid categoryId");
        }
        public Long categoryId() { return categoryId; }
        public String name() { return name == null ? null : name.strip(); }
        public String brand() { return brand == null ? null : brand.strip(); }
        public String description() { return description; }
    }

    public static final class SkuCreate {
        private String skuCode;
        private String title;
        private BigDecimal price;
        private Map<String, String> attributes;
        private String status;
        @JsonSetter("skuCode") public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        @JsonSetter("title") public void setTitle(String title) { this.title = title; }
        @JsonSetter("price") public void setPrice(BigDecimal price) { this.price = price; }
        @JsonSetter("attributes") public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
        @JsonSetter("status") public void setStatus(String status) { this.status = status; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            requireCode(skuCode, "skuCode");
            requireText(title, 128, "title");
            if (price == null || price.scale() > 2 || price.signum() < 0)
                throw new IllegalArgumentException("Invalid price");
            if (status != null && !status.equals("ACTIVE") && !status.equals("DISCONTINUED"))
                throw new IllegalArgumentException("Invalid status");
            if (attributes != null) {
                for (var e : attributes.entrySet()) {
                    requireText(e.getKey(), 64, "attribute name");
                    requireText(e.getValue(), 256, "attribute value");
                }
            }
        }
        public String skuCode() { return skuCode; }
        public String title() { return title == null ? null : title.strip(); }
        public BigDecimal price() { return price.setScale(2, java.math.RoundingMode.UNNECESSARY); }
        public Map<String, String> attributes() { return attributes; }
        public String status() { return status == null ? "ACTIVE" : status; }
    }

    public static final class SkuUpdate {
        private String title;
        private BigDecimal price;
        private Map<String, String> attributes;
        private String status;
        @JsonSetter("title") public void setTitle(String title) { this.title = title; }
        @JsonSetter("price") public void setPrice(BigDecimal price) { this.price = price; }
        @JsonSetter("attributes") public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
        @JsonSetter("status") public void setStatus(String status) { this.status = status; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            if (title != null) requireText(title, 128, "title");
            if (price != null && (price.scale() > 2 || price.signum() < 0))
                throw new IllegalArgumentException("Invalid price");
            if (status != null && !status.equals("ACTIVE") && !status.equals("DISCONTINUED"))
                throw new IllegalArgumentException("Invalid status");
            if (attributes != null) {
                for (var e : attributes.entrySet()) {
                    requireText(e.getKey(), 64, "attribute name");
                    requireText(e.getValue(), 256, "attribute value");
                }
            }
        }
        public String title() { return title == null ? null : title.strip(); }
        public BigDecimal price() { return price == null ? null : price.setScale(2, java.math.RoundingMode.UNNECESSARY); }
        public Map<String, String> attributes() { return attributes; }
        public String status() { return status; }
    }

    public static final class AttributesUpdate {
        private Map<String, AttributeEntry> attributes;
        @JsonSetter("attributes") public void setAttributes(Map<String, AttributeEntry> attributes) { this.attributes = attributes; }
        @JsonAnySetter public void reject(String name, Object value) { rejectUnknown(name, value); }
        public void validate() {
            if (attributes == null || attributes.isEmpty()) throw new IllegalArgumentException("Missing attributes");
            if (attributes.size() > 64) throw new IllegalArgumentException("Too many attributes");
            for (var e : attributes.entrySet()) {
                requireText(e.getKey(), 64, "attribute name");
                e.getValue().validate();
            }
        }
        public List<AttributeRow> rows() {
            var rows = new ArrayList<AttributeRow>();
            for (var e : attributes.entrySet()) rows.add(new AttributeRow(e.getKey().strip(), e.getValue().value.strip(), e.getValue().searchable));
            return rows;
        }
        public static final class AttributeEntry {
            private String value;
            private Boolean searchable;
            @JsonSetter("value") public void setValue(String value) { this.value = value; }
            @JsonSetter("searchable") public void setSearchable(Boolean searchable) { this.searchable = searchable; }
            void validate() {
                requireText(value, 512, "attribute value");
            }
        }
        public record AttributeRow(String name, String value, boolean searchable) { }
    }
}
