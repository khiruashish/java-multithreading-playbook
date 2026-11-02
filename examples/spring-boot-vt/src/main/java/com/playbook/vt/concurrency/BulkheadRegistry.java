package com.playbook.vt.concurrency;

import com.playbook.vt.config.ConcurrencyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One bulkhead per downstream dependency, configured under {@code playbook.concurrency.bulkheads}. */
@Component
public class BulkheadRegistry {

    private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();

    public BulkheadRegistry(ConcurrencyProperties props, MeterRegistry registry) {
        props.bulkheads().forEach((name, cfg) ->
                bulkheads.put(name, new Bulkhead(name, cfg.maxConcurrent(), cfg.maxWait(), registry)));
    }

    public Bulkhead get(String name) {
        Bulkhead b = bulkheads.get(name);
        if (b == null) throw new IllegalArgumentException("No bulkhead configured for '" + name + "'");
        return b;
    }
}
