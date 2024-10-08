package com.chrisjenx.yakcov

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneNumberTest {

    @BeforeTest
    fun setUp() {
        initPhoneNumberUtil()
    }

    @Test
    fun isPhoneNumber_fail() {
        assertFalse("43435".isPhoneNumber())
    }

    @Test
    fun isPhoneNumber_success_us() {
        assertTrue("+16508991234".isPhoneNumber())
    }

    @Test
    fun isPhoneNumber_success_noplusnumber() {
        assertTrue("6508991234".isPhoneNumber())
    }

}

expect fun initPhoneNumberUtil()
