package com.chrisjenx.yakcov.generic

import androidx.compose.runtime.mutableStateOf
import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * InList means "the value must be one of the allowed list". Value present in the list -> valid.
 */
class InListRuleTest {

    @Test
    fun inList_value_in_list_success() {
        assertEquals(Outcome.SUCCESS, InList(listOf("a", "b")).validate("a").outcome())
    }

    @Test
    fun inList_value_not_in_list_error() {
        assertEquals(Outcome.ERROR, InList(listOf("a", "b")).validate("c").outcome())
    }

    @Test
    fun inList_null_success() {
        // optional by default; Required owns presence
        assertEquals(Outcome.SUCCESS, InList(listOf("a", "b")).validate(null).outcome())
    }

    @Test
    fun inList_empty_list_value_error_but_null_success() {
        assertEquals(Outcome.ERROR, InList(emptyList<String>()).validate("a").outcome())
        assertEquals(Outcome.SUCCESS, InList(emptyList<String>()).validate(null).outcome())
    }

    @Test
    fun inList_reacts_to_state() {
        val allowed = mutableStateOf(listOf("a"))
        val rule = InList(allowed)
        assertEquals(Outcome.ERROR, rule.validate("b").outcome())
        allowed.value = listOf("a", "b")
        assertEquals(Outcome.SUCCESS, rule.validate("b").outcome())
    }
}
