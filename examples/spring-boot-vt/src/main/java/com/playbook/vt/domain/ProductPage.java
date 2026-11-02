package com.playbook.vt.domain;

import java.util.List;

/** Aggregated view. {@code degraded} lists the optional parts that fell back to defaults. */
public record ProductPage(Product product, Price price, Stock stock, List<String> degraded, String servedBy) {}
