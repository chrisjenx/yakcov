package com.chrisjenx.yakcov

import android.content.Context
import androidx.startup.Initializer
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.metadata.source.AssetsMetadataLoader
import org.jetbrains.annotations.VisibleForTesting


@VisibleForTesting
internal lateinit var phoneUtil: PhoneNumberUtil


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

/**
 * Will init the Android phone number util at app startup using androidx.startup, see manifest.
 */
class PhoneNumberUtilInitializer : Initializer<PhoneNumberUtil> {
    override fun create(context: Context): PhoneNumberUtil {
        val assetManager = AssetsMetadataLoader(context.applicationContext.assets)
        val instance = PhoneNumberUtil.createInstance(assetManager)
        phoneUtil = instance // Set global
        return instance
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}

