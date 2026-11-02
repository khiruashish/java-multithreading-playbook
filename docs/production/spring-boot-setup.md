# Enabling Virtual Threads in Spring Boot

<span class="level l2">L2</span>

## Version matrix

| Spring Boot | Spring Framework | Java | VT support |
|---|---|---|---|
| 2.x | 5.x | 8–17 | None. Upgrade first |
| 3.0–3.1 | 6.0 | 17+ | Manual: customize `TomcatProtocolHandlerCustomizer` |
| **3.2+** | 6.1+ | 21+ | `spring.threads.virtual.enabled=true` |
| **4.x** | 7.x | 17+ (21+ for VTs) | Same property. Jakarta EE 11, Jackson 3 |

## The one property

```yaml title="application.yml (from examples/spring-boot-vt)"
--8<-- "examples/spring-boot-vt/src/main/resources/application.yml"
```

With `spring.threads.virtual.enabled=true`, Spring Boot configures:

| Component | What runs on virtual threads |
|---|---|
| Tomcat / Jetty | Every HTTP request handler |
| `applicationTaskExecutor` | `@Async` methods and `AsyncTaskExecutor` injection. A `SimpleAsyncTaskExecutor` that starts a new VT per task |
| Task scheduler | `@Scheduled` methods (`SimpleAsyncTaskScheduler`) |
| Kafka / RabbitMQ / JMS / Pulsar listener containers | Listener invocations |
| Spring Data Redis | Cluster command executor |
| Spring for GraphQL / Spring WebMVC async | Async request processing |

!!! warning "`server.tomcat.threads.max` no longer limits concurrency"
    With VTs on, Tomcat doesn't use its worker pool for requests. Limit concurrency with an admission filter
    ([Resilience](resilience.md)) and with `server.tomcat.max-connections` (default 8192).

## Doing it manually (Spring Boot 3.0/3.1, or custom containers)

```java
@Bean
TomcatProtocolHandlerCustomizer<?> virtualThreadsForTomcat() {
    return handler -> handler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
}

@Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
AsyncTaskExecutor applicationTaskExecutor() {
    return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
}
```

## Verifying it's on

```bash
curl -s localhost:8080/api/debug/thread
# {"name":"tomcat-handler-12","isVirtual":true,"toString":"VirtualThread[#65,tomcat-handler-12]/runnable@ForkJoinPool-1-worker-3"}
```

The sample's integration test asserts this:

```java title="ProductPageIntegrationTest.java (excerpt)"
@Test
void requestsAreServedByVirtualThreads() {
    Map<String, Object> body = client.get().uri("/api/debug/thread").retrieve().body(MAP);
    assertThat(body).containsEntry("isVirtual", true);
}
```

## Concurrency beans in the sample

```java title="ConcurrencyConfig.java"
--8<-- "examples/spring-boot-vt/src/main/java/com/playbook/vt/config/ConcurrencyConfig.java"
```

```java title="HttpClientConfig.java"
--8<-- "examples/spring-boot-vt/src/main/java/com/playbook/vt/config/HttpClientConfig.java"
```

## Java 25 with preview features (optional)

To use `StructuredTaskScope` in a Boot app, enable preview for compile, test, and run:

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration><compilerArgs><arg>--enable-preview</arg></compilerArgs></configuration>
</plugin>
<plugin>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration><argLine>--enable-preview</argLine></configuration>
</plugin>
```

```bash
java --enable-preview -jar app.jar
```

Keep preview usage behind one facade (like the sample's `FanOut`) so a JDK upgrade touches one class.
