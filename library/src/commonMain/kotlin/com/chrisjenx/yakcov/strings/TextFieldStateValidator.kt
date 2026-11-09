package com.chrisjenx.yakcov.strings

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.ValueValidator
import com.chrisjenx.yakcov.ValueValidator.Companion.defaultValidationSeparator
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.onFocusCursorToEnd
import kotlinx.coroutines.CoroutineScope

/**
 * The [TextFieldState] counterpart to [TextFieldValueValidator]: same [ValueValidatorRule]s, same
 * error timing, same `with(validator) { … }` call shape — but the text field owns its text through a
 * [TextFieldState] instead of a `value` + `onValueChange` pair.
 *
 * Validation *observes* the state. Rules are evaluated against `textFieldState.text` through a
 * snapshot read, so [validationResults]/[isValid]/[isError] update on every edit with no
 * `onValueChange` wiring. The only thing that needs a running observer is the one-time move from
 * pristine to validating on the user's first edit (the step `onValueChange` performs for
 * [TextFieldValueValidator]); [rememberTextFieldStateValidator] and [validationConfig] both run it.
 * A validator constructed directly and used without [validationConfig] must run
 * [observeTextChanges] itself (see that function).
 *
 * Because it is a `ValueValidator<String, String>`, it mixes freely with [TextFieldValueValidator]
 * in `listOf(a, b).validate()`, so a form can migrate one field at a time.
 *
 * ## Usage
 * ```
 * val email = rememberTextFieldStateValidator(rules = listOf(Required, Email))
 * with(email) {
 *     OutlinedTextField(
 *         state = textFieldState,
 *         modifier = Modifier.validationConfig(
 *             validateOnFocusLost = true,
 *             shakeOnInvalid = true,
 *             showErrorOnInteraction = false, // errors on blur/submit, never while typing
 *         ),
 *         isError = isError(),
 *         supportingText = supportingText(),
 *     )
 * }
 * ```
 *
 * ## Programmatic writes
 * - `validator.value = text` replaces the text, puts the caret at the end, and is **not** treated as
 *   user interaction — exactly like assigning [TextFieldValueValidator.value]. Use it for a re-seed.
 * - `validator.validate(text)` replaces the text and shows errors (submit semantics).
 * - `validator.reset()` returns to the pristine state (honoring `initialValidate`).
 * - Writing to [textFieldState] directly (`setTextAndPlaceCursorAtEnd`, `clearText`, `edit { }`) is
 *   indistinguishable from typing, so it counts as interaction — right for a suggestion pick.
 *
 * ## Transformations
 * Rules see `textFieldState.text`: the text **after** any `InputTransformation` (which only runs on
 * user edits, never on programmatic writes) and **before** any `OutputTransformation` (which only
 * changes what is drawn). See the decision record in `docs/decisions/textfieldstate.md`.
 *
 * Like the other validators this is `@Stable`, not `@Immutable`: it holds mutable state. Never put it
 * in an immutable reducer `Model`, and never copy it.
 *
 * @param textFieldState the state the text field renders; the validator never copies it.
 * @param rules evaluated against `textFieldState.text.toString()`.
 * @param initialValidate start validating (and showing errors) immediately. Needed for a field whose
 *  starting value is already valid (a blank optional `Email`, a quantity seeded with `"1"`), so it
 *  doesn't start as not-valid.
 * @param alwaysShowRule show the rule message via [supportingText] as a non-error before validation.
 */
