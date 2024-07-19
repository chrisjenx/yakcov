package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.metadata.init.ClassPathResourceMetadataLoader


private val phoneUtil = PhoneNumberUtil.createInstance(ClassPathResourceMetadataLoader())

/**
 * Check if is phone number to best ability of each platform.
 */
actual fun String?.isPhoneNumber(): Boolean {
    this ?: return false
    return try {
        val result = phoneUtil.parse(this, "US")
        phoneUtil.isValidNumber(result)
    } catch (t: Throwable) {
        t.printStackTrace()
        false
    }
}
