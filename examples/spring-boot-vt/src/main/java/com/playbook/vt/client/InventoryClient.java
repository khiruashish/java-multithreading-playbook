package com.playbook.vt.client;

import com.playbook.vt.concurrency.Bulkhead;
import com.playbook.vt.concurrency.BulkheadRegistry;
import com.playbook.vt.config.DownstreamBaseUrl;
import com.playbook.vt.domain.Stock;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    private final RestClient http;
    private final Bulkhead bulkhead;
    private final DownstreamBaseUrl baseUrl;

    public InventoryClient(RestClient downstreamRestClient, BulkheadRegistry bulkheads,
                         DownstreamBaseUrl baseUrl) {
        this.http = downstreamRestClient;
        this.bulkhead = bulkheads.get("inventory");
        this.baseUrl = baseUrl;
    }

    public Stock get(String id) throws Exception {
        return bulkhead.call(() -> http.get().uri(baseUrl.get() + "/inventory/{id}", id).retrieve().body(Stock.class));
    }
}
