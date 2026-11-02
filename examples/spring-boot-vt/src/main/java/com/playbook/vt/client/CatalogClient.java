package com.playbook.vt.client;

import com.playbook.vt.concurrency.Bulkhead;
import com.playbook.vt.concurrency.BulkheadRegistry;
import com.playbook.vt.config.DownstreamBaseUrl;
import com.playbook.vt.domain.Product;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Plain blocking HTTP call guarded by the "catalog" bulkhead. */
@Component
public class CatalogClient {

    private final RestClient http;
    private final Bulkhead bulkhead;
    private final DownstreamBaseUrl baseUrl;

    public CatalogClient(RestClient downstreamRestClient, BulkheadRegistry bulkheads,
                         DownstreamBaseUrl baseUrl) {
        this.http = downstreamRestClient;
        this.bulkhead = bulkheads.get("catalog");
        this.baseUrl = baseUrl;
    }

    public Product get(String id) throws Exception {
        return bulkhead.call(() -> http.get().uri(baseUrl.get() + "/catalog/{id}", id).retrieve().body(Product.class));
    }
}
