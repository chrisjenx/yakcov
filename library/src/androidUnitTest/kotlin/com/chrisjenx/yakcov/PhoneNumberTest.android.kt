package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.MetadataLoader
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.io.InputStream
import io.michaelrocks.libphonenumber.kotlin.io.JavaInputStream
import java.util.logging.Level
import java.util.logging.Logger


actual fun initPhoneNumberUtil() {
    PhoneNumberUtilInitializer.phoneNumberUtil =
        PhoneNumberUtil.createInstance(ClassPathResourceMetadataLoader())
}

class ClassPathResourceMetadataLoader : MetadataLoader {
    override fun loadMetadata(phoneMetadataResource: String): InputStream? {
        val inputStream: java.io.InputStream? =
            ClassPathResourceMetadataLoader::class.java.getResourceAsStream(phoneMetadataResource)
        if (inputStream == null) {
            logger.log(Level.WARNING, String.format("File %s not found", phoneMetadataResource))
            return null
        }
        return JavaInputStream(inputStream)
    }

    companion object {
        private val logger: Logger =
            Logger.getLogger(ClassPathResourceMetadataLoader::class.java.getName())
    }
}

