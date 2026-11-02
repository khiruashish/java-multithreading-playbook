package com.playbook.vt.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductPageIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    @Value("${local.server.port}")
    int port;

    RestClient client;

    @BeforeEach
    void setUp() {
        client = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> { }) // let tests inspect errors
                .build();
    }

    @Test
    void requestsAreServedByVirtualThreads() {
        Map<String, Object> body = client.get().uri("/api/debug/thread").retrieve().body(MAP);
        assertThat(body).containsEntry("isVirtual", true);
    }

    @Test
    void aggregatesAllThreeDownstreamsInParallel() {
        long start = System.nanoTime();
        Map<String, Object> page = client.get().uri("/api/products/p-1/page").retrieve().body(MAP);
        long ms = (System.nanoTime() - start) / 1_000_000;

        assertThat(page).containsKeys("product", "price", "stock");
        assertThat((List<?>) page.get("degraded")).isEmpty();
        // 120 + 80 + 60 = 260 ms sequential; parallel is ~max = 120 ms (+ overhead, first call warms up)
        assertThat(ms).isLessThan(1_500);
    }

    @Test
    void slowPricingDegradesInsteadOfFailing() {
        Map<String, Object> page = client.get().uri("/api/products/slow-1/page").retrieve().body(MAP);
        assertThat((List<Object>) page.get("degraded")).containsExactly("price");
        assertThat((Map<String, Object>) page.get("price")).containsEntry("available", false);
    }

    @Test
    void failingInventoryDegradesInsteadOfFailing() {
        Map<String, Object> page = client.get().uri("/api/products/err-1/page").retrieve().body(MAP);
        assertThat((List<Object>) page.get("degraded")).containsExactly("stock");
    }

    @Test
    void missingProductIs404ProblemDetail() {
        var response = client.get().uri("/api/products/missing-1/page").retrieve().toEntity(MAP);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    @Test
    void asyncRunsOnVirtualThread() {
        Map<String, Object> body = client.post().uri("/api/orders/o-1/confirm").retrieve().body(MAP);
        assertThat((String) body.get("result")).isEqualTo("sent:o-1 on virtual=true");
    }

    @Test
    void handlesHundredsOfConcurrentRequestsWithoutErrorsOrHangs() throws Exception {
        for (int i = 0; i < 20; i++) {                       // warm up JIT and HTTP connections
            client.get().uri("/api/products/warm-{i}/page", i).retrieve().toBodilessEntity();
        }
        long start = System.nanoTime();
        List<Future<Integer>> statuses = new ArrayList<>();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 300; i++) {
                int n = i;
                statuses.add(ex.submit(() -> client.get().uri("/api/products/p-{n}/page", n)
                        .retrieve().toBodilessEntity().getStatusCode().value()));
            }
        }
        Map<Integer, Long> byStatus = statuses.stream()
                .collect(Collectors.groupingBy(Future::resultNow, Collectors.counting()));
        long seconds = (System.nanoTime() - start) / 1_000_000_000;
        // 200 = served; 503 = shed by a full bulkhead / admission control; 504 = fan-out deadline hit.
        // Under overload the service must degrade with explicit, fast failures: never 500s, never hang.
        assertThat(byStatus.keySet()).as("statuses %s", byStatus).isSubsetOf(200, 503, 504);
        assertThat(byStatus.getOrDefault(200, 0L)).as("statuses %s", byStatus).isGreaterThan(0);
        assertThat(seconds).as("bounded by deadlines, no hang").isLessThan(15);
    }
}
