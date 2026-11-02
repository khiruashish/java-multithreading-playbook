# Modern Concurrency (Java 21 → 25)

<span class="level l3">L3</span> <span class="level l4">L4</span>

Virtual threads make threads cheap. The following two APIs make **using many threads safe**:

| API | Status | Replaces | Gives you |
|---|---|---|---|
| **Structured concurrency** (`StructuredTaskScope`) | Preview 21–26 | `ExecutorService` + `Future` for fan-out | Subtask lifetimes bounded by a lexical scope, automatic cancellation, error propagation, observable task trees |
| **Scoped values** (`ScopedValue`) | Preview 21–24, **final 25** | `ThreadLocal` for context | Immutable, bounded-lifetime, cheaply inherited context |

Also on this tab: [Virtual Threads vs Reactive](vt-vs-reactive.md) and the [Flow API](flow-api.md).

## Enabling preview features

```bash
# Compile & run (JDK 21 example)
javac --release 21 --enable-preview Main.java
java --enable-preview Main
# Single-file launch
java --enable-preview --source 21 Main.java
```

```kotlin
// Gradle Kotlin DSL
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("--enable-preview") }
tasks.withType<Test>().configureEach { jvmArgs("--enable-preview") }
tasks.withType<JavaExec>().configureEach { jvmArgs("--enable-preview") }
```

!!! warning "Preview means the API can change"
    Code written against the 21–24 `StructuredTaskScope` API (`ShutdownOnFailure`) **does not compile** on 25, where
    the API became `StructuredTaskScope.open(Joiner)`. Isolate preview APIs behind one internal class.
