package com.chrisjenx.yakcov.strings

import androidx.compose.runtime.mutableStateOf
import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class OneOfRuleTest {

    @Test
    fun oneOf_exact_match_success() {
        assertEquals(Outcome.SUCCESS, OneOf(setOf("US", "GB")).validate("US").outcome())
    }

    @Test
    fun oneOf_ignore_case_success_by_default() {
        assertEquals(Outcome.SUCCESS, OneOf(setOf("US", "GB")).validate("us").outcome())
    }

    @Test
    fun oneOf_trims_input_by_default_success() {
        assertEquals(Outcome.SUCCESS, OneOf(setOf("US", "GB")).validate("  US  ").outcome())
    }

    @Test
    fun oneOf_not_in_set_error() {
        assertEquals(Outcome.ERROR, OneOf(setOf("US", "GB")).validate("FR").outcome())
    }

    @Test
    fun oneOf_empty_success() {
        // blank short-circuits; Required owns emptiness
        assertEquals(Outcome.SUCCESS, OneOf(setOf("US", "GB")).validate("").outcome())
    }

    @Test
    fun oneOf_case_sensitive_error() {
        assertEquals(Outcome.ERROR, OneOf(setOf("US"), ignoreCase = false).validate("us").outcome())
    }

    @Test
    fun oneOf_no_trim_error() {
        assertEquals(Outcome.ERROR, OneOf(setOf("US"), trim = false).validate(" US ").outcome())
    }

    @Test
    fun oneOf_reacts_to_state() {
        val allowed = mutableStateOf(setOf("US"))
        val rule = OneOf(allowed)
        assertEquals(Outcome.ERROR, rule.validate("GB").outcome())
        allowed.value = setOf("US", "GB")
        assertEquals(Outcome.SUCCESS, rule.validate("GB").outcome())
    }
}
