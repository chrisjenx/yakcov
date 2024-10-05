package com.chrisjenx.yakcov.strings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.ValueValidator
import com.chrisjenx.yakcov.ValueValidator.Companion.defaultValidationSeparator
import com.chrisjenx.yakcov.ValueValidatorRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
 * val emailValidator = rememberTextFieldValueValidator(rules = listOf(Required, Email))
 * with(emailValidator) {
 *   TextField(
 *     value = value,
 *     onValueChange = ::onValueChange,
 *     modifier = Modifier
 *       .validateFocusChanged() // Will validate after losing focus
 *       .shakeOnInvalid(), // Will shake the field if invalid and validate() is called
 *     isError = isError(), // Will mark as error if an error is present
 *     supportingText = supportingText(), // Will concat all errors into a composable response
 *   )
 * }
 * ```
 *
 * The recommend way to use these in your models is as such:
 *
 * ```
 * @Stable // Note these are stable not Immutable, make sure if this is embedded that it's parent's are stable too
 * data class MyModel(
 *   // by default marks as required
 *   val email: TextFieldValueValidator = TextFieldValueValidator(
 *     textFieldValue = // optional initial value
 *     rules = listOf(Required, Email), // Pass in the validators you want to use.
 *     initialValidate = true, // if true, will start validation straight away vs user interaction or calling validate()
 *   ),
 * )
 * ```
 *
 * You can use the helper methods to control other composable state, as these are derived state
 * they will update when the user interacts with the field, or if you
 * call [TextFieldValueValidator.validate].
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
 * // We provide a helper method to validate all fields if desired, otherwise call validate on
 * //  the ones you care about
 * fun validate(): Boolean {
 *   val model = this.model.value
 *   return listOf(model.email, model.password).validate()
 * }
 *
 * fun save() {
 *   if(!validate()) return
 *   model.value.email.value.text // This is the value of the email field
 *   // etc..
 * }
 * ```
 * Updating the model: It would be invalid to copy these values, you need to update the underlying value or call validate:
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
 * Create a new subclass of [ValueValidatorRule] then apply it as a rule to the [TextFieldValueValidator].
 *
 */
@Stable
class TextFieldValueValidator(
    state: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue()),
    rules: List<ValueValidatorRule<String>> = listOf(Required),
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
) : ValueValidator<TextFieldValue, String>(
    state = state,
    rules = rules,
    initialValidate = initialValidate,
    alwaysShowRule = alwaysShowRule,
    validationSeparator = validationSeparator,
    validateMapper = { validate(it.text) }
) {

    constructor(
        value: String,
        rules: List<ValueValidatorRule<String>> = listOf(Required),
        initialValidate: Boolean = false,
        alwaysShowRule: Boolean = false,
        validationSeparator: String = defaultValidationSeparator,
    ) : this(
        mutableStateOf(TextFieldValue(value)), rules, initialValidate = initialValidate,
        alwaysShowRule = alwaysShowRule, validationSeparator = validationSeparator
    )

    /**
     * Alias for [onValueChange] for String changes
     */
    fun onValueChange(string: String) {
        onValueChange(value = string.let { state.value.copy(text = it) })
    }

    /**
     * Returns a [Modifier] that will move the cursor to the end of the text when the focus is gained.
     *
     * @param highlight if true will highlight the text as well
     */
    @Composable
    fun Modifier.onFocusCursorToEnd(
        scope: CoroutineScope = rememberCoroutineScope(),
        highlight: Boolean = false,
    ): Modifier {
        return this.onFocusChanged {
            if (it.isFocused) {
                scope.launch {
                    val range = if (!highlight) TextRange(value.text.length)
                    // Yes, this is the wrong way around (bug workaround to get cursor to end)
                    else TextRange(value.text.length, 0)
                    value = value.copy(selection = range)
                }
            }
        }
    }

    /**
     * Called when the field value changes, we also start validating. Generally prefer to use
     * [TextFieldValue] over [String] as that supports better text editing tracking, esp when
     * formatting text.
     *
     * @see validate
     */
    fun validate(string: String? = null): Boolean {
        return validate(value = string?.let { state.value.copy(text = it) })
    }

    override fun toString(): String {
        return state.value.text
    }

}


/**
 * @see TextFieldValueValidator for docs
 */
@Composable
fun rememberTextFieldValueValidator(
    textFieldValue: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue()),
    rules: List<ValueValidatorRule<String>> = listOf(Required),
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
): TextFieldValueValidator = remember {
    TextFieldValueValidator(
        state = textFieldValue,
        rules = rules,
        initialValidate = initialValidate,
        alwaysShowRule = alwaysShowRule,
        validationSeparator = validationSeparator,
    )
}

/**
 * @see TextFieldValueValidator for docs
 */
@Composable
fun rememberTextFieldValueValidator(
    value: String,
    rules: List<ValueValidatorRule<String>> = listOf(Required),
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
): TextFieldValueValidator = remember {
    TextFieldValueValidator(
        value = value,
        rules = rules,
        initialValidate = initialValidate,
        alwaysShowRule = alwaysShowRule,
        validationSeparator = validationSeparator,
    )
}
