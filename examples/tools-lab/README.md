# tools-lab

Hands-on projects for the playbook's **Tools** tab. Everything here was run on JDK 21; the docs show the real output.

| Folder | Tool(s) | Run |
|---|---|---|
| `diagnostics/TroubledService.java` | jcmd, jstack, JFR, `jfr view`, async-profiler, jq | `java TroubledService.java [pinnedThreads]` then attach tools to its pid |
| `jmh/` | JMH | `mvn package && java -jar target/benchmarks.jar` |
| `jcstress/` | jcstress | `mvn package && java -jar target/jcstress.jar -m quick` |
| `testing-and-analysis/` | JUnit, Awaitility, Lincheck, SpotBugs, Error Prone | `mvn test` · `mvn compile spotbugs:check` (fails on purpose) · `mvn -Perrorprone compile` (fails on purpose) |
