package com.chrisjenx.yakcov.sample

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.validate
import com.chrisjenx.yakcov.validationBehavior
import com.chrisjenx.yakcov.supportingText
import com.chrisjenx.yakcov.strings.Email
import com.chrisjenx.yakcov.strings.Required

/**
 * A plain snapshot-presenter — it OWNS the FieldValidator(s) as fields (the draft + validity live
 * here, unit-testable without entering composition). In a real Molecule presenter you would hold
 * these as class fields and return an immutable Model snapshotted from field.value/field.state.
 */
class FormPresenter {
    val email = FieldValidator(initial = "", rules = listOf(Required, Email))

    /** Validates every field (showing errors), then reports whether the form may proceed. */
    fun submit(): Boolean = listOf(email).validate()
}

/**
 * Reusable binder — written once in app code, ~1 line per field at the call site. Holds a local
 * [TextFieldValue] so the cursor/selection survives reformatting, and pushes only the plain text to
 * the presenter's serializable draft.
 */
@Composable
fun ValidatedTextField(
    field: FieldValidator<String>,
    label: String,
    modifier: Modifier = Modifier,
) {
    var tfv by remember { mutableStateOf(TextFieldValue(field.value)) }
    // Reflect external/programmatic draft changes (reset/seed); caret to end of the new text so a
    // shorter value can't leave the selection past the end. Normal typing keeps them in sync, so
    // this branch only runs on a presenter-driven change — never mid-keystroke.
    if (tfv.text != field.value) tfv = TextFieldValue(field.value, TextRange(field.value.length))
    OutlinedTextField(
        value = tfv,
        onValueChange = { tfv = it; field.onValueChange(it.text) },
        label = { Text(label) },
        isError = field.state.isError,
        supportingText = field.state.supportingText(),
        modifier = modifier
            .fillMaxWidth()
            // field.attempts is bumped only by validate() (never onFocusLost()) and never resets, so
            // every repeat invalid submit shakes even though the resulting state is `==` to the last.
            .validationBehavior(
                isError = field.state.isError,
                shakeTrigger = field.attempts,
                onFocusLost = { field.onFocusLost() },
            ),
    )
}

@Composable
fun PresenterFormSample() {
    val presenter = remember { FormPresenter() }
    var submitted by remember { mutableStateOf<Boolean?>(null) }

    Text(text = "Presenter-owned", style = MaterialTheme.typography.headlineSmall)
    ValidatedTextField(presenter.email, label = "Email (presenter-owned)")
    Button(
        onClick = { submitted = presenter.submit() },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Submit") }
    submitted?.let { Text(if (it) "Valid — proceeding" else "Fix the errors above") }
}
