# ViewModel

**Recommended wiring: the field owns a `TextFieldState`, and the validator lives in the
ViewModel and follows it.** The VM holds one `TextFieldState` per field (the editing buffer) and a
`FieldValidator` per field. `textChanges()` feeds the text to the validator, one way.
`FieldValidator` has a plain constructor and is snapshot-state backed, so the form state survives
configuration changes, `submit()` is unit-testable with no Compose UI, and composables reading
`field.state` recompose automatically.

If your team's convention is strictly-immutable UI state, the second tab keeps a pure `UiState`
of `String`s and `FieldValidationState`s, fed the same way.

!!! warning "Don't render the text from VM state"
    `OutlinedTextField(value = state.email, onValueChange = vm::onEmailChange)` with a
    `StateFlow` + `collectAsState()` is the textbook skipped-input setup. `collectAsState()`
    delivers on a later frame, the field is reset to the stale text, and fast typing or a paste
    loses characters. Compose 1.13 deprecates that text-field shape. Let the field own the text,
    and have the VM observe it. See the
    [decision record](../decisions/textfieldstate.md#why-text-that-round-trips-through-a-model-loses-input).

=== "Validator in the ViewModel (recommended)"

    ```kotlin
    --8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/viewmodel/ViewModelExample.kt:vm"
    ```

    ```kotlin
    --8<-- "docs-examples/src/commonMain/kotlin/com/chrisjenx/yakcov/docs/viewmodel/ViewModelExample.kt:vm-ui"
    ```

=== "Immutable UiState"

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

!!! note "Formatting fields"
    Fields that reformat as the user types (currency, phone) use `inputTransformation` to restrict
    input and `outputTransformation` to change how it's displayed. The `TextFieldState` keeps the
    caret right, and the validator sees the stored text (after the input transformation, before
    the output one). There's no `TextFieldValue` or caret clamping to hand-roll. See
    [Transformations](../decisions/textfieldstate.md#5-transformations-raw-or-transformed-text).

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
