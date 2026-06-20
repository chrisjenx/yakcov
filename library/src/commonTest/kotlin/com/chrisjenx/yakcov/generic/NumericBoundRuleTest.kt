package com.chrisjenx.yakcov.generic

import androidx.compose.runtime.mutableStateOf
import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class NumericBoundRuleTest {

    // --- Min ---

    @Test
    fun min_null_success() {
        assertEquals(Outcome.SUCCESS, Min(5).validate(null).outcome())
    }

    @Test
    fun min_at_bound_success() {
        assertEquals(Outcome.SUCCESS, Min(5).validate(5).outcome())
    }

    @Test
    fun min_above_bound_success() {
        assertEquals(Outcome.SUCCESS, Min(5).validate(6).outcome())
    }

    @Test
    fun min_below_bound_error() {
        assertEquals(Outcome.ERROR, Min(5).validate(4).outcome())
    }

    @Test
    fun min_reacts_to_state() {
        val bound = mutableStateOf(5)
        val rule = Min(bound)
        assertEquals(Outcome.ERROR, rule.validate(4).outcome())
        bound.value = 3
        assertEquals(Outcome.SUCCESS, rule.validate(4).outcome())
    }

    @Test
    fun min_supports_long_and_double() {
        assertEquals(Outcome.ERROR, Min(5L).validate(4L).outcome())
        assertEquals(Outcome.ERROR, Min(2.5).validate(2.0).outcome())
        assertEquals(Outcome.SUCCESS, Min(2.5).validate(2.5).outcome())
    }

    @Test
    fun bounds_treat_nan_as_passthrough_consistently() {
        // NaN is not meaningfully comparable; treat it like null/absent across all three rules
        // (also matches the String MinValue/MaxValue behavior) so Min/Max/InRange agree.
        assertEquals(Outcome.SUCCESS, Min(0.0).validate(Double.NaN).outcome())
        assertEquals(Outcome.SUCCESS, Max(100.0).validate(Double.NaN).outcome())
        assertEquals(Outcome.SUCCESS, InRange(1.0, 10.0).validate(Double.NaN).outcome())
        assertEquals(Outcome.SUCCESS, Min(0f).validate(Float.NaN).outcome())
        assertEquals(Outcome.SUCCESS, Max(0f).validate(Float.NaN).outcome())
    }

    // --- Max ---

    @Test
    fun max_null_success() {
        assertEquals(Outcome.SUCCESS, Max(10).validate(null).outcome())
    }

    @Test
    fun max_at_bound_success() {
        assertEquals(Outcome.SUCCESS, Max(10).validate(10).outcome())
    }

    @Test
    fun max_above_bound_error() {
        assertEquals(Outcome.ERROR, Max(10).validate(11).outcome())
    }

    @Test
    fun max_below_bound_success() {
        assertEquals(Outcome.SUCCESS, Max(10).validate(5).outcome())
    }

    @Test
    fun max_reacts_to_state() {
        val bound = mutableStateOf(10)
        val rule = Max(bound)
        assertEquals(Outcome.ERROR, rule.validate(11).outcome())
        bound.value = 12
        assertEquals(Outcome.SUCCESS, rule.validate(11).outcome())
    }

    // --- InRange ---

    @Test
    fun inRange_null_success() {
        assertEquals(Outcome.SUCCESS, InRange(1, 10).validate(null).outcome())
    }

    @Test
    fun inRange_within_success() {
        assertEquals(Outcome.SUCCESS, InRange(1, 10).validate(5).outcome())
    }

    @Test
    fun inRange_at_min_success() {
        // inclusive lower bound — pins < vs <=
        assertEquals(Outcome.SUCCESS, InRange(1, 10).validate(1).outcome())
    }

    @Test
    fun inRange_at_max_success() {
        // inclusive upper bound — pins > vs >=
        assertEquals(Outcome.SUCCESS, InRange(1, 10).validate(10).outcome())
    }

    @Test
    fun inRange_below_error() {
        assertEquals(Outcome.ERROR, InRange(1, 10).validate(0).outcome())
    }

    @Test
    fun inRange_above_error() {
        assertEquals(Outcome.ERROR, InRange(1, 10).validate(11).outcome())
    }
}
