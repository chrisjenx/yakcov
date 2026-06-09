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
import androidx.compose.ui.text.input.TextFieldValue
import com.chrisjenx.yakcov.FieldValidator
import com.chrisjenx.yakcov.allValid
import com.chrisjenx.yakcov.onFocusLost
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

    /** Reveals every field, then reports whether the form may proceed. */
    fun submit(): Boolean = listOf(email).allValid()
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
    // Reflect external/programmatic draft changes while preserving local selection.
    if (tfv.text != field.value) tfv = tfv.copy(text = field.value)
    OutlinedTextField(
        value = tfv,
        onValueChange = { tfv = it; field.onChange(it.text) },
        label = { Text(label) },
        isError = field.state.isError,
        supportingText = field.state.supportingText(),
        modifier = modifier
            .fillMaxWidth()
            .onFocusLost { field.onBlur() },
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
    submitted?.let { Text(if (it) "Valid — proceeding" else "Invalid — shake here") }
}