@Stable
class TextFieldStateValidator private constructor(
    val textFieldState: TextFieldState,
    private val text: TextFieldStateText,
    rules: List<ValueValidatorRule<String>>,
    initialValidate: Boolean,
    alwaysShowRule: Boolean,
    validationSeparator: String,
) : ValueValidator<String, String>(
    state = text,
    rules = rules,
    initialValidate = initialValidate,
    alwaysShowRule = alwaysShowRule,
    validationSeparator = validationSeparator,
    validateMapper = { validate(it) },
) {

    constructor(
        textFieldState: TextFieldState = TextFieldState(),
        rules: List<ValueValidatorRule<String>> = listOf(Required),
        initialValidate: Boolean = false,
        alwaysShowRule: Boolean = false,
        validationSeparator: String = defaultValidationSeparator,
    ) : this(
        textFieldState = textFieldState,
        text = TextFieldStateText(textFieldState),
        rules = rules,
        initialValidate = initialValidate,
        alwaysShowRule = alwaysShowRule,
        validationSeparator = validationSeparator,
    )

    constructor(
        initialText: String,
        rules: List<ValueValidatorRule<String>> = listOf(Required),
        initialValidate: Boolean = false,
        alwaysShowRule: Boolean = false,
        validationSeparator: String = defaultValidationSeparator,
    ) : this(
        textFieldState = TextFieldState(initialText),
        rules = rules,
        initialValidate = initialValidate,
        alwaysShowRule = alwaysShowRule,
        validationSeparator = validationSeparator,
    )

    /**
     * Watches [textFieldState] and treats every text change that did not come through this validator
     * (typing, paste, autofill, IME, or a direct write to the state) as user interaction: the field
     * starts validating, and errors show per `validationConfig(showErrorOnInteraction = …)`. Selection
     * and composition changes are ignored.
     *
     * Suspends until cancelled. [rememberTextFieldStateValidator] and [validationConfig] already run
     * it while the field is composed; call it yourself only for a validator you construct directly and
     * don't attach with [validationConfig], e.g. `LaunchedEffect(validator) { validator.observeTextChanges() }`
     * or from a component's lifecycle scope. Running it more than once concurrently is harmless.
     */
    suspend fun observeTextChanges() {
        snapshotFlow { textFieldState.text.toString() }.collect { latest ->
            if (latest != text.acknowledged) {
                text.acknowledged = latest
                onValueChange(value = null)
            }
        }
    }

    @Composable
    override fun ObserveValueChanges() {
        LaunchedEffect(this) { observeTextChanges() }
    }

    /**
     * Returns a [Modifier] that moves the cursor to the end of the text when the field gains focus.
     *
     * @param highlight if true selects the whole text instead.
     * @see com.chrisjenx.yakcov.onFocusCursorToEnd the free form, usable without a validator.
     */
    @Composable
    fun Modifier.onFocusCursorToEnd(
        scope: CoroutineScope = rememberCoroutineScope(),
        highlight: Boolean = false,
    ): Modifier = onFocusCursorToEnd(
        state = textFieldState,
        scope = scope,
        highlight = highlight,
    )

    override fun toString(): String = textFieldState.text.toString()
}

/**
 * Remembers a [TextFieldStateValidator] over an existing [textFieldState] and runs its
 * [TextFieldStateValidator.observeTextChanges] while composed.
 *
 * @see TextFieldStateValidator for docs
 */
@Composable
fun rememberTextFieldStateValidator(
    textFieldState: TextFieldState,
    rules: List<ValueValidatorRule<String>> = listOf(Required),
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
): TextFieldStateValidator {
    val validator = remember(textFieldState) {
        TextFieldStateValidator(
            textFieldState = textFieldState,
            rules = rules,
            initialValidate = initialValidate,
            alwaysShowRule = alwaysShowRule,
            validationSeparator = validationSeparator,
        )
    }
    LaunchedEffect(validator) { validator.observeTextChanges() }
    return validator
}

/**
 * Remembers a [TextFieldStateValidator] over a new saveable [TextFieldState] (via
 * [rememberTextFieldState], caret at the end of [initialText]) and runs its
 * [TextFieldStateValidator.observeTextChanges] while composed.
 *
 * The text survives configuration change / process death; the validation state, as with
 * [rememberTextFieldValueValidator], does not.
 *
 * @see TextFieldStateValidator for docs
 */
@Composable
fun rememberTextFieldStateValidator(
    initialText: String = "",
    rules: List<ValueValidatorRule<String>> = listOf(Required),
    initialValidate: Boolean = false,
    alwaysShowRule: Boolean = false,
    validationSeparator: String = defaultValidationSeparator,
): TextFieldStateValidator = rememberTextFieldStateValidator(
    textFieldState = rememberTextFieldState(initialText),
    rules = rules,
    initialValidate = initialValidate,
    alwaysShowRule = alwaysShowRule,
    validationSeparator = validationSeparator,
)

/**
 * Presents a [TextFieldState]'s text as the [MutableState] that [ValueValidator] is built on.
 *
 * Reads are snapshot reads of the state, so the validator's derived results track every edit. Writes
 * replace the text with the caret at the end and are recorded in [acknowledged], which is how
 * [TextFieldStateValidator.observeTextChanges] tells a programmatic write through the validator from
 * user input.
 */
private class TextFieldStateText(private val state: TextFieldState) : MutableState<String> {

    /** The last text the validator has accounted for; a different text is user interaction. */
    var acknowledged: String = state.text.toString()

    override var value: String
        get() = state.text.toString()
        set(value) {
            acknowledged = value
            if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
        }

    override fun component1(): String = value
    override fun component2(): (String) -> Unit = { value = it }

    override fun toString(): String = value
}
