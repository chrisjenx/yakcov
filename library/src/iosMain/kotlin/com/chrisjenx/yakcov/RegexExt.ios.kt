package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.metadata.init.ComposeResourceMetadataLoader


internal actual val phoneUtil: PhoneNumberUtil by lazy {
    PhoneNumberUtil.createInstance(ComposeResourceMetadataLoader())
}
