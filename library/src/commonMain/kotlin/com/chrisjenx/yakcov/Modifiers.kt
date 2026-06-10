package com.chrisjenx.yakcov

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
