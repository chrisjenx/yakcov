package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.metadata.init.ClassPathResourceMetadataLoader


private val phoneUtil = PhoneNumberUtil.createInstance(ClassPathResourceMetadataLoader())

/**
 * Check if is phone number to best ability of each platform.
 */
actual fun String?.isPhoneNumber(defaultRegion: String?): Boolean {
    this ?: return false
    return try {
        val result = phoneUtil.parse(this, defaultRegion?.uppercase())
        phoneUtil.isValidNumber(result)
    } catch (t: Throwable) {
        t.printStackTrace()
        false
    }
}
