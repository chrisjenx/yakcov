package com.chrisjenx.yakcov.strings

import com.chrisjenx.yakcov.JSIgnore
import com.chrisjenx.yakcov.ValidationResult.Outcome
import com.chrisjenx.yakcov.WasmJsIgnore
import com.chrisjenx.yakcov.initPhoneNumberUtil
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneRuleTest {

    @BeforeTest
    fun setUp() {
        initPhoneNumberUtil()
    }

    @Test
    fun phoneNumber_empty_success() {
        assertEquals(Outcome.SUCCESS, Phone().validate("").outcome())
    }

    @Test
    fun phoneNumber_invalid_error() {
        assertEquals(Outcome.ERROR, Phone().validate("43435").outcome())
    }

    @Test
    @JSIgnore
    @WasmJsIgnore
    fun phoneNumber_noRegion() {
        assertEquals(Outcome.SUCCESS, Phone().validate("6508991234").outcome())
    }

    @Test
    @JSIgnore
    @WasmJsIgnore
    fun phoneNumber_wrongRegion() {
        // This is a UK number should error for US
        assertEquals(Outcome.ERROR, Phone("US").validate("07745973912").outcome())
        assertEquals(Outcome.SUCCESS, Phone("GB").validate("07740973910").outcome())
    }

    @Test
    @JSIgnore
    @WasmJsIgnore
    fun phoneNumber_withRegion() {
        assertEquals(Outcome.SUCCESS, Phone().validate("+16508991234").outcome())
    }

}

