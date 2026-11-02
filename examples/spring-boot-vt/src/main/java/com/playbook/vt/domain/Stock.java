package com.playbook.vt.domain;

public record Stock(String productId, int quantity, boolean known) {
    public static Stock unknown(String productId) {
        return new Stock(productId, 0, false);
    }
}
