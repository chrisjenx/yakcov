package com.chrisjenx.yakcov

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * Shake parity constants, matching `ValueValidator`'s own imperative shake so both paths feel
 * identical. `internal` rather than `private` so tests assert against these values instead of
 * re-typing the literals — a copy in a test drifts silently when the real ones are tuned.
 *
 * NOTE: `ValueValidator.kt` still spells the same values inline; unifying that too means editing a
 * file this change deliberately leaves alone, so it stays a follow-up.
 */
internal val ShakeStrength = ShakingState.Strength.Custom(20f)
internal val ShakeDirection = ShakingState.Direction.LEFT_THEN_RIGHT
internal const val ShakeDurationMs = 20

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
    val shakingState = rememberShakingState(strength = ShakeStrength, direction = ShakeDirection)
    shakeOnInvalidImpl(isError, trigger, shakingState)
}

/**
 * [shakeOnInvalid] with an injected [shakingState], so tests can observe `xPosition` — the animated
 * offset is otherwise trapped inside a `remember` no test can reach. Internal, not public: callers
 * who want to own the state should use [shakable] + [ShakingState.shake] directly.
 */
internal fun Modifier.shakeOnInvalid(
    isError: Boolean,
    trigger: Int,
    shakingState: ShakingState,
): Modifier = composed { shakeOnInvalidImpl(isError, trigger, shakingState) }

/** The single shake implementation shared by both [shakeOnInvalid] overloads. */
@Composable
private fun Modifier.shakeOnInvalidImpl(
    isError: Boolean,
    trigger: Int,
    shakingState: ShakingState,
): Modifier {
    ShakeOnTriggerEffect(isError, trigger) {
        try {
            shakingState.shake(animationDuration = ShakeDurationMs)
        } finally {
            // ShakeOnTriggerEffect keys its LaunchedEffect on `trigger`, so a new trigger CANCELS
            // this coroutine mid-animateTo. If the restart's isError guard then skips (the value
            // was corrected inside the ~240ms shake window — autofill, paste, programmatic set),
            // nothing would animate xPosition home and the field would render permanently offset.
            withContext(NonCancellable) { shakingState.xPosition.snapTo(0f) }
        }
    }
    return shakable(shakingState)
}

/**
 * Bundles focus-loss handling and shake-on-invalid over plain values — the plain-value counterpart to
 * `ValueValidator`'s `validationConfig`, usable from a reducer's model or a headless [FieldValidator].
 *
 * Deliberately **not** named `validationConfig`: that name is already a `ValueValidator` member taking
 * a leading `Boolean`, and Kotlin resolves members over extensions, so a same-named free function
 * would silently bind to the member inside `with(validator) { }`.
 *
 * @param isError whether the field is currently showing an error. It gates *only* the shake, so with
 *  the default `shakeTrigger = null` it is inert — passing your error state here without a trigger
 *  reads as though it were wired up but changes nothing.
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

/**
 * Moves the cursor to the end of the text when this element gains focus — the plain-value form of
 * `TextFieldValueValidator`'s member of the same name, usable with no validator.
 *
 * `@Composable` rather than `composed {}` because the [scope] default is a composable call. The value
 * is read through `rememberUpdatedState` so the deferred write sees the *live* text: the one-frame
 * deferral is what makes the reversed [TextRange] land the cursor at the end.
 *
 * @param highlight when true, selects the whole text instead of collapsing to the end.
 */
@Composable
fun Modifier.onFocusCursorToEnd(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    scope: CoroutineScope = rememberCoroutineScope(),
    highlight: Boolean = false,
): Modifier {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    return onFocusChanged { focusState ->
        if (focusState.isFocused) {
            scope.launch {
                val length = currentValue.text.length
                // Yes, this is the wrong way around (bug workaround to get cursor to end)
                val range = if (!highlight) TextRange(length) else TextRange(length, 0)
                currentOnValueChange(currentValue.copy(selection = range))
            }
        }
    }
}
