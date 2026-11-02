package com.playbook.vt.config;

import com.playbook.vt.concurrency.AdmissionControlFilter;
import com.playbook.vt.concurrency.FanOut;
import com.playbook.vt.concurrency.PinningMonitor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class ConcurrencyConfig {

    /** Fan-out helper whose subtasks run on named virtual threads ("fanout-0", "fanout-1", ...). */
    @Bean
    FanOut fanOut() {
        return new FanOut("fanout-");
    }

    /**
     * Bounded PLATFORM pool for CPU-heavy work (image resize, PDF, scoring). Virtual threads are not preempted, so
     * long CPU work on them would starve the carriers that every request depends on.
     */
    @Bean(destroyMethod = "close")
    ExecutorService cpuExecutor(ConcurrencyProperties props, MeterRegistry registry) {
        int size = props.cpuPoolSize() > 0 ? props.cpuPoolSize() : Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(size, size, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(size * 50),
                Thread.ofPlatform().name("cpu-", 0).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return ExecutorServiceMetrics.monitor(registry, pool, "cpu");
    }

    @Bean
    FilterRegistrationBean<AdmissionControlFilter> admissionControlFilter(ConcurrencyProperties props) {
        var reg = new FilterRegistrationBean<>(new AdmissionControlFilter(props.admission().maxInFlight()));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    PinningMonitor pinningMonitor(MeterRegistry registry) {
        return new PinningMonitor(registry, Duration.ofMillis(10));
    }
}
