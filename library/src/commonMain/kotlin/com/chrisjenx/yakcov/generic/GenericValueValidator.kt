package com.chrisjenx.yakcov.generic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.chrisjenx.yakcov.ValueValidator
import com.chrisjenx.yakcov.ValueValidator.Companion.defaultValidationSeparator
import com.chrisjenx.yakcov.ValueValidatorRule

@Stable
class GenericValueValidator<T>(
    state: MutableState<T>,
    rules: List<ValueValidatorRule<T>>,
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
) : ValueValidator<T, T>(
    state = state,
    rules = rules,
    initialValidate = initialValidate,
    alwaysShowRule = alwaysShowRule,
    validationSeparator = validationSeparator,
    validateMapper = { validate(it) }
)


/**
 * @see GenericValueValidator for docs
 */
@Composable
fun <T> rememberGenericValueValidator(
    state: T,
    rules: List<ValueValidatorRule<T>>,
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
): GenericValueValidator<T> {
    return remember {
        GenericValueValidator(
            state = mutableStateOf(state),
            rules = rules,
            initialValidate = initialValidate,
            alwaysShowRule = alwaysShowRule,
            validationSeparator = validationSeparator,
        )
    }
}
