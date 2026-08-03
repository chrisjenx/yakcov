package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.createInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the public unit-test seam from #41.
 *
 * The regressions these guard are silent by construction: `isPhoneNumber` degrades to `false`
 * instead of throwing, so an unconfigured util makes *every* number invalid and only
 * positive-path assertions can tell the difference.
 */
class PhoneNumberUtilTestSupportTest {

    @BeforeTest
    fun setUp() = initPhoneNumberUtilForTest()

    @AfterTest
    fun tearDown() = resetPhoneNumberUtilForTest()

    /**
     * The point of discovering real metadata: before #41 the Android unit tests carried two
     * hand-copied protos, so any region beyond US/GB was unverifiable.
     */
    @Test
    fun validatesRegionsBeyondTheOldHandCopiedMetadata() {
        assertTrue("+33 1 42 68 53 00".isPhoneNumber("FR"), "FR landline should be valid")
        assertTrue("+34 913 601 000".isPhoneNumber("ES"), "ES landline should be valid")
        assertTrue("+49 30 2000 3000".isPhoneNumber("DE"), "DE landline should be valid")
        assertTrue("+61 2 9374 4000".isPhoneNumber("AU"), "AU landline should be valid")
    }

    @Test
    fun stillRejectsInvalidNumbers() {
        assertFalse("abc".isPhoneNumber("US"), "letters are not a phone number")
        assertFalse("43435".isPhoneNumber("US"), "too short to be a US number")
        // Region-aware: well-formed as digits, but not a valid national number for FR.
        assertFalse("1234567890".isPhoneNumber("FR"), "not a valid FR national number")
    }

    @Test
    fun repeatedInitIsCheapAndKeepsWorking() {
        val first = phoneUtil
        initPhoneNumberUtilForTest()
        // Same instance: building a PhoneNumberUtil is expensive and @Before runs per test.
        assertTrue(first === phoneUtil, "expected the shared util to be reused")
        assertTrue("+16508991234".isPhoneNumber("US"))
    }

    @Test
    fun explicitUtilOverloadWins() {
        val custom = PhoneNumberUtil.createInstance(UnitTestMetadataLoader())
        initPhoneNumberUtilForTest(custom)
        assertTrue(custom === phoneUtil, "the explicitly installed util should be used")
        assertTrue("+16508991234".isPhoneNumber("US"))
    }

    /**
     * `null` is libphonenumber's "not available", and it asks for optional files: only a subset of
     * country codes ship a `PhoneNumberAlternateFormatsProto_*`. Throwing for a miss would break
     * formatting for every country without one, so a miss must stay a `null`.
     */
    @Test
    fun missingOptionalResourceIsNullNotAnError() {
        val loader = UnitTestMetadataLoader()
        assertNull(
            loader.loadMetadata("PhoneNumberAlternateFormatsProto_999"),
            "an absent optional resource must be reported as null",
        )
        assertNull(
            loader.loadMetadata("PhoneNumberMetadataProto_ZZTOP"),
            "an unknown region must be reported as null",
        )
        // ...while a real one still resolves, so the null above isn't just a broken loader.
        assertNotNull(loader.loadMetadata("PhoneNumberMetadataProto_US"))
    }

    /** The wholesale-misconfiguration guard that replaced per-resource throwing. */
    @Test
    fun discoveryVerificationPassesInThisModule() {
        UnitTestMetadataLoader.verifyMetadataDiscoverable()
    }

    /**
     * The ordering hazard called out in #41: without a reset, one configured test class decides
     * whether a later class that forgot to configure passes. Unconfigured means no `Context` was
     * captured by androidx.startup, so validation degrades to `false` (and logs) rather than
     * silently reusing a previous test's util.
     */
    @Test
    fun resetRestoresTheUnconfiguredState() {
        assertTrue("+16508991234".isPhoneNumber("US"), "sanity: configured before reset")

        resetPhoneNumberUtilForTest()
        assertFalse(
            "+16508991234".isPhoneNumber("US"),
            "after reset there is no util and no Context, so validation must degrade to false",
        )

        initPhoneNumberUtilForTest()
        assertTrue("+16508991234".isPhoneNumber("US"), "re-init after reset should work")
    }
}
