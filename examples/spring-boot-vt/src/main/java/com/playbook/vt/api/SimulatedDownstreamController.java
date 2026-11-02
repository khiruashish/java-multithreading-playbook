package com.playbook.vt.api;

import com.playbook.vt.config.SimulationProperties;
import com.playbook.vt.domain.Price;
import com.playbook.vt.domain.Product;
import com.playbook.vt.domain.Stock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Stand-ins for three remote services so the sample runs on its own. Each "call" sleeps to simulate IO latency.
 * Product ids starting with "slow-" make pricing exceed the deadline, "err-" make inventory fail, and "missing-"
 * makes catalog return 404.
 */
@RestController
@RequestMapping("/sim")
public class SimulatedDownstreamController {

    private final SimulationProperties sim;

    public SimulatedDownstreamController(SimulationProperties sim) {
        this.sim = sim;
    }

    @GetMapping("/catalog/{id}")
    public Product catalog(@PathVariable String id) throws InterruptedException {
        Thread.sleep(sim.catalogLatency());
        if (id.startsWith("missing-")) throw new ResponseStatusException(NOT_FOUND, "no product " + id);
        return new Product(id, "Organic Bananas (" + id + ")", "fruits");
    }

    @GetMapping("/pricing/{id}")
    public Price pricing(@PathVariable String id) throws InterruptedException {
        Thread.sleep(id.startsWith("slow-") ? Duration.ofSeconds(5) : sim.pricingLatency());
        return new Price(id, 4_900, "INR", true); // 49.00 INR in paise
    }

    @GetMapping("/inventory/{id}")
    public Stock inventory(@PathVariable String id) throws InterruptedException {
        Thread.sleep(sim.inventoryLatency());
        if (id.startsWith("err-")) throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "inventory down");
        return new Stock(id, 42, true);
    }
}
