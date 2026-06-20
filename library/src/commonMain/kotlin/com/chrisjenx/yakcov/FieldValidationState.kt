package com.chrisjenx.yakcov

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Plain, serializable validation output for a single field — designed to live inside a presenter's
 * immutable UI `Model` and be persisted (encode with kotlinx.serialization into a SavedStateHandle /
 * DataStore, or use [Saver] with `rememberSaveable`) so it survives process death.
 *
 * Only [severity] and [showError] are serialized. [result] is [Transient]: resource-backed results
 * hold a `StringResource` handle that must not be persisted (handles can shift across builds). After
 * restore [result] is `null` and [text] returns `null` until the rules are re-run — see the README
 * "restore" recipe (persist the draft, reconstruct the validator, validate/revalidate).
 *
 * Equality intentionally includes [result] (default data-class behavior): a message-only change at
 * constant [severity]/[showError] must still be observed by `mutableStateOf`/`distinctUntilChanged`,
 * otherwise the UI would show a stale error.
 *
 * @property severity highest [Outcome] across the field's rules (defaults to [Outcome.SUCCESS]).
 * @property showError whether errors should be surfaced to the user yet (shown on focus loss/submit).
 * @property result the most-severe [ValidationResult], for rendering the message via [text].
 */
@Immutable
@Serializable
data class FieldValidationState(
    val severity: Outcome = Outcome.SUCCESS,
    val showError: Boolean = false,
    @Transient val result: ValidationResult? = null,
) {
    /** True only once errors are shown AND the severity is [Outcome.ERROR]. */
    val isError: Boolean get() = showError && severity == Outcome.ERROR

    /** True only once errors are shown AND the severity is [Outcome.WARNING]. */
    val isWarning: Boolean get() = showError && severity == Outcome.WARNING

    companion object {
        /** The initial, never-validated state. */
        val Pristine = FieldValidationState()

        /**
         * A `rememberSaveable` [Saver] that persists [severity] + [showError] (the same fields
         * kotlinx serialization carries). [result] is not saved and is `null` on restore.
         */
        val Saver: Saver<FieldValidationState, Any> = listSaver(
            save = { listOf(it.severity.name, it.showError) },
            restore = {
                FieldValidationState(
                    severity = Outcome.valueOf(it[0] as String),
                    showError = it[1] as Boolean,
                )
            },
        )
    }
}

/**
 * Run this rule against [value] and wrap the outcome in a [FieldValidationState]. Pure — does not
 * enter composition. (Rules built from the default constructors are also free of Compose snapshot
 * reads; a rule that opts into a live `State` performs a snapshot read.)
 */
fun <V> ValueValidatorRule<V>.toFieldState(value: V, showError: Boolean): FieldValidationState {
    val res = validate(value)
    return FieldValidationState(severity = res.outcome(), showError = showError, result = res)
}

/**
 * Fold a rule list into a single [FieldValidationState], keeping the most-severe rule's message.
 * On a severity tie the first-ordered rule's message wins (stable fold). Empty rule lists fold to
 * [FieldValidationState.Pristine] (a field with no rules is always valid). Pure — does not enter
 * composition.
 */
fun <V> List<ValueValidatorRule<V>>.toFieldState(value: V, showError: Boolean): FieldValidationState =
    map { it.toFieldState(value, showError) }
        .maxByOrNull { it.severity.severity }
        ?: FieldValidationState.Pristine

/**
 * Pure read: true when no field is currently in error. NOTE this answers "is anything in error
 * right now", NOT "has this been validated" — not-yet-shown [FieldValidationState.Pristine] fields
 * return true. For submit, validate first (see `List<FieldValidator<*>>.validate()`).
 */
fun List<FieldValidationState>.hasNoErrors(): Boolean = none { it.severity == Outcome.ERROR }

/**
 * Resolve the field's message for display, or `null` when there is nothing to show. Gated on
 * [showError]: returns `null` until errors are shown (focus loss/submit), so the message tracks the
 * same showError gating as [isError]/[isWarning] — no message pops while the user is still typing.
 * [Composable] because resource-backed results resolve their `StringResource` here. For the raw,
 * ungated message (regardless of showError) use `result?.format()` directly.
 *
 * @param default a plain hint shown when there is no message to display (e.g. a pristine field).
 *  Defaults to `null`, preserving the prior return-`null`-when-nothing-to-show behavior.
 */
@Composable
fun FieldValidationState.text(default: String? = null): String? =
    (if (showError) result?.format() else null) ?: default

/**
 * Convenience that mirrors `ValueValidator.supportingText()`: returns a composable that renders the
 * shown message (see [text]) or the [default] hint, or `null` when there is neither. Drops
 * the `text()?.let { Text(it) }` boilerplate.
 *
 * @param default a plain hint shown when there is no validation message (see [text]).
 */
@Composable
fun FieldValidationState.supportingText(default: String? = null): (@Composable () -> Unit)? {
    val message = text(default) ?: return null
    return { Text(message) }
}
