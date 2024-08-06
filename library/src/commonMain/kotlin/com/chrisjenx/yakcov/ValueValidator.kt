package com.chrisjenx.yakcov

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.util.fastJoinToString
import com.chrisjenx.yakcov.TextFieldValueValidator.Companion.defaultValidationSeparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class ValueValidator<V, R>(
    val state: MutableState<V>,
    protected val rules: List<ValueValidatorRule<R>>,
    protected val initialValidate: Boolean = false,
    protected val alwaysShowRule: Boolean = false,
    protected val validationSeparator: String = defaultValidationSeparator,
    protected val shakeOnInvalid: Boolean = false,
    private val validateMapper: ValueValidatorRule<R>.(V) -> StringValidation?,
) {

    /**
     * The current value of the field.
     */
    var value: V by state

    /**
     * Set to true after first call of validate.
     */
    private var shouldValidate by mutableStateOf(initialValidate || rules.isEmpty())

    /**
     * Once validation is requested return validation rules, null is valid or not requested
     * validation yet, check [shouldValidate].
     */
    protected val errorState: List<StringValidation>? by derivedStateOf {
        if (!shouldValidate) return@derivedStateOf null
        validations
    }

    // Lazy create shaking state
    private val shakingState by lazy {
        ShakingState(
            strength = ShakingState.Strength.Custom(20f),
            direction = ShakingState.Direction.LEFT_THEN_RIGHT
        )
    }

    /**
     *  Current Field validation state, this will only show invalid rules, once they are
     *  satisfied these will disappear. I.e. a null list is valid field.
     */
    val validations: List<StringValidation>? by derivedStateOf {
        rules.mapNotNull { it.validateMapper(value) }.takeIf { it.isNotEmpty() }
    }

    /**
     * True once [validate] has been called and there are no errors.
     *
     * @see isError
     * @see getErrorString
     */
    val isValid by derivedStateOf { shouldValidate && errorState == null }

    /**
     * Slightly different to [validate] this is prefered to be called whne the value changes and
     * won't shake while the user is typing unlike [validate]
     */
    open fun onValueChange(value: V) {
        validate(value, shake = false)
    }

    /**
     * Called when requesting if valid or to update the value and validate.
     *
     * @see validate
     */
    open fun validate(value: V? = null): Boolean {
        return validate(value, shake = true)
    }

    /**
     * @return true if the [TextFieldValue] is invalid, false otherwise.
     */
    fun isError(): Boolean = errorState != null

    /**
     * @return the error message if the [TextFieldValue] is invalid, null otherwise.
     */
    @Composable
    fun getErrorString(): String? {
        return errorState?.map { it.format() }?.fastJoinToString(validationSeparator)
    }

    /**
     * Returns the rule validations if set, will only return outstanding rules.
     * I.e. if field is required this will only show if the field is empty.
     *
     * Unlike [getErrorString] this will return rules regardless of if [validate] has been called or not.
     */
    @Composable
    fun getValidationString(): String? {
        return validations?.map { it.format() }?.fastJoinToString(validationSeparator)
    }

    /**
     * Generates a supporting error text for your TextField.
     */
    @Composable
    fun supportingText(): (@Composable () -> Unit)? {
        if (alwaysShowRule) getValidationString()?.let { validations ->
            return { Text(validations) }
        }
        getErrorString()?.let { error ->
            return { Text(error) }
        }
        return null
    }

    /**
     * Returns a [Modifier] that will validate the [TextFieldValue] when the focus is lost.
     */
    fun Modifier.validateFocusChanged(): Modifier {
        var hadFocus by mutableStateOf(false)
        return this.onFocusChanged { focusState ->
            if (focusState.hasFocus) hadFocus = true
            // Don't shake on loss of focus, as we want to just show the error
            if (!focusState.isFocused && hadFocus) validate(value = null, shake = false)
        }
    }

    private var shakeOnInvalidScope: CoroutineScope? = null

    /**
     * Adds to the modifier, that when [validate] is called AND the field is invalid it will
     * shake the field to draw attention to the error.
     */
    @Composable
    fun Modifier.shakeOnInvalid(): Modifier {
        shakeOnInvalidScope = rememberCoroutineScope()
        return this.shakable(shakingState)
    }

    // Internal validate method so focus and external validate act correctly
    internal fun validate(value: V? = null, shake: Boolean = false): Boolean {
        value?.also { this.value = it }
        shouldValidate = true
        if (shake) errorState?.let {
            // Only shake if invalid and scope is set
            shakeOnInvalidScope?.let { scope ->
                scope.launch { shakingState.shake(animationDuration = 20) }
            }
        }
        return errorState == null
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TextFieldValueValidator

        if (value != other.value) return false
        if (rules != other.rules) return false
        return errorState == other.errorState
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + rules.hashCode()
        result = 31 * result + (errorState?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return state.toString()
    }

}


/**
 * Iterate through list of fields and validate them all to start error validation, true if all valid
 * false if any are invalid
 */
fun List<TextFieldValueValidator>.validate(): Boolean {
    // Fold to make sure we loop through all validators
    return this.fold(true) { valid, validator ->
        validator.validate(value = null, shake = true) && valid
    }
}

