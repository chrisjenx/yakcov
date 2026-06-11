# Yakcov

**Y**et **A**nother **K**otlin **CO**mpose **V**alidation library.

Yakcov makes `TextField` (and any other input) validation painless in Compose
Multiplatform. Validators are backed by Compose snapshot state, so your UI reacts
automatically; validation outcomes are severity-ranked (`ERROR`, `WARNING`, `INFO`,
`SUCCESS`); and the same rules run on **Android, JVM, JS, Wasm, and iOS**.

!!! tip "These docs can't lie"
    Every code block on this site is pulled from the
    [`docs-examples`](https://github.com/chrisjenx/yakcov/tree/main/docs-examples)
    module, which compiles in CI against the current library API. If an example
    here stopped working, the build would fail before the docs could ship.

## Install

Yakcov publishes all targets to
[Maven Central](https://central.sonatype.com/artifact/com.chrisjenx.yakcov/library) —
add the common artifact and Gradle resolves the right target per platform:

```kotlin
commonMain {
    dependencies {
        implementation("com.chrisjenx.yakcov:library:${version}")
    }
}
```

## Yakcov in 30 seconds

```kotlin
--8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/basic/BasicScreen.kt:basic"
```

## Where next

- [Getting started](getting-started.md) — rules, error display, `validationConfig`, form-level submit
- Pattern guides, each showing **all the layers** (UI, state holder, validation wiring):
    - [Basic screen](patterns/basic.md) — no state holder
    - [ViewModel](patterns/viewmodel.md) — validator owned by the ViewModel
    - [MVI / UDF](patterns/mvi.md) — pure reducer, immutable model
    - [Circuit](patterns/circuit.md) — validator owned by the presenter
- Recipes: [custom rules](recipes/custom-rules.md), [phone validation](recipes/phone.md)
