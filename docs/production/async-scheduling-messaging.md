# Async, Scheduling & Messaging

<span class="level l2">L2</span> <span class="level l3">L3</span>

## `@Async` and `@Scheduled`

```java title="NotificationService.java (from the sample)"
--8<-- "examples/spring-boot-vt/src/main/java/com/playbook/vt/application/NotificationService.java"
```

### Then vs now

=== "Java 8 / Boot 2"

    ```java
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(10);
        ex.setMaxPoolSize(50);
        ex.setQueueCapacity(500);           // tuned by trial and error
        ex.setThreadNamePrefix("async-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(30);
        ex.initialize();
        return ex;
    }
    ```

=== "Java 21 / Boot 3.2+"

    ```yaml
    spring.threads.virtual.enabled: true
    spring.task.execution.simple.concurrency-limit: 500   # optional cap on concurrent @Async tasks
    spring.task.execution.shutdown.await-termination: true
    spring.task.execution.shutdown.await-termination-period: 30s
    ```

`concurrency-limit` makes `SimpleAsyncTaskExecutor` block the submitter when the limit is reached. That's natural
backpressure, and it replaces the queue-capacity tuning.

## Propagating context into `@Async`

```java
@Bean
TaskDecorator contextPropagatingDecorator() {
    return runnable -> {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        SecurityContext security = SecurityContextHolder.getContext();
        return () -> {
            if (mdc != null) MDC.setContextMap(mdc);
            SecurityContextHolder.setContext(security);
            try { runnable.run(); }
            finally { MDC.clear(); SecurityContextHolder.clearContext(); }
        };
    };
}
```

Spring Boot applies a single `TaskDecorator` bean to the auto-configured executor. With Micrometer
Context Propagation you can use `ContextPropagatingTaskDecorator` instead.

## Kafka listeners on virtual threads

```java
@KafkaListener(topics = "grocery.order.order.placed.v1", groupId = "notification", concurrency = "6")
public void onOrderPlaced(OrderPlaced event) {
    // blocking calls are fine: templates DB, SMS provider HTTP
    var template = templates.find(event.locale());
    smsClient.send(event.phone(), template.render(event));
}
```

* `concurrency` is still the number of **consumers** (one per partition at most). VTs make each consumer's blocking
  work cheap, but they don't add parallelism within a partition.
* For **parallelism within a partition while keeping per-key order**, dispatch each record to a per-key lane
  (see [Case Studies](../system-design/case-studies.md#case-3-ordered-parallel-event-processing)) and commit offsets only
  after the lane finishes. Alternatively, use Confluent's Parallel Consumer.

## Scheduling patterns

| Need | Java 8 | Java 21+ |
|---|---|---|
| Fixed-delay job | `@Scheduled(fixedDelay)` on a pool of 1 → long jobs block every other job | Each run on its own VT. Jobs don't block each other |
| Avoid overlapping runs across pods | ShedLock | ShedLock (unchanged) |
| Many small timers (10k reminders) | `ScheduledThreadPoolExecutor` with care | One VT per reminder: `Thread.sleep(until)` is cheap |

```java
// 10k "send reminder at T" timers, each a sleeping VT (fine for minutes-to-hours horizons in one pod;
// use a durable scheduler such as Quartz, a DB poller, or SQS delay for anything that must survive restarts)
reminders.forEach(r -> Thread.ofVirtual().name("reminder-" + r.id()).start(() -> {
    try {
        Thread.sleep(Duration.between(Instant.now(), r.at()));
        notifier.send(r);
    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
}));
```
