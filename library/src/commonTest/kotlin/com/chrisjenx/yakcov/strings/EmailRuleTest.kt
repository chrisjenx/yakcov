package com.chrisjenx.yakcov.strings

import com.chrisjenx.yakcov.IOSIgnore
import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.initPhoneNumberUtil
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailRuleTest {

    @Test
    fun email_empty_success() {
        assertEquals(Outcome.SUCCESS, Email.validate("").outcome())
    }

    @Test
    fun email_invalid_error() {
        assertEquals(Outcome.ERROR, Email.validate("43435").outcome())
    }

    @Test
    fun email_valid_success() {
        assertEquals(Outcome.SUCCESS, Email.validate("me@me.com").outcome())
    }

}
