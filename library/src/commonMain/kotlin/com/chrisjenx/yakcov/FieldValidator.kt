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
 * state. Do not copy it; mutate via [onValueChange]/[onFocusLost]/[validate]/[reset].
 *
 * ### Threading
 * The mutators write Compose snapshot state and should be called on the presenter's confined/main
 * thread. [onValueChange] and [reset] commit their draft + state writes inside a single mutable
 * snapshot, so a concurrent observer never sees a torn `value`/`state` pair.
 *
 * ### Observability
 * Pass an [observer] to receive a [FieldValidatorEvent] after every mutation commits
 * ([FieldValidatorEvent.ValueChanged]/[FieldValidatorEvent.Validated]/[FieldValidatorEvent.Reset]).
 * No event fires at construction. Observers must not throw — see [FieldValidatorObserver].
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
 *     fun submit(): Boolean = listOf(email, amount).validate()   // shows errors, then checks
 * }
 * ```
 *
 * @param initial the starting draft value (also the value [reset] re-seeds to).
 * @param rules the rules evaluated on every change/focus-loss/validate.
 * @param initialValidate when true, validates and shows errors immediately (and on [reset]).
 * @param observer optional [FieldValidatorObserver] notified after each mutation commits.
 */
@Stable
class FieldValidator<V>(
    private val initial: V,
    private val rules: List<ValueValidatorRule<V>>,
    private val initialValidate: Boolean = false,
    private val observer: FieldValidatorObserver<V>? = null,
) {
    /** The field draft — the single source of truth. Mutate it via [onValueChange]. */
    var value: V by mutableStateOf(initial)
        private set

    /** The current plain validation output. Readable without entering composition. */
    var state: FieldValidationState by mutableStateOf(seedState(initial))
        private set

    /**
     * Monotonic count of submit-intent validations — every [validate] call, and no others. Pass this
     * as the `shakeTrigger` of [validationBehavior]/[shakeOnInvalid]: a repeated invalid submit
     * produces an *equal* [FieldValidationState], so shake cannot be driven by diffing that state.
     *
     * Deliberately not bumped by [onFocusLost] (focus loss reveals the error without shaking) nor
     * reset by [reset] (it counts events, not state).
     */
    var attempts: Int by mutableStateOf(0)
        private set

    /** Update the draft and revalidate, keeping the current showError (no error pop while typing). */
    fun onValueChange(value: V) {
        Snapshot.withMutableSnapshot {
            this.value = value
            revalidate(showError = state.showError)
        }
        observer?.onEvent(FieldValidatorEvent.ValueChanged(this.value, state))
    }

    /** Revalidate and show errors — call when the field loses focus. Does not count as an attempt. */
    fun onFocusLost(): Boolean = revealAndValidate(countAttempt = false)

    /**
     * Revalidate, force errors visible, and report validity — call at submit time. Increments
     * [attempts]. Mirrors [ValueValidator.validate].
     *
     * @return `true` when the field is valid (no [ValidationResult.Outcome.ERROR]).
     */
    fun validate(): Boolean = revealAndValidate(countAttempt = true)

    private fun revealAndValidate(countAttempt: Boolean): Boolean {
        Snapshot.withMutableSnapshot {
            revalidate(showError = true)
            if (countAttempt) attempts++
        }
        observer?.onEvent(FieldValidatorEvent.Validated(value, state))
        return !state.isError
    }

    /** Re-seed the draft to [initial] and reset validation (honoring `initialValidate`). */
    fun reset() = reset(initial)

    /** Re-seed the draft to the given [value] and reset validation (honoring `initialValidate`). */
    fun reset(value: V) {
        Snapshot.withMutableSnapshot {
            this.value = value
            state = seedState(value)
        }
        observer?.onEvent(FieldValidatorEvent.Reset(this.value, state))
    }

    /** Initial state for [value]: errors shown if [initialValidate], otherwise [FieldValidationState.Pristine]. */
    private fun seedState(value: V): FieldValidationState =
        if (initialValidate) rules.toFieldState(value, showError = true)
        else FieldValidationState.Pristine

    private fun revalidate(showError: Boolean) {
        state = rules.toFieldState(value, showError)
    }
}

/**
 * Validate every field (showing errors) and report whether all are valid. Mirrors the safe
 * `List<ValueValidator>.validate()` contract: because it shows errors first, an untouched required
 * field cannot masquerade as valid. For a pure read that shows nothing use `map { it.state }.hasNoErrors()`.
 *
 * @return `true` when no field is in error.
 */
fun List<FieldValidator<*>>.validate(): Boolean {
    forEach { it.validate() }
    return map { it.state }.hasNoErrors()
}
