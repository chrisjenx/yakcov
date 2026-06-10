package com.chrisjenx.yakcov

/**
 * Observes [FieldValidator] mutations. Pass to the [FieldValidator] constructor; `null` (the
 * default) costs one null-check per mutation and nothing else.
 *
 * Events fire **after** the mutation's snapshot commit, so the observer always reads a consistent
 * post-mutation `value`/`state` pair — never a torn one. No event fires at construction (including
 * `initialValidate = true`): the caller built the initial state and already knows it.
 *
 * Observers **must not throw** — an exception propagates to whoever called the mutator
 * (`onValueChange`/`validate`/`reset`); it is not swallowed.
 *
 * Useful for analytics, logging, or driving UI — e.g. the sample app's live state-flow
 * visualizer (`StateFlowSample.kt`).
 */
fun interface FieldValidatorObserver<V> {
    fun onEvent(event: FieldValidatorEvent<V>)
}

/**
 * A [FieldValidator] mutation, named after the method vocabulary: `onValueChange` →
 * [ValueChanged], `validate`/`onFocusLost` → [Validated], `reset` → [Reset]. Carries the **after**
 * picture only — observers wanting deltas track the previous event themselves.
 */
sealed interface FieldValidatorEvent<V> {
    /** The draft after the mutation. */
    val value: V

    /** The validation state after the mutation. */
    val state: FieldValidationState

    /** The draft changed via `onValueChange` (reveal state preserved — no error pop). */
    data class ValueChanged<V>(
        override val value: V,
        override val state: FieldValidationState,
    ) : FieldValidatorEvent<V>

    /** Errors were revealed via `validate()`/`onFocusLost()` (also via `List.validate()`). */
    data class Validated<V>(
        override val value: V,
        override val state: FieldValidationState,
    ) : FieldValidatorEvent<V>

    /** The draft was re-seeded via `reset()`/`reset(value)` (state honors `initialValidate`). */
    data class Reset<V>(
        override val value: V,
        override val state: FieldValidationState,
    ) : FieldValidatorEvent<V>
}
