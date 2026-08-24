package com.chrisjenx.yakcov

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * Returns a [Modifier] that will modify how the field acts to user interaction and validation
 *
 * @see ValueValidator.validationConfig
 */
fun Modifier.validationConfig(
    validator: ValueValidator<*, *>,
    validateOnFocusLost: Boolean = false,
    shakeOnInvalid: Boolean = false,
    showErrorOnInteraction: Boolean = true,
): Modifier = with(validator) {
    validationConfig(
        validateOnFocusLost = validateOnFocusLost,
        shakeOnInvalid = shakeOnInvalid,
        showErrorOnInteraction = showErrorOnInteraction,
    )
}

/**
 * Invokes [onLost] when this element loses focus (had focus, then lost it). Pairs with the headless
 * [FieldValidator] — a presenter binder can call `field.onFocusLost()` here without the validator
 * needing any `Modifier` dependency.
 */
fun Modifier.onFocusLost(onLost: () -> Unit): Modifier = composed {
    var hadFocus by remember { mutableStateOf(false) }
    onFocusChanged { focusState ->
        if (hadFocus && !focusState.hasFocus) onLost()
        hadFocus = focusState.hasFocus
    }
}

/** Shake parity constants — copied from the ValueValidator implementation they replace. */
private val ShakeStrength = ShakingState.Strength.Custom(20f)
private val ShakeDirection = ShakingState.Direction.LEFT_THEN_RIGHT
private const val ShakeDurationMs = 20

/**
 * Invokes [onShake] when [trigger] changes while [isError] is true.
 *
 * The trigger value observed at first composition is treated as already-seen, so a field that is
 * already in error (e.g. `initialValidate = true`) does not shake on its first frame. [trigger] must
 * be monotonic: a counter that returns to its first-composition value will not fire.
 *
 * Internal so the trigger semantics can be tested without asserting on animation frames; the public
 * entry point is [shakeOnInvalid].
 */
@Composable
internal fun ShakeOnTriggerEffect(
    isError: Boolean,
    trigger: Int,
    onShake: suspend () -> Unit,
) {
    val initialTrigger = remember { trigger }
    LaunchedEffect(trigger) {
        if (trigger != initialTrigger && isError) onShake()
    }
}

/**
 * Shakes this element when [trigger] changes while [isError] is true — the plain-value equivalent of
 * `ValueValidator`'s `validationConfig(shakeOnInvalid = true)`, usable with no validator at all.
 *
 * Because a repeated invalid submit produces a structurally *equal* validation state, shake cannot be
 * driven by diffing that state; pass a monotonic counter instead (`FieldValidator.attempts`, or a
 * `submitAttempts` field in your reducer's model).
 *
 * @see validationBehavior for the bundled form.
 */
fun Modifier.shakeOnInvalid(isError: Boolean, trigger: Int): Modifier = composed {
    val shakingState = remember { ShakingState(ShakeStrength, ShakeDirection) }
    ShakeOnTriggerEffect(isError, trigger) { shakingState.shake(animationDuration = ShakeDurationMs) }
    shakable(shakingState)
}

/**
 * Bundles focus-loss handling and shake-on-invalid over plain values — the plain-value counterpart to
 * `ValueValidator`'s `validationConfig`, usable from a reducer's model or a headless [FieldValidator].
 *
 * Deliberately **not** named `validationConfig`: that name is already a `ValueValidator` member taking
 * a leading `Boolean`, and Kotlin resolves members over extensions, so a same-named free function
 * would silently bind to the member inside `with(validator) { }`.
 *
 * @param isError whether the field is currently showing an error — gates the shake.
 * @param shakeTrigger a monotonic counter; `null` disables shake entirely.
 * @param onFocusLost invoked when the element loses focus; `null` adds no focus handling. Equivalent
 *  to chaining [onFocusLost] yourself.
 * @see shakeOnInvalid
 * @see onFocusLost
 */
fun Modifier.validationBehavior(
    isError: Boolean,
    shakeTrigger: Int? = null,
    onFocusLost: (() -> Unit)? = null,
): Modifier = this
    .then(if (onFocusLost != null) Modifier.onFocusLost(onLost = onFocusLost) else Modifier)
    .then(if (shakeTrigger != null) Modifier.shakeOnInvalid(isError, shakeTrigger) else Modifier)
