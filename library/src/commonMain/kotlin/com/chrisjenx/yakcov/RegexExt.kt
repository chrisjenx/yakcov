package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

private val emailRegex =
    """(?:[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#${'$'}%&'*+/=?^_`{|}~-]+)*|"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])*")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)\])"""
        .toRegex(RegexOption.IGNORE_CASE)

@OptIn(ExperimentalContracts::class)
fun String?.isEmail(): Boolean {
    contract { returns(true) implies (this@isEmail != null) }
    this ?: return false
    return matches(emailRegex)
}

internal expect val phoneUtil: PhoneNumberUtil


/**
 * Check if is phone number to best ability of each platform.
 *
 * @param defaultRegion The default region to use if the number is not in international format.
 *  it's two digits country code. e.g. "US", "GB", "ES"
 */
fun String?.isPhoneNumber(defaultRegion: String? = "US"): Boolean {
    this ?: return false
    return try {
        val result = phoneUtil.parse(this, defaultRegion?.uppercase())
        phoneUtil.isValidNumber(result)
    } catch (t: Throwable) {
        t.printStackTrace()
        false
    }
}
