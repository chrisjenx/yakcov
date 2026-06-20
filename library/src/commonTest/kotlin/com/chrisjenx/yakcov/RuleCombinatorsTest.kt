package com.chrisjenx.yakcov

import androidx.compose.runtime.mutableStateOf
import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.strings.MinLength
import com.chrisjenx.yakcov.strings.Required
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleCombinatorsTest {

    @Test
    fun optional_enabled_delegates_to_rule_error() {
        // Required fails on blank; when enabled the wrapped failure passes through.
        assertEquals(Outcome.ERROR, Optional(enabled = true, rule = Required).validate("").outcome())
    }

    @Test
    fun optional_enabled_delegates_to_rule_success() {
        assertEquals(Outcome.SUCCESS, Optional(enabled = true, rule = Required).validate("hi").outcome())
    }

    @Test
    fun optional_disabled_always_success() {
        // Required would fail on blank, but the gate is off so the rule is skipped.
        assertEquals(Outcome.SUCCESS, Optional(enabled = false, rule = Required).validate("").outcome())
    }

    @Test
    fun optional_reacts_to_state() {
        val enabled = mutableStateOf(false)
        val rule = Optional(enabled = enabled, rule = Required)
        assertEquals(Outcome.SUCCESS, rule.validate("").outcome())
        enabled.value = true
        assertEquals(Outcome.ERROR, rule.validate("").outcome())
    }

    @Test
    fun onlyWhen_boolean_true_delegates() {
        assertEquals(Outcome.ERROR, Required.onlyWhen(true).validate("").outcome())
    }

    @Test
    fun onlyWhen_boolean_false_skips() {
        assertEquals(Outcome.SUCCESS, Required.onlyWhen(false).validate("").outcome())
    }

    @Test
    fun onlyWhen_state_gates_wrapped_rule() {
        val enabled = mutableStateOf(true)
        // MinLength(8) fails on a short non-blank value when the gate is open.
        assertEquals(Outcome.ERROR, MinLength(8).onlyWhen(enabled).validate("abc").outcome())
        enabled.value = false
        assertEquals(Outcome.SUCCESS, MinLength(8).onlyWhen(enabled).validate("abc").outcome())
    }
}
