package com.chrisjenx.yakcov.strings

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class MinLengthRuleTest {

    @Test
    fun minLength_empty_success() {
        // blank short-circuits to success; Required owns emptiness
        assertEquals(Outcome.SUCCESS, MinLength(3).validate("").outcome())
    }

    @Test
    fun minLength_whitespace_only_success_with_trim() {
        // default trim=true: a whitespace-only value is blank and passes
        assertEquals(Outcome.SUCCESS, MinLength(3).validate("   ").outcome())
    }

    @Test
    fun minLength_whitespace_only_success_without_trim() {
        // even with trim=false, isBlank() short-circuits whitespace-only input
        assertEquals(Outcome.SUCCESS, MinLength(5, trim = false).validate("   ").outcome())
    }

    @Test
    fun minLength_short_value_error() {
        // a non-blank value shorter than the minimum still fails
        assertEquals(Outcome.ERROR, MinLength(3).validate("ab").outcome())
    }

    @Test
    fun minLength_exact_length_success() {
        assertEquals(Outcome.SUCCESS, MinLength(3).validate("abc").outcome())
    }

    @Test
    fun minLength_over_length_success() {
        assertEquals(Outcome.SUCCESS, MinLength(3).validate("abcd").outcome())
    }

    @Test
    fun minLength_trim_counts_trimmed_length() {
        // surrounding whitespace is trimmed before measuring, so "ab" (len 2) fails MinLength(3)
        assertEquals(Outcome.ERROR, MinLength(3, trim = true).validate("  ab  ").outcome())
    }

    @Test
    fun minLength_includeWhiteSpace_false_counts_non_whitespace() {
        // "a b" has 2 non-whitespace chars, which is < 3
        assertEquals(
            Outcome.ERROR,
            MinLength(3, includeWhiteSpace = false).validate("a b").outcome()
        )
    }

    @Test
    fun minLength_includeWhiteSpace_false_success() {
        // "a b c" has 3 non-whitespace chars
        assertEquals(
            Outcome.SUCCESS,
            MinLength(3, includeWhiteSpace = false).validate("a b c").outcome()
        )
    }
}
