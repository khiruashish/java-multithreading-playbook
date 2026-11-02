package com.playbook.vt.domain;

/** Money in minor units (paise / cents). Never use floating point for money. */
public record Price(String productId, long amountMinor, String currency, boolean available) {
    public static Price unknown(String productId) {
        return new Price(productId, 0, "INR", false);
    }
}
