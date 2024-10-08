package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.metadata.init.ClassPathResourceMetadataLoader

actual fun initPhoneNumberUtil() {
    phoneUtil = PhoneNumberUtil.createInstance(ClassPathResourceMetadataLoader())
}
