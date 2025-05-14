package com.chrisjenx.yakcov

import androidx.compose.ui.Modifier

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
