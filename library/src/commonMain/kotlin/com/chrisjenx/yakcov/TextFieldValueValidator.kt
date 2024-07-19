package com.chrisjenx.yakcov

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.util.fastJoinToString

/**
 * We wrap the [TextFieldValue] to add validation support.
 *
 * The validator makes it easy to wrap any TextField and provide helper methods.
 *
 * @param alwaysShowRule will show the field requirment via the [supportingText] as a non error
 * until input has started
 * @param initialValidate will start validation straight away, vs waiting for user interaction.
 * If the field is in error state will show error straight away.
 *
 * ## Usage
 *
 * ```
 * with(model.email) {
 *   TextField(
 *     value = value,
 *     onValueChange = ::onValueChange,
 *     modifier = Modifier.validateFocusChanged(), // Will validate after losing focus
 *     // other params
 *     isError = isError(), // Will mark as error if an error is present
 *     supportingText = supportingText(), // Will concat all errors into a composable response
 *   )
 * }
 * ```
 *
 * The recommend way to use these in your models is as such:
 *
 * ```
 * @Stable // Note these are stable not Immutable, make sure if this is emmbedded that it's parent's are stable too
 * data class MyModel(
 *   // by default marks as required
 *   val email: TextFieldValueValidator = TextFieldValueValidator(
 *     textFieldValue = // optional initial value
 *     rules = listOf(Required, Email), // Pass in the validators you want to use.
 *     initialValidate = true, // if true, will start validation straight away vs user interaction or calling validate
 *   ),
 * )
 * ```
 *
 * You can use the helper methods to control other composable state, as these are derived state they will update
 * when the user interacts with the field, or if you call [TextFieldValueValidator.validate].
 *
 * ```
 * Button(
 *   enabled = model.email.isValid, // Will only enable if the email is valid
 *   onClick = {}
 * ) { Text("Submit") }
 * ```
 * ### Component Validation and Values
 * To check your form is validated or fields are validated you can use the following:
 *
 * ```
 * val model = _model.asStateFlow()
 *
 * // helper method to check if fields are valid (will also show errors if they are not showing already)
 * fun validate(): Boolean {
 *   val model = this.model.value
 *   val isValid = true
 *   if(!model.email.validate()) isValid = false
 *   return isValid
 * }
 *
 * fun save() {
 *   if(!validate()) return
 *   model.value.email.value.text // This is the value of the email field
 *   // etc..
 * }
 * ```
 * Upating the model: It would be invalid to copy these values, you need to update the underlying value or call validate:
 *
 * ```
 * // CORRECT WAY
 * fun updateEmail(value: TextFieldValue) {
 *   model.value.email.value = value // this updates the underlying field value and will validate if been called
 *   // OR
 *   model.value.email.validate(value) // this will update the value and start validation on the field
 * }
 *
 * // DO NOT DO THIS
 * fun updateEmail(value: TextFieldValue) {
 *   // This resets the validation state and the validator
 *   _model.value = _model.value.copy(email = TextFieldValueValidator(value))
 * }
 * ```
 *
 * ### Custom Validators
 *
 * Create a new subclass of [TextFieldValueRule] then apply it as a rule to the [TextFieldValueValidator].
 *
 */
@Stable
class TextFieldValueValidator(
    private val textFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue()),
    private val rules: List<TextFieldValueRule> = listOf(Required),
    initialValidate: Boolean = false,
    private val alwaysShowRule: Boolean = false,
    private val validationSeparator: String = defaultValidationSeparator,
) {

    constructor(
        text: String,
        rules: List<TextFieldValueRule> = listOf(Required),
        initialValidate: Boolean = false,
    ) : this(mutableStateOf(TextFieldValue(text)), rules, initialValidate)

    /**
     * Set to true after first call of validate.
     */
    private var shouldValidate by mutableStateOf(initialValidate || rules.isEmpty())

    /**
     * Once validation is requested return validation rules, null is valid or not requested
     * validation yet, check [shouldValidate].
     */
    private val errorState: List<StringValidation>? by derivedStateOf {
        if (!shouldValidate) return@derivedStateOf null
        validations
    }

    /**
     *  Current Field validation state, this will only show invalid rules, once they are
     *  satisfied these will disappear. I.e. a null list is valid field.
     */
    val validations: List<StringValidation>? by derivedStateOf {
        rules.mapNotNull { it.validate(value) }.takeIf { it.isNotEmpty() }
    }

    var value by textFieldValue

    /**
     * True once [validate] has been called and there are no errors.
     *
     * @see isError
     * @see getErrorString
     */
    val isValid by derivedStateOf { shouldValidate && errorState == null }

    /**
     * Called when the field value changes. Which is also when we should start validating.
     *
     * @return true if the [TextFieldValue] is valid, false otherwise.
     * @see validate
     */
    fun validate(textFieldValue: TextFieldValue? = null): Boolean {
        textFieldValue?.also { this.textFieldValue.value = it }
        shouldValidate = true
        return errorState == null
    }

    /**
     * Alias for [validate]
     */
    fun onValueChange(textFieldValue: TextFieldValue) {
        validate(textFieldValue)
    }

    /**
     * Called when the field value changes, we also start validating. Generally prefer to use
     * [TextFieldValue] over [String] as that supports better text editing tracking, esp when
     * formatting text.
     *
     * @see validate
     */
    fun validate(text: String? = null): Boolean {
        return validate(text?.let { textFieldValue.value.copy(text = text) })
    }

    /**
     * Alias for [validate]
     */
    fun onValueChange(text: String) = validate(text)

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
            if (!focusState.isFocused && hadFocus) validate(textFieldValue = null)
        }
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
        return textFieldValue.value.text
    }

    companion object {
        /**
         * What we joinToString with for multiple rules
         */
        var defaultValidationSeparator = ", "
    }

}


/**
 * @see TextFieldValueValidator for docs
 */
@Composable
fun rememberTextFieldValueValidator(
    textFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue()),
    rules: List<TextFieldValueRule> = listOf(Required),
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = TextFieldValueValidator.defaultValidationSeparator,
): TextFieldValueValidator {
    return remember {
        TextFieldValueValidator(
            textFieldValue = textFieldValue,
            rules = rules,
            initialValidate = initialValidate,
            alwaysShowRule = alwaysShowRule,
            validationSeparator = validationSeparator,
        )
    }
}


/**
 * Iterate through list of fields and validate them all to start error validation, true if all valid
 * false if any are invalid
 */
fun List<TextFieldValueValidator>.validate(): Boolean {
    return this.all { validator -> validator.validate(textFieldValue = null) }
}
