package com.chrisjenx.yakcov

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDataDetector
import platform.Foundation.NSMakeRange
import platform.Foundation.NSTextCheckingTypePhoneNumber
import platform.Foundation.matchesInString

/**
 * Check if is phone number to best ability of each platform.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun String?.isPhoneNumber(defaultRegion: String?): Boolean {
    if (this.isNullOrBlank()) return false
    val detector = NSDataDetector(types = NSTextCheckingTypePhoneNumber, error = null)
    val range = NSMakeRange(0.toULong(), this.length.toULong())
    val matches = detector.matchesInString(this, options = 0.toULong(), range = range)
    return matches.isNotEmpty()
}
