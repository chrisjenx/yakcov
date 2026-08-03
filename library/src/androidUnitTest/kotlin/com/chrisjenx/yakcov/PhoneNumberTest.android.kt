package com.chrisjenx.yakcov

/**
 * Dogfoods the public test seam consumers use (#41). Before it existed this file carried a private
 * copy of libphonenumber's `ClassPathResourceMetadataLoader` plus two hand-copied metadata protos
 * under `androidUnitTest/resources`, so Android unit tests could only ever validate US and GB.
 */
actual fun initPhoneNumberUtil() = initPhoneNumberUtilForTest()
