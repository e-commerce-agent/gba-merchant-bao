package com.gba.merchantbao.common.privacy;

public final class PhoneMasker {

    private PhoneMasker() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String normalized = phone.trim();
        if (normalized.length() <= 7) {
            return "***";
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
    }
}
