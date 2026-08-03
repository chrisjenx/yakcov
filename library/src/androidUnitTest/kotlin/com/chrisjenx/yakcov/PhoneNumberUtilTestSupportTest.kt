package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.createInstance
import java.io.File
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
     * AGP writes `android_merged_assets` relative to the module directory, which is only *usually*
     * the test JVM's working directory — a build that sets `Test.workingDir` would otherwise get the
     * "metadata not found" error while `isIncludeAndroidResources` was set all along.
     */
    @Test
    fun discoveryIsIndependentOfWorkingDirectory() {
        val original = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", File(original, "not-the-module-dir").path)
            val dirs = UnitTestMetadataLoader.findMergedAssetResourceDirs()
            assertTrue(
                dirs.any { File(it, "files/PhoneNumberMetadataProto_US").isFile },
                "discovery must survive a build that overrides the test working directory",
            )
        } finally {
            System.setProperty("user.dir", original)
        }
    }

    /**
     * The ordering hazard called out in #41: without a reset, one configured test class decides
     * whether a later class that forgot to configure passes, so a suite's result depends on
     * execution order within the shard.
     */
    @Test
    fun resetDropsTheInstalledUtil() {
        val installed = PhoneNumberUtil.createInstance(UnitTestMetadataLoader())
        initPhoneNumberUtilForTest(installed)
        assertTrue(installed === phoneUtil, "sanity: installed before reset")

        resetPhoneNumberUtilForTest()
        // Assert on identity, not on what the fallback does. After a reset `phoneUtil` falls through
        // to the Context-derived util, which throws here (a local unit test captures no Context) but
        // would succeed the day this module gains Robolectric — asserting "degrades to false" would
        // then flip to a failure looking like the seam broke, and it also drives isPhoneNumber's
        // catch/printStackTrace, spilling a fake-looking trace into every CI run.
        val afterReset = runCatching { phoneUtil }.getOrNull()
        assertTrue(afterReset !== installed, "reset must drop the installed util")

        initPhoneNumberUtilForTest()
        assertTrue("+16508991234".isPhoneNumber("US"), "re-init after reset should work")
    }
}
