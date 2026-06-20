package com.chrisjenx.yakcov.strings

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class HexColorRuleTest {

    @Test
    fun hexColor_empty_success() {
        // blank short-circuits to success; Required owns emptiness
        assertEquals(Outcome.SUCCESS, HexColor.validate("").outcome())
    }

    @Test
    fun hexColor_six_digit_success() {
        assertEquals(Outcome.SUCCESS, HexColor.validate("#ff8800").outcome())
    }

    @Test
    fun hexColor_three_digit_success() {
        assertEquals(Outcome.SUCCESS, HexColor.validate("#f80").outcome())
    }

    @Test
    fun hexColor_four_digit_alpha_success() {
        assertEquals(Outcome.SUCCESS, HexColor.validate("#f80a").outcome())
    }

    @Test
    fun hexColor_eight_digit_alpha_success() {
        assertEquals(Outcome.SUCCESS, HexColor.validate("#ff8800cc").outcome())
    }

    @Test
    fun hexColor_uppercase_success() {
        assertEquals(Outcome.SUCCESS, HexColor.validate("#FFAABB").outcome())
    }

    @Test
    fun hexColor_missing_hash_error() {
        assertEquals(Outcome.ERROR, HexColor.validate("ff8800").outcome())
    }

    @Test
    fun hexColor_non_hex_chars_error() {
        assertEquals(Outcome.ERROR, HexColor.validate("#gggggg").outcome())
    }

    @Test
    fun hexColor_wrong_length_error() {
        // 5 and 7 digits are not valid CSS hex color lengths
        assertEquals(Outcome.ERROR, HexColor.validate("#fffff").outcome())
        assertEquals(Outcome.ERROR, HexColor.validate("#fffffff").outcome())
    }
}
