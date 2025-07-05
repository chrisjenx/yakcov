package com.chrisjenx.yakcov

import android.content.Context
import androidx.startup.Initializer
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.createInstance


internal actual val phoneUtil: PhoneNumberUtil get() = PhoneNumberUtilInitializer.phoneNumberUtil


/**
 * Will init the Android phone number util at app startup using androidx.startup, see manifest.
 */
class PhoneNumberUtilInitializer : Initializer<PhoneNumberUtil> {
    override fun create(context: Context): PhoneNumberUtil {
        val instance = PhoneNumberUtil.createInstance(context)
        phoneNumberUtil = instance // Set global
        return instance
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }

    companion object {
        internal lateinit var phoneNumberUtil: PhoneNumberUtil
    }
}
