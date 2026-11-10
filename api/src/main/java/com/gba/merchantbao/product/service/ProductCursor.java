package com.gba.merchantbao.product.service;

import com.gba.merchantbao.product.exception.ProductError;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

/** Position only; every read still reapplies the trusted merchant scope. */
final class ProductCursor {
    private static final String PUBLIC = "public";
    private static final String STAFF = "staff";
    private ProductCursor() { }

    static String encodePublic(LocalDateTime updatedAt, long id) {
        return encode(PUBLIC, updatedAt, id);
    }
    static String encodeStaff(LocalDateTime updatedAt, long id) {
        return encode(STAFF, updatedAt, id);
    }
    static String[] decodePublic(String cursor) { return decode(PUBLIC, cursor); }
    static String[] decodeStaff(String cursor) { return decode(STAFF, cursor); }

    private static String encode(String kind, LocalDateTime updatedAt, long id) {
        String raw = kind + ":" + updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
    }
    private static String[] decode(String kind, String cursor) {
        try {
            if (cursor == null) return null;
            if (!cursor.matches("[A-Za-z0-9_-]{1,512}")) throw ProductError.invalid();
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            if (!decoded.startsWith(kind + ":")) throw ProductError.invalid();
            String[] parts = decoded.substring(kind.length() + 1).split(":");
            if (parts.length != 2 || !parts[0].matches("[0-9]{1,15}") || !parts[1].matches("[1-9][0-9]{0,18}"))
                throw ProductError.invalid();
            return parts;
        } catch (RuntimeException e) {
            throw ProductError.invalid();
        }
    }
}
