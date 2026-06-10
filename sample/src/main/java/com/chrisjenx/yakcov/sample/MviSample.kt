package com.chrisjenx.yakcov.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import com.chrisjenx.yakcov.FieldValidationState
import com.chrisjenx.yakcov.RegularValidationResult
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.hasNoErrors
import com.chrisjenx.yakcov.onFocusLost
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.toFieldState

/* =====================================================================================
 * REDUCER-MVI integration of the yakcov *headless* validator.
 *
 * Contrast with PresenterSample.kt: there a mutable `FieldValidator` owns each draft + its
 * validity. Here NOTHING mutable lives in the form logic. The screen is `UI = f(Model)`; state
 * only ever changes via `dispatch(Event)` -> the pure `reduce(model, event)`. Validation is a
 * pure fold — `List<ValueValidatorRule>.toFieldState(value, showError)` — so `reduce` never
 * touches Compose/coroutines/IO and is unit-testable with zero composition.
 *
 * The library's key value is one state with TWO independent channels:
 *   - severity  : recomputed on EVERY keystroke. Answers "is it valid?"   -> drives canSubmit.
 *   - showError : only flips true on focus-loss/submit. Answers "show it yet?" -> drives the red text.
 * ===================================================================================== */

// --- Rules: plain top-level lists, declared once, shared by reduce() AND any unit test. --------
// MinLength(Int) uses the plain Int constructor (no live Compose State), so it is safe to fold
// from a non-Composable reducer.
private val emailRules: List<ValueValidatorRule<String>> = listOf(Required, Email)
private val passwordRules: List<ValueValidatorRule<String>> = listOf(Required, MinLength(8))

/**
 * Cross-field "confirm == password" for the PURE reducer path. The built-in `PasswordMatches`
 * couples to a *mutable* `ValueValidator`, so it can't live in an immutable model; instead we build
 * the confirm rules fresh inside `reduce()`, closing over the current password draft via this tiny
 * custom [ValueValidatorRule] (it's a SAM). This is the pattern to copy for any cross-field rule.
 */
private fun confirmRules(password: String): List<ValueValidatorRule<String>> = listOf(
    Required,
    ValueValidatorRule { confirm ->
        if (confirm == password) RegularValidationResult.success()
        else RegularValidationResult.error("Passwords do not match")
    },
)

/** Which field an event targets — keeps the [Event] surface to three cases for any field count. */
enum class Field { Email, Password, Confirm }

// --- State: every field is a val; FieldValidationState is @Immutable, so Model is value-stable. -
data class Model(
    val emailDraft: String = "",
    val emailState: FieldValidationState = FieldValidationState.Pristine,
    val passwordDraft: String = "",
    val passwordState: FieldValidationState = FieldValidationState.Pristine,
    val confirmDraft: String = "",
    val confirmState: FieldValidationState = FieldValidationState.Pristine,
    /** Submit outcome: null = not attempted, true = accepted, false = blocked (show errors + shake). */
    val submitted: Boolean? = null,
) {
    /**
     * Form-level validity, driven purely by SEVERITY (ignores showError), so the UI can react to
     * validity from the very first frame while no red text is shown yet. Accurate at load ONLY
     * because [initialModel] seeds each state by folding the rules with showError = false — a
     * never-validated `FieldValidationState.Pristine` has SUCCESS severity and `hasNoErrors()`
     * would wrongly report a required-but-empty field as submittable.
     */
    val canSubmit: Boolean
        get() = listOf(emailState, passwordState, confirmState).hasNoErrors()
}

/** Seed with rules pre-run (showError = false) so [Model.canSubmit] is honest before any input. */
fun initialModel(): Model = Model(
    emailState = emailRules.toFieldState("", showError = false),
    passwordState = passwordRules.toFieldState("", showError = false),
    confirmState = confirmRules("").toFieldState("", showError = false),
)

// --- Events: the closed set of user intents. Exhaustive `when` keeps reduce honest. -----------
sealed interface Event {
    /** Typing. Recompute severity live, but PRESERVE this field's showError (no error pops early). */
    data class Changed(val field: Field, val text: String) : Event

