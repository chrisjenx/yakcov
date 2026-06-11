package com.chrisjenx.yakcov.docs.recipes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.RegularValidationResult
import com.chrisjenx.yakcov.ValidationResult
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.strings.Phone
import com.chrisjenx.yakcov.strings.Required
import com.chrisjenx.yakcov.strings.rememberTextFieldValueValidator

// --8<-- [start:custom-rule]
// A reusable rule: an object (or data class for parameterised rules) implementing
// ValueValidatorRule<V>. Return success for blank input — let Required own emptiness.
data object UsZipCode : ValueValidatorRule<String> {
    private val zip = Regex("""^\d{5}(-\d{4})?$""")
    override fun validate(value: String): ValidationResult {
        if (value.isBlank()) return RegularValidationResult.success()
        return if (zip.matches(value)) RegularValidationResult.success()
        else RegularValidationResult.error("Enter a valid ZIP code")
    }
}
// --8<-- [end:custom-rule]

// --8<-- [start:custom-rule-sam]
// ValueValidatorRule is a fun interface — one-off rules can be inline SAM lambdas.
// Severity is graded: error/warning/info/success all flow through supportingText.
val NoPlusAddressing = ValueValidatorRule<String> { value ->
    if ("+" in value) RegularValidationResult.warning("Plus-addressing may break receipts")
    else RegularValidationResult.success()
}
// --8<-- [end:custom-rule-sam]

// --8<-- [start:phone]
@Composable
fun PhoneField() {
    // Phone(defaultRegion) is powered by libphonenumber-kotlin, which yakcov declares
    // compileOnly — your app must add the dependency (see this page's install section).
    val phone = rememberTextFieldValueValidator(rules = listOf(Required, Phone("US")))
    with(phone) {
        OutlinedTextField(
            value = value,
            onValueChange = ::onValueChange,
            label = { Text("Phone") },
            isError = isError(),
            supportingText = supportingText(),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .validationConfig(validateOnFocusLost = true),
        )
    }
}
// --8<-- [end:phone]
