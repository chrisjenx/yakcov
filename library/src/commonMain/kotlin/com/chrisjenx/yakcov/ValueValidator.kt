package com.chrisjenx.yakcov

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
    internal val initialValidate: Boolean = false,
    protected val alwaysShowRule: Boolean = false,
    protected val validationSeparator: String = defaultValidationSeparator,
    private val validateMapper: ValueValidatorRule<R>.(V) -> ValidationResult,
) {

    /**
     * The current value of the field.
     */
    var value: V by state

    internal var internalState: InternalState by mutableStateOf(
        if (initialValidate || rules.isEmpty()) InternalState.Validating()
        else InternalState.Initial
    )

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
        if (internalState !is InternalState.Validating) return@derivedStateOf false
        // If no rules set then we are always valid
        val severity = validationResults.map { it.outcome() }.maxOfOrNull { it.severity }
            ?: return@derivedStateOf true
        severity < Outcome.ERROR.severity
    }

    /**
     * Slightly different to [validate] this is preferred to be called when the value changes and
     * won't shake while the user is typing unlike [validate]. This also will respect settings
     * set by [validationConfig].
     *
     * @see validate
     */
    open fun onValueChange(value: V?) {
        validateWithResult(value, shake = false, shouldShowError = showErrorOnUserInput)
    }

    /**
     * Called when requesting if valid or to update the value and validate.
     *
     * This will shake if enabled, will also turn on errors if deferred by [validationConfig].
     *
     * @see validate
     * @return true if the value if outcomes are all less than [Outcome.ERROR]
     */
    open fun validate(value: V? = null): Boolean {
        return validate(value, shake = true, shouldShowError = true)
    }

    /**
     * Called when requesting if valid or to update the value and validate.
     *
     * @see validate
     * @return the outcome of the validations, returns most severe outcome.
     */
    open fun validateWithResult(value: V? = null): Outcome {
        return validateWithResult(value, shake = true, shouldShowError = true)
    }

    /**
     * Will reset the state of the validator back to initial validation state.
     *
     * The only exception is if the [initialValidate] is set to true, in which case this will
     *  effectively do nothing.
     */
    open fun reset() {
        if (initialValidate) return
        internalState = InternalState.Initial
    }

    /**
     * @return true if the [ValueValidator] has started validation and any outcome is [Outcome.ERROR]
     * or higher.
     */
    fun isError(): Boolean = validationResults
        .takeIf { (internalState as? InternalState.Validating)?.shouldShowError == true }
        ?.any { it.outcome().severity >= Outcome.ERROR.severity }
        ?: false

    /**
     * If there are multiple rules this will return the highest severity of the rules.
     * I.e. if three rules succeed and one Errors, this will return Error.
     *
     * @return the outcome of the validations, will be null if no validations have been run yet.
     */
    fun outcome(): Outcome? = validationResults
        .takeIf { internalState is InternalState.Validating }
        .let { results ->
            if (results == null) return@let null // Not validating yet
            // Return the result with the highest severity, or if no rules return success
            results.maxByOrNull { it.outcome().severity }?.outcome() ?: Outcome.SUCCESS
        }

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
            .takeIf { (internalState as? InternalState.Validating)?.shouldShowError == true }
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
        val isValidating = internalState is InternalState.Validating
        if (alwaysShowRule || isValidating) getValidationResultString(severity)?.let { validations ->
            return { Text(validations) }
        }
        return null
    }

    /**
     * Whether to show error message when user has started typing,
     * errors will then be show after [validate] is called.
     * Default is true (matched the default value set on that method)
     *
     * @see validationConfig
     */
    private var showErrorOnUserInput by mutableStateOf(true)

    /**
     * Returns a [Modifier] that will modify how the field acts to user interaction and validation
     *
     * @param validateOnFocusLost when the user leaves the field will start validation (and show
     *  error if invalid).
     * @param shakeOnInvalid when [validate] is called AND the field is invalid it will
     *  shake the field to draw attention to the error.
     * @param showErrorOnInteraction default `true`, will show error message [onValueChange]
     *  or if loosing focus when [validateOnFocusLost] is `true`.
     *  When you call [validate] it will start validating showing errors if present.
     */
    fun Modifier.validationConfig(
        validateOnFocusLost: Boolean = false,
        shakeOnInvalid: Boolean = false,
        showErrorOnInteraction: Boolean = true,
    ): Modifier = composed {
        // Set scope if shaking is enabled
        shakeOnInvalidScope = if (shakeOnInvalid) rememberCoroutineScope() else null
        // Track if we should show error on user input
        showErrorOnUserInput = showErrorOnInteraction
        this
            .then(
                if (validateOnFocusLost) {
                    // Track focus
                    var hadFocus by remember { mutableStateOf(false) }
                    Modifier.onFocusChanged { focusState ->
                        // Don't shake on loss of focus, as we want to just show the error
                        if (validateOnFocusLost && hadFocus && !focusState.hasFocus) {
                            validate(value = null, shake = false, shouldShowError = true)
                        }
                        hadFocus = focusState.hasFocus
                    }
                } else {
                    Modifier
                }
            )
            .then(if (shakeOnInvalid) Modifier.shakable(shakingState) else Modifier)
    }

    /**
     * Returns a [Modifier] that will validate the [TextFieldValue] when the focus is lost.
     */
    @Deprecated(
        "Use validationConfig instead",
        ReplaceWith("validationConfig(validateOnFocusLost = true)")
    )
    fun Modifier.validateFocusChanged(): Modifier = composed {
        var hadFocus by remember { mutableStateOf(false) }
        onFocusChanged { focusState ->
            // Don't shake on loss of focus, as we want to just show the error
            if (hadFocus && !focusState.hasFocus) {
                validate(value = null, shake = false, shouldShowError = true)
            }
            hadFocus = focusState.hasFocus
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
    @Deprecated(
        "Use validationConfig instead",
        ReplaceWith("validationConfig(shakeOnInvalid = true)")
    )
    fun Modifier.shakeOnInvalid(): Modifier = composed {
        shakeOnInvalidScope = rememberCoroutineScope()
        shakable(shakingState)
    }

    // Internal validate method so focus and external validate act correctly
    internal fun validate(
        value: V? = null,
        shake: Boolean = false,
        shouldShowError: Boolean? = null,
    ): Boolean {
        return validateWithResult(value, shake, shouldShowError).severity < Outcome.ERROR.severity
    }

    // Internal validate method so focus and external validate act correctly
    internal fun validateWithResult(
        value: V? = null,
        shake: Boolean = false,
        shouldShowError: Boolean? = null,
    ): Outcome {
        value?.also { this.value = it }
        internalState = internalState.toValidating(shouldShowError)
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
        if (other == null || other !is ValueValidator<*, *>) return false
        if (value != other.value) return false
        if (rules != other.rules) return false
        if (internalState != other.internalState) return false
        return validationResults == other.validationResults
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + rules.hashCode()
        result = 31 * result + validationResults.hashCode()
        result = 31 * result + internalState.hashCode()
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

    internal sealed class InternalState {
        /**
         * Initial state, no validation has been started yet.
         */
        data object Initial : InternalState()

        /**
         * @param shouldShowError if true [isError] can be true, [supportingText]
         * will still show messaging.
         */
        data class Validating(val shouldShowError: Boolean = true) : InternalState()

        /**
         * Convert to validating state, if [canShowError] is null it will default to true or the
         * current state if already in validating state.
         */
        fun toValidating(canShowError: Boolean? = null): InternalState = when (this) {
            is Initial -> Validating(shouldShowError = canShowError ?: true)
            is Validating -> {
                val canShow = canShowError ?: this.shouldShowError
                // Once true we don't go back to false (unless we come from intial state)
                this.copy(shouldShowError = this.shouldShowError || canShow)
            }
        }

    }

}


/**
 * Iterate through list of fields and validate them all to start error validation, true if all valid
 * false if any are invalid
 */
fun List<ValueValidator<*, *>>.validate(): Boolean {
    // Fold to make sure we loop through all validators
    return this.fold(true) { valid, validator ->
        validator.validate(value = null, shake = true, shouldShowError = true) && valid
    }
}

/**
 * Iterate through list of fields and validate them all to start error validation, will return
 * the highest severity of all the validators passed in.
 */
fun List<ValueValidator<*, *>>.validateWithResult(): Outcome {
    return this.map { it.validateWithResult(value = null, shake = true, shouldShowError = true) }
        .maxByOrNull { it.severity } ?: Outcome.SUCCESS
}