    /** Focus left a field — show errors on just that one field (show-on-focus-loss). */
    data class FocusLost(val field: Field) : Event

    /** Submit — show errors on ALL fields, then record acceptance off severity (show-on-submit). */
    data object Submit : Event
}

// --- reduce(): THE pure core. No Compose, no coroutines, no IO, no Android. --------------------
/**
 * `(Model, Event) -> Model`. `showError` is *threaded*, not recomputed: Changed keeps the prior
 * showError; FocusLost/Submit force it true. Because the confirm rule closes over the password
 * draft, a password change also re-validates confirm (otherwise a stale match would stay green).
 */
fun reduce(model: Model, event: Event): Model = when (event) {
    is Event.Changed -> when (event.field) {
        Field.Email -> model.copy(
            emailDraft = event.text,
            emailState = emailRules.toFieldState(event.text, showError = model.emailState.showError),
            submitted = null,
        )
        Field.Password -> model.copy(
            passwordDraft = event.text,
            passwordState = passwordRules.toFieldState(event.text, showError = model.passwordState.showError),
            // password drives the cross-field rule, so re-fold confirm against the new password
            confirmState = confirmRules(event.text)
                .toFieldState(model.confirmDraft, showError = model.confirmState.showError),
            submitted = null,
        )
        Field.Confirm -> model.copy(
            confirmDraft = event.text,
            confirmState = confirmRules(model.passwordDraft)
                .toFieldState(event.text, showError = model.confirmState.showError),
            submitted = null,
        )
    }

    is Event.FocusLost -> when (event.field) {
        Field.Email -> model.copy(
            emailState = emailRules.toFieldState(model.emailDraft, showError = true),
        )
        Field.Password -> model.copy(
            passwordState = passwordRules.toFieldState(model.passwordDraft, showError = true),
        )
        Field.Confirm -> model.copy(
            confirmState = confirmRules(model.passwordDraft).toFieldState(model.confirmDraft, showError = true),
        )
    }

    Event.Submit -> {
        // Show errors on every field (force showError = true), then read validity off severity.
        val surfaced = model.copy(
            emailState = emailRules.toFieldState(model.emailDraft, showError = true),
            passwordState = passwordRules.toFieldState(model.passwordDraft, showError = true),
            confirmState = confirmRules(model.passwordDraft).toFieldState(model.confirmDraft, showError = true),
        )
        surfaced.copy(submitted = surfaced.canSubmit)
    }
}

// --- Store: the single source of truth + single dispatch entry point. Plain class, zero deps. --
/**
 * Holds `model` in a snapshot cell and funnels all events through [dispatch] -> [reduce]. The only
 * Compose-aware piece; the reducer it calls stays pure. Must stay @Stable (NOT @Immutable): it owns
 * a `mutableStateOf`. Swap for a ViewModel + StateFlow later without touching `reduce`/`Model`/`Event`.
 */
@Stable
class Store(initial: Model) {
    var model by mutableStateOf(initial)
        private set

    fun dispatch(event: Event) {
        model = reduce(model, event)
    }
}

/**
 * THE serializable payoff. We persist only the three drafts + each field's showError flag (the same
 * data `FieldValidationState.Saver` carries; `result` is `@Transient`). On restore we RE-RUN the
 * rules through `toFieldState` to repopulate the transient message while preserving showError — so a
 * surfaced error survives process death with its text intact. Zero new deps (runtime-saveable comes
 * transitively with material3). The whole [Store] round-trips from one `rememberSaveable`.
 */
private val modelSaver: Saver<Model, Any> = listSaver(
    save = { m ->
        listOf(
            m.emailDraft, m.emailState.showError,
            m.passwordDraft, m.passwordState.showError,
            m.confirmDraft, m.confirmState.showError,
        )
    },
    restore = { s ->
        val emailDraft = s[0] as String
        val passwordDraft = s[2] as String
        val confirmDraft = s[4] as String
        Model(
            emailDraft = emailDraft,
            emailState = emailRules.toFieldState(emailDraft, showError = s[1] as Boolean),
            passwordDraft = passwordDraft,
            passwordState = passwordRules.toFieldState(passwordDraft, showError = s[3] as Boolean),
            confirmDraft = confirmDraft,
            confirmState = confirmRules(passwordDraft).toFieldState(confirmDraft, showError = s[5] as Boolean),
        )
    },
)

