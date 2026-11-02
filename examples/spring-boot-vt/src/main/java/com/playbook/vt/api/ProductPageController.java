package com.playbook.vt.api;

import com.playbook.vt.application.NotificationService;
import com.playbook.vt.application.ProductPageService;
import com.playbook.vt.concurrency.RequestContext;
import com.playbook.vt.domain.ProductPage;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class ProductPageController {

    private final ProductPageService productPages;
    private final NotificationService notifications;

    public ProductPageController(ProductPageService productPages, NotificationService notifications) {
        this.productPages = productPages;
        this.notifications = notifications;
    }

    @GetMapping("/api/products/{id}/page")
    public ProductPage page(@PathVariable String id,
                            @RequestHeader(value = "X-Request-Id", required = false) String requestId)
            throws Exception {
        String rid = requestId != null ? requestId : UUID.randomUUID().toString();
        MDC.put("requestId", rid);
        try {
            return RequestContext.callWith(rid, () -> productPages.page(id));
        } finally {
            MDC.remove("requestId");
        }
    }

    @PostMapping("/api/orders/{id}/confirm")
    public Map<String, String> confirm(@PathVariable String id) throws Exception {
        return Map.of("result", notifications.sendOrderConfirmation(id).get());
    }

    /** Shows which kind of thread served the request. With virtual threads on: isVirtual=true. */
    @GetMapping("/api/debug/thread")
    public Map<String, Object> thread() {
        Thread t = Thread.currentThread();
        return Map.of("name", t.getName(), "isVirtual", t.isVirtual(), "toString", t.toString());
    }
}
