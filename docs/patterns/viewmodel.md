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