private val storeSaver: Saver<Store, Any> = Saver(
    save = { with(modelSaver) { save(it.model) } },
    restore = { saved -> Store(modelSaver.restore(saved) ?: initialModel()) },
)

// --- UI = f(state). One stateless binder; it only reads state and dispatches events. ----------
/**
 * The only place the `@Transient` message re-enters composition (supportingText() resolves it). A
 * local [TextFieldValue] keeps the caret stable across recomposition; only `.text` flows into the
 * immutable Model, which stays the source of truth.
 */
@Composable
private fun MviTextField(
    label: String,
    draft: String,
    state: FieldValidationState,
    onChanged: (String) -> Unit,
    onFocusLost: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    mask: Boolean = false,
) {
    var tfv by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(draft, TextRange(draft.length)))
    }
    // Reflect model-driven draft changes (restore/reset) without clobbering the caret mid-typing.
    if (tfv.text != draft) tfv = TextFieldValue(draft, TextRange(draft.length))
    OutlinedTextField(
        value = tfv,
        onValueChange = { tfv = it; onChanged(it.text) },
        label = { Text(label) },
        isError = state.isError,                 // showError && severity == ERROR (gated on showError)
        supportingText = state.supportingText(), // null until shown -> no message while typing
        singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = keyboardType),
        visualTransformation = if (mask) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusLost(onFocusLost),             // show-on-focus-loss for this field
    )
}

/** Entry point — add `MviFormSample()` to SampleActivity's scrolling Column. */
@Composable
fun MviFormSample() {
    // The whole store survives config change / process death from a SINGLE rememberSaveable; on
    // restore the rules re-run so the @Transient messages come back with showError preserved.
    val store = rememberSaveable(saver = storeSaver) { Store(initialModel()) }
    val model = store.model

    Text(text = "Reducer-MVI (headless)", style = MaterialTheme.typography.headlineSmall)

    MviTextField(
        label = "Email (MVI)",
        draft = model.emailDraft,
        state = model.emailState,
        onChanged = { store.dispatch(Event.Changed(Field.Email, it)) },
        onFocusLost = { store.dispatch(Event.FocusLost(Field.Email)) },
        keyboardType = KeyboardType.Email,
    )
    MviTextField(
        label = "Password (MVI, min 8)",
        draft = model.passwordDraft,
        state = model.passwordState,
        onChanged = { store.dispatch(Event.Changed(Field.Password, it)) },
        onFocusLost = { store.dispatch(Event.FocusLost(Field.Password)) },
        keyboardType = KeyboardType.Password,
        mask = true,
    )
    MviTextField(
        label = "Confirm password (MVI)",
        draft = model.confirmDraft,
        state = model.confirmState,
        onChanged = { store.dispatch(Event.Changed(Field.Confirm, it)) },
        onFocusLost = { store.dispatch(Event.FocusLost(Field.Confirm)) },
        keyboardType = KeyboardType.Password,
        mask = true,
    )

    // SEVERITY drives this label live (it flips as you type, BEFORE any error text shows) — the
    // clearest view of the "is it valid?" channel. The button stays enabled so Submit can act as the
    // show-all-errors BACKSTOP for fields the user never focused (so onFocusLost never fired) — e.g.
    // tapping Submit on a fresh form, or skipping a required field. Per-field focus-loss already shows
    // errors on touched fields; Submit guarantees the rest. Prefer disable-on-invalid instead? Set
    // `enabled = model.canSubmit` — but then you lose that backstop (you can't submit to show errors).
    Button(
        onClick = { store.dispatch(Event.Submit) },   // show-all-errors backstop, then check
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (model.canSubmit) "Create account" else "Fix errors to submit") }

    // Submit outcome. `false` occurs when Submit surfaces a still-invalid field the user never focused
    // (submit-first, or an untouched required field) — a clean point to drive a shake on `false`
    // (keep ShakingState UI-owned).
    model.submitted?.let { Text(if (it) "Valid — proceeding" else "Fix the errors above") }
}
