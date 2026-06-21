package com.chrisjenx.yakcov.docs.recipes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.chrisjenx.yakcov.RegularValidationResult
import com.chrisjenx.yakcov.ValidationResult
import com.chrisjenx.yakcov.ValueValidatorRule
import com.chrisjenx.yakcov.onlyWhen
import com.chrisjenx.yakcov.generic.Max
import com.chrisjenx.yakcov.generic.Min
import com.chrisjenx.yakcov.generic.rememberGenericValueValidator
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Phone
import com.chrisjenx.yakcov.strings.PhoneFormat
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
// --8<-- [start:phoneFormat]
@Composable
fun PhoneFormatField() {
    // PhoneFormat is a lenient, dependency-free format check — no libphonenumber needed.
    // The server stays authoritative and normalizes to E.164.
    val phone = rememberTextFieldValueValidator(rules = listOf(Required, PhoneFormat))
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
// --8<-- [end:phoneFormat]

// --8<-- [start:conditional]
// onlyWhen wraps any rule so it runs only while a State<Boolean> is true; otherwise the
// value passes. Collapses conditionally-required / optional-when-hidden fields into the
// existing rules instead of a bespoke rule per case.
fun taxIdRules(isBusiness: State<Boolean>): List<ValueValidatorRule<String>> =
    listOf(Required.onlyWhen(isBusiness), MinLength(9).onlyWhen(isBusiness))
// --8<-- [end:conditional]

// --8<-- [start:optional-min-length]
// "Optional, but at least N chars if the user types something." Gate only Required by the
// state; leave MinLength ungated. Required.onlyWhen owns the empty case (blank passes when
// not required), while the ungated MinLength still rejects a too-short value once typed —
// because every string rule treats blank as valid and defers emptiness to Required.
fun nicknameRules(required: State<Boolean>): List<ValueValidatorRule<String>> =
    listOf(Required.onlyWhen(required), MinLength(3))
// --8<-- [end:optional-min-length]

// --8<-- [start:generic-bounds]
@Composable
fun QuantityField() {
    // Typed numeric bounds validate the value directly (no string parsing). null and NaN
    // pass — pair with generic Required for presence. The value type must be nullable
    // (Int?) so the bounds' null pass-through type-checks.
    val quantity = rememberGenericValueValidator<Int?>(
        state = 1,
        rules = listOf(Min(1), Max(99)),
    )
    Text(if (quantity.isValid) "OK" else "Enter 1–99")
}
// --8<-- [end:generic-bounds]
