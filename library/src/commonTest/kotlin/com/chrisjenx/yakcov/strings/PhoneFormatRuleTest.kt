package com.chrisjenx.yakcov.strings

import com.chrisjenx.yakcov.ValidationResult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneFormatRuleTest {

    @Test
    fun blankInputIsSuccess() {
        // Required owns emptiness; a format rule passes blank input.
        assertEquals(Outcome.SUCCESS, PhoneFormat.validate("").outcome())
    }

    @Test
    fun wellFormedNumberIsSuccess() {
        assertEquals(Outcome.SUCCESS, PhoneFormat.validate("+1 650-899-1234").outcome())
    }

    @Test
    fun tooFewDigitsIsError() {
        assertEquals(Outcome.ERROR, PhoneFormat.validate("43435").outcome())
    }

    @Test
    fun lettersAreError() {
        assertEquals(Outcome.ERROR, PhoneFormat.validate("1-800-FLOWERS").outcome())
    }

    @Test
    fun wrongRegionNumberStillPasses_formatOnly() {
        // A GB number that Phone("US") would reject — PhoneFormat only checks format,
        // so it passes. Region-aware rejection is Phone's job.
        assertEquals(Outcome.SUCCESS, PhoneFormat.validate("07745973912").outcome())
    }
}
