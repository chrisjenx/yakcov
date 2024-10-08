package com.chrisjenx.yakcov

import com.google.i18n.phonenumbers.PhoneNumberUtil

private val phoneUtil = PhoneNumberUtil.getInstance()

/**
 * Check if is phone number to best ability of each platform.
 */
actual fun String?.isPhoneNumber(defaultRegion: String?): Boolean {
    this ?: return false
    return try {
        val result = phoneUtil.parse(this, defaultRegion?.uppercase())
        phoneUtil.isValidNumber(result)
    } catch (t: Throwable) {
        false
    }
}