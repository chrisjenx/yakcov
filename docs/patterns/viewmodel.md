# ViewModel

**Recommended wiring: the validator lives in the ViewModel.** `FieldValidator` has a
plain constructor and is snapshot-state backed, which means it works *outside*
composition: own it in the VM and the form state survives configuration changes,
`submit()` is unit-testable with no Compose UI, and any composable reading
`field.value`/`field.state` still recomposes automatically.

If your team's convention is strictly-immutable UI state, the value-hoist wiring keeps
the ViewModel pure at the cost of more plumbing per field.

=== "Validator in the ViewModel (recommended)"

    ```kotlin
    --8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/viewmodel/ViewModelExample.kt:vm"
    ```

    ```kotlin
    --8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/viewmodel/ViewModelExample.kt:vm-ui"
    ```

=== "Value-hoist (immutable UiState)"

    ```kotlin
    --8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/viewmodel/ViewModelExample.kt:vm-hoist"
    ```

    ```kotlin
    --8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/viewmodel/ViewModelExample.kt:vm-hoist-ui"
    ```

!!! note "The @Composable boundary"
    The ViewModel never calls the `@Composable` rendering helpers. `field.state.isError`
    is a plain property the VM *can* read; `supportingText()` resolves string resources
    and must be called from the UI, in composition. Keep severity logic in the state
    holder and rendering in the composable.

!!! warning "Construct once, never copy"
    Construct a `FieldValidator` **once** and hold it (a VM/presenter field, DI, or
    `remember`). Don't construct it inside a recomposing body and don't copy it — both
    reset its validation state.

!!! note "Reformatting fields keep the cursor"
    The `ValidatedField` binder above uses the plain `String` overload of
    `OutlinedTextField` for brevity. For fields that **reformat** as the user types
    (currency, phone), bind a local `TextFieldValue` and push only `.text` to
    `field.onValueChange`, so the cursor/selection survives the reformat — otherwise the
    caret jumps to the end on every keystroke. See `ValidatedTextField` in the
    [PresenterSample](https://github.com/chrisjenx/yakcov/blob/main/sample/src/main/java/com/chrisjenx/yakcov/sample/PresenterSample.kt).

## Observability

`FieldValidator` takes an optional `observer` — a `FieldValidatorObserver` fired after
every mutation commits (`ValueChanged` / `Validated` / `Reset`) with the post-mutation
value + state. Handy for analytics, logging, or driving UI like the sample's live
state-flow visualizer
([`StateFlowSample.kt`](https://github.com/chrisjenx/yakcov/blob/main/sample/src/main/java/com/chrisjenx/yakcov/sample/StateFlowSample.kt)).
No event fires at construction; observers must not throw.

On restore-from-persistence, prefer `FieldValidator(initial = restoredDraft, rules,
initialValidate = true)` over calling `validate()` — it re-runs the rules without
emitting a `Validated` event an analytics tap would over-count.

!!! info "Unit-testing presenters"
    If presenter/VM unit tests construct `FieldValidator` or read `.value`/`.state`,
    add `org.jetbrains.compose:runtime` to the test source set — it is not pulled in
    transitively.
