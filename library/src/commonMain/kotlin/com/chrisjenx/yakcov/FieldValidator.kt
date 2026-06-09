package com.chrisjenx.yakcov

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot

/**
 * A headless, presenter-ownable field validator for snapshot-presenter (Molecule) screens.
 *
 * Unlike [ValueValidator] (constructed in composition via `rememberTextFieldValueValidator` /
 * `rememberGenericValueValidator`), this has a plain constructor so a presenter can own it as the
 * single source of truth for a field's draft. No `@Composable` construction, no coroutine scope,
 * no `Modifier` dependency.
 *
 * [value] stays snapshot-backed so a composable reading `validator.value` recomposes, but the owner
 * is the presenter. [state] is a plain [FieldValidationState].
 *
 * ### Lifecycle
 * Construct **once** and hold it (a presenter class, a DI graph, or a `remember`/retained slot).
 * Never construct it inside a recomposing `@Composable` presenter body (e.g. a Molecule
 * `launchMolecule` body) — like [ValueValidator], a new instance resets all draft + validation
 * state. Do not copy it; mutate via [onValueChange]/[onBlur]/[reveal]/[reset].
 *
 * ### Threading
 * The mutators write Compose snapshot state and should be called on the presenter's confined/main
 * thread. [onValueChange] and [reset] commit their draft + state writes inside a single mutable snapshot,
 * so a concurrent observer never sees a torn `value`/`state` pair.
 *
 * ### Not for reducer-MVI Models
 * This is a mutable, reference-identity holder. Do **not** place it inside an immutable data-class
 * `Model` used for structural diffing/time-travel — `copy()` aliases the reference. For reducer-MVI,
 * store the draft value + the immutable [FieldValidationState] in the Model and recompute with
 * [toFieldState] inside `reduce()`.
 *
 * ## Usage
 * ```
 * class CheckoutPresenter {
 *     val email = FieldValidator("", listOf(Required, Email))
 *     // CurrencyAmount is your own ValueValidatorRule — compose built-ins with your domain rules
 *     val amount = FieldValidator("", listOf(Required, CurrencyAmount))
 *     fun submit(): Boolean = listOf(email, amount).allValid()   // reveals, then checks
 * }
 * ```
 *
 * @param initial the starting draft value (also the value [reset] re-seeds to).
 * @param rules the rules evaluated on every change/blur/reveal.
 * @param initialValidate when true, validates and reveals errors immediately (and on [reset]).
 */
@Stable
class FieldValidator<V>(
    private val initial: V,
    private val rules: List<ValueValidatorRule<V>>,
    private val initialValidate: Boolean = false,
) {
    /** The field draft — the single source of truth. Mutate it via [onValueChange]. */
    var value: V by mutableStateOf(initial)
        private set

    /** The current plain validation output. Readable without entering composition. */
    var state: FieldValidationState by mutableStateOf(seedState(initial))
        private set

    /** Update the draft and revalidate, keeping the current reveal state (no error pop while typing). */
    fun onValueChange(value: V) = Snapshot.withMutableSnapshot {
        this.value = value
        revalidate(showError = state.showError)
    }

    /** Revalidate and reveal errors — call when the field loses focus. Alias for [reveal]. */
    fun onBlur() = reveal()

    /** Revalidate and force errors visible — call at submit time. */
    fun reveal() {
        revalidate(showError = true)
    }

    /** Re-seed the draft to [initial] and reset validation (honoring `initialValidate`). */
    fun reset() = reset(initial)

    /** Re-seed the draft to the given [value] and reset validation (honoring `initialValidate`). */
    fun reset(value: V) = Snapshot.withMutableSnapshot {
        this.value = value
        state = seedState(value)
    }

    /** Initial state for [value]: revealed if [initialValidate], otherwise [FieldValidationState.Pristine]. */
    private fun seedState(value: V): FieldValidationState =
        if (initialValidate) rules.toFieldState(value, showError = true)
        else FieldValidationState.Pristine

    private fun revalidate(showError: Boolean) {
        state = rules.toFieldState(value, showError)
    }
}

/** Reveal errors on every field — e.g. at submit time. */
fun List<FieldValidator<*>>.reveal() = forEach { it.reveal() }

/**
 * Reveal all fields, then report whether none are in error. Mirrors the safe
 * `List<ValueValidator>.validate()` contract: because it reveals first, an untouched required field
 * cannot masquerade as valid. For a pure (non-revealing) read use `map { it.state }.hasNoErrors()`.
 */
fun List<FieldValidator<*>>.allValid(): Boolean {
    reveal()
    return map { it.state }.hasNoErrors()
}
