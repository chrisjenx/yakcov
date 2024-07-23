package com.chrisjenx.yakcov


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
external fun parsePhoneNumber(phone: String, defaultCountry: String?): PhoneNumber?

@JsModule("libphonenumber-js")
external class PhoneNumber {
    val country: String
    val number: String
    fun isPossible(): Boolean
    fun isValid(): Boolean
    fun getType(): String
}
