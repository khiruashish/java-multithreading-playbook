package com.playbook.vt.application;

import com.playbook.vt.client.CatalogClient;
import com.playbook.vt.client.InventoryClient;
import com.playbook.vt.client.PricingClient;
import com.playbook.vt.concurrency.FanOut;
import com.playbook.vt.concurrency.RequestContext;
import com.playbook.vt.config.ConcurrencyProperties;
import com.playbook.vt.domain.Price;
import com.playbook.vt.domain.ProductPage;
import com.playbook.vt.domain.Stock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fan-out aggregator. Catalog is REQUIRED (failure fails the request and cancels siblings); price and stock are
 * OPTIONAL (failure or deadline degrades to a default). All three run concurrently on virtual threads and share one
 * deadline, so total latency is roughly max(catalog, pricing, inventory) instead of their sum.
 *
 * <p>Java 8 equivalent: three CompletableFuture.supplyAsync calls on a sized pool, thenCombine chains, orTimeout and
 * exceptionally handlers, and no sibling cancellation. See docs/production/fan-out-service.md.
 */
@Service
public class ProductPageService {

    private static final Logger log = LoggerFactory.getLogger(ProductPageService.class);

    private final CatalogClient catalog;
    private final PricingClient pricing;
    private final InventoryClient inventory;
    private final FanOut fanOut;
    private final ConcurrencyProperties props;

    public ProductPageService(CatalogClient catalog, PricingClient pricing, InventoryClient inventory,
                              FanOut fanOut, ConcurrencyProperties props) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.inventory = inventory;
        this.fanOut = fanOut;
        this.props = props;
    }

    public ProductPage page(String productId) throws InterruptedException {
        try (var scope = fanOut.open(props.fanOutTimeout())) {
            var product = scope.fork(RequestContext.propagate(() -> catalog.get(productId)));
            var price = scope.forkOptional(RequestContext.propagate(() -> pricing.get(productId)),
                    ex -> { log.warn("pricing degraded: {}", ex.toString()); return Price.unknown(productId); });
            var stock = scope.forkOptional(RequestContext.propagate(() -> inventory.get(productId)),
                    ex -> { log.warn("inventory degraded: {}", ex.toString()); return Stock.unknown(productId); });

            var p = product.get();          // required: throws and cancels siblings on failure
            var pr = price.get();
            var st = stock.get();

            List<String> degraded = new ArrayList<>();
            if (price.degraded()) degraded.add("price");
            if (stock.degraded()) degraded.add("stock");
            return new ProductPage(p, pr, st, List.copyOf(degraded), Thread.currentThread().toString());
        }
    }
}
