package com.chrisjenx.yakcov

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNumberTest {

    @Test
    @JSIgnore
    @WasmJsIgnore
    fun isPhoneNumber_fail() {
        initPhoneNumberUtil()
        assertFalse("43435".isPhoneNumber())
    }

    @Test
    @JSIgnore
    @WasmJsIgnore
    fun isPhoneNumber_success_us() {
        initPhoneNumberUtil()
        assertTrue("+16508991234".isPhoneNumber())
    }

    @Test
    @JSIgnore
    @WasmJsIgnore
    fun isPhoneNumber_success_noplusnumber() {
        initPhoneNumberUtil()
        assertTrue("6508991234".isPhoneNumber())
    }

}

expect fun initPhoneNumberUtil()
