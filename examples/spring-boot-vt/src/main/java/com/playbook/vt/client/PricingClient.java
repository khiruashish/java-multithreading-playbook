package com.playbook.vt.client;

import com.playbook.vt.concurrency.Bulkhead;
import com.playbook.vt.concurrency.BulkheadRegistry;
import com.playbook.vt.config.DownstreamBaseUrl;
import com.playbook.vt.domain.Price;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PricingClient {

    private final RestClient http;
    private final Bulkhead bulkhead;
    private final DownstreamBaseUrl baseUrl;

    public PricingClient(RestClient downstreamRestClient, BulkheadRegistry bulkheads,
                         DownstreamBaseUrl baseUrl) {
        this.http = downstreamRestClient;
        this.bulkhead = bulkheads.get("pricing");
        this.baseUrl = baseUrl;
    }

    public Price get(String id) throws Exception {
        return bulkhead.call(() -> http.get().uri(baseUrl.get() + "/pricing/{id}", id).retrieve().body(Price.class));
    }
}
