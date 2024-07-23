package com.chrisjenx.yakcov

/**
 * import parsePhoneNumber from 'libphonenumber-js'
 *
 * const phoneNumber = parsePhoneNumber(' 8 (800) 555-35-35 ', 'RU')
 * if (phoneNumber) {
 *   phoneNumber.country === 'RU'
 *   phoneNumber.number === '+78005553535'
 *   phoneNumber.isPossible() === true
 *   phoneNumber.isValid() === true
 *   // Note: `.getType()` requires `/max` metadata: see below for an explanation.
 *   phoneNumber.getType() === 'TOLL_FREE'
 * }
 */

/**
 * Check if is phone number to best ability of each platform.
 */
actual fun String?.isPhoneNumber(): Boolean {
    this ?: return false
    return try {
        val result = parsePhoneNumber(this, "US")
        result?.isValid() ?: false
    } catch (e: Throwable) {
        false
    }
}

@JsModule("libphonenumber-js")
@JsNonModule
external fun parsePhoneNumber(phone: String, defaultCountry: String?): PhoneNumber?

@JsModule("libphonenumber-js")
@JsNonModule
external class PhoneNumber {
    val country: String
    val number: String
    fun isPossible(): Boolean
    fun isValid(): Boolean
    fun getType(): String
}