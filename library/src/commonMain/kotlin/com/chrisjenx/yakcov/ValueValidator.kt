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
import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.strings.TextFieldValueValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

abstract class ValueValidator<V, R>(
    protected val state: MutableState<V>,
    protected val rules: List<ValueValidatorRule<R>>,
    protected val initialValidate: Boolean = false,
    protected val alwaysShowRule: Boolean = false,
    protected val validationSeparator: String = defaultValidationSeparator,
    protected val shakeOnInvalid: Boolean = false,
    private val validateMapper: ValueValidatorRule<R>.(V) -> ValidationResult,
) {

    /**
     * The current value of the field.
     */
    var value: V by state

    /**
     * Set to true after first call of validate.
     */
    private var shouldValidate: Boolean by mutableStateOf(initialValidate || rules.isEmpty())

    /**
     *  Current Field validation state, will be empty if no rules are set.
     */
    val validationResults: List<ValidationResult> by derivedStateOf {
        rules.map { it.validateMapper(value) }
    }

    /**
     * True once [validate] has been called and all rules are are less than [Outcome.ERROR]
     *
     * @see isError
     * @see getValidationResultString
     */
    val isValid: Boolean by derivedStateOf {
        if (!shouldValidate) return@derivedStateOf false
        val severity = validationResults.map { it.outcome() }.maxOfOrNull { it.severity }
            ?: return@derivedStateOf false
        severity < Outcome.ERROR.severity
    }

    /**
     * Slightly different to [validate] this is prefered to be called whne the value changes and
     * won't shake while the user is typing unlike [validate]
     */
    open fun onValueChange(value: V?) {
        validateWithResult(value, shake = false)
    }

    /**
     * Called when requesting if valid or to update the value and validate.
     *
     * @see validate
     * @return true if the value if outcomes are all less than [Outcome.ERROR]
     */
    open fun validate(value: V? = null): Boolean = validate(value, shake = true)

    /**
     * Called when requesting if valid or to update the value and validate.
     *
     * @see validate
     * @return the outcome of the validations, returns most severe outcome.
     */
    open fun validateWithResult(value: V? = null): Outcome {
        return validateWithResult(value, shake = true)
    }

    /**
     * @return true if the [ValueValidator] has started validation and any outcome is [Outcome.ERROR]
     * or higher.
     */
    fun isError(): Boolean = validationResults
        .takeIf { shouldValidate }
        ?.maxOfOrNull { it.outcome().severity >= Outcome.ERROR.severity }
        ?: false

    /**
     * If there are multiple rules this will return the highest severity of the rules.
     * I.e. if three rules succeed and one Errors, this will return Error.
     *
     * @return the outcome of the validations, will be null if no validations have been run yet.
     */
    fun outcome(): Outcome? = validationResults
        .takeIf { shouldValidate }
        ?.maxByOrNull { it.outcome().severity }?.outcome()

    /**
     * @return the error message of [ValueValidatorRule] with [Outcome.ERROR], null otherwise.
     */
    @Composable
    @Deprecated(
        "Use getValidationResultString instead",
        ReplaceWith("getValidationResultString(Output.ERROR.severity)")
    )
    fun getErrorString(): String? {
        return validationResults
            .takeIf { shouldValidate }
            ?.filter { it.outcome().severity >= Outcome.ERROR.severity }
            ?.mapNotNull { it.format() }
            ?.takeIf { it.isNotEmpty() } // return null if no errors
            ?.fastJoinToString(validationSeparator)
    }

    /**
     * Unlike [getErrorString] this will return rules regardless if
     * [validate] has been called or not.
     *
     * @param severity the minimum severity to return, defaults to [Outcome.SUCCESS.severity]
     * @return the validation message of [ValueValidatorRule] with [Outcome]
     *  greater or equal to [severity]
     */
    @Composable
    fun getValidationResultString(severity: Short = Outcome.SUCCESS.severity): String? {
        return validationResults
            .filter { it.outcome().severity >= severity }
            .mapNotNull { it.format() }
            .takeIf { it.isNotEmpty() }
            ?.fastJoinToString(validationSeparator)
    }

    /**
     * Generates a supporting error text for your TextField.
     *
     * @param severity the minimum severity to show, defaults to [Outcome.SUCCESS.severity]
     */
    @Composable
    fun supportingText(severity: Short = Outcome.SUCCESS.severity): (@Composable () -> Unit)? {
        if (alwaysShowRule || shouldValidate) getValidationResultString(severity)?.let { validations ->
            return { Text(validations) }
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
            if (!focusState.isFocused && hadFocus) validateWithResult(value = null, shake = false)
        }
    }

    private var shakeOnInvalidScope: CoroutineScope? = null
    private val shakingState by lazy {
        ShakingState(
            strength = ShakingState.Strength.Custom(20f),
            direction = ShakingState.Direction.LEFT_THEN_RIGHT
        )
    }

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
        return validateWithResult(value, shake).severity < Outcome.ERROR.severity
    }

    // Internal validate method so focus and external validate act correctly
    internal fun validateWithResult(value: V? = null, shake: Boolean = false): Outcome {
        value?.also { this.value = it }
        shouldValidate = true
        val outcome = validationResults
            .map { it.outcome() }.maxByOrNull { it.severity } ?: Outcome.SUCCESS
        if (shake && outcome.severity >= Outcome.ERROR.severity) {
            // Only shake if invalid and scope is set
            shakeOnInvalidScope?.launch { shakingState.shake(animationDuration = 20) }
        }
        return outcome
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TextFieldValueValidator

        if (value != other.value) return false
        if (rules != other.rules) return false
        return validationResults == other.validationResults
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + rules.hashCode()
        result = 31 * result + validationResults.hashCode()
        return result
    }

    override fun toString(): String {
        return state.toString()
    }

    companion object {
        /**
         * What we joinToString with for multiple rules
         */
        var defaultValidationSeparator = ", "
    }

}


/**
 * Iterate through list of fields and validate them all to start error validation, true if all valid
 * false if any are invalid
 */
fun List<ValueValidator<*, *>>.validate(): Boolean {
    // Fold to make sure we loop through all validators
    return this.fold(true) { valid, validator ->
        validator.validate(value = null, shake = true) && valid
    }
}

/**
 * Iterate through list of fields and validate them all to start error validation, will return
 * the highest severity of all the validators passed in.
 */
fun List<ValueValidator<*, *>>.validateWithResult(): Outcome {
    return this.map { it.validateWithResult(value = null, shake = true) }
        .maxByOrNull { it.severity } ?: Outcome.SUCCESS
}

