package com.chrisjenx.yakcov

import cocoapods.libPhoneNumber_iOS.NBPhoneNumberUtil
import kotlinx.cinterop.ExperimentalForeignApi


/**
 * Check if is phone number to best ability of each platform.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun String?.isPhoneNumber(defaultRegion: String?): Boolean {
    if (this.isNullOrBlank()) return false
    try {
        val phoneUtil = NBPhoneNumberUtil.sharedInstance() ?: return false
        val phoneNumber = phoneUtil.parse(this, defaultRegion, null)
        return phoneUtil.isValidNumberForRegion(phoneNumber, defaultRegion)
    } catch (e: Throwable) {
        return false
    }
}
