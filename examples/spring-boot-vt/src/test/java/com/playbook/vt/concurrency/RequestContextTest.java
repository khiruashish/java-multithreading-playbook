package com.playbook.vt.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextTest {

    @Test
    void contextIsBoundOnlyInsideScopeAndPropagatesToForkedThreads() throws Exception {
        assertThat(RequestContext.requestId()).isEqualTo("none");

        String inChild = RequestContext.callWith("req-42", () -> {
            try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
                String plain = ex.submit(RequestContext::requestId).get();
                String propagated = ex.submit(RequestContext.propagate(RequestContext::requestId)).get();
                assertThat(plain).as("executors do not inherit context").isEqualTo("none");
                return propagated;
            }
        });

        assertThat(inChild).isEqualTo("req-42");
        assertThat(RequestContext.requestId()).as("cleared after scope").isEqualTo("none");
    }
}
