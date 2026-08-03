package com.chrisjenx.yakcov

import io.michaelrocks.libphonenumber.kotlin.MetadataLoader
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.createInstance
import io.michaelrocks.libphonenumber.kotlin.io.InputStream
import io.michaelrocks.libphonenumber.kotlin.io.JavaInputStream
import java.io.File
import java.net.URL
import java.util.Properties

/**
 * Configures yakcov's phone-number validation for an Android **local unit test** (plain JVM or
 * Robolectric), so [isPhoneNumber] and the `Phone` rule return real results instead of `false`.
 *
 * Without this, every number reads as invalid in unit tests: on Android the [PhoneNumberUtil] is
 * built from an application [android.content.Context] captured by the androidx.startup
 * `PhoneNumberUtilInitializer`, and neither that provider nor compose-resources'
 * `AndroidContextProvider` is created by a local unit test (robolectric/robolectric#9603,
 * [CMP-6612](https://youtrack.jetbrains.com/issue/CMP-6612)). Because `isPhoneNumber` deliberately
 * degrades to `false` rather than crashing, the failure is silent: a reject-only assertion such as
 * `assertFalse("abc".isPhoneNumber())` passes vacuously while the positive path is never exercised.
 *
 * This call needs **no** `Context`, no androidx.startup, and no compose-resources `ContentProvider`
 * — it loads libphonenumber's real metadata directly (see [UnitTestMetadataLoader]), so all regions
 * validate, not just a hand-picked few. It is safe to call from every `@Before`: the underlying
 * [PhoneNumberUtil] is built once per JVM and reused, since building one is expensive.
 *
 * ```kotlin
 * @Before fun setUp() = initPhoneNumberUtilForTest()
 * @After fun tearDown() = resetPhoneNumberUtilForTest()
 * ```
 *
 * Requires the test's module to keep Android resources available to unit tests, which is also what
 * Robolectric needs:
 *
 * ```kotlin
 * android { testOptions { unitTests { isIncludeAndroidResources = true } } }
 * ```
 *
 * libphonenumber-kotlin is `compileOnly` in yakcov, so the module under test must declare it too
 * (see the [phone recipe](https://chrisjenx.github.io/yakcov/recipes/phone/)).
 *
 * @throws IllegalStateException if libphonenumber's metadata cannot be found, with the two
 *  supported remedies. Pass your own util to [initPhoneNumberUtilForTest] to bypass discovery.
 */
fun initPhoneNumberUtilForTest() {
    PhoneNumberUtilHolder.inject(sharedTestUtil)
}

/**
 * Installs a specific [util] as the one yakcov's phone rules use, for full control over metadata
 * loading. Prefer the no-argument [initPhoneNumberUtilForTest] unless discovery doesn't fit your
 * setup.
 *
 * The installed util takes precedence over the `Context`-derived one for the rest of the JVM's
 * life, or until [resetPhoneNumberUtilForTest].
 */
fun initPhoneNumberUtilForTest(util: PhoneNumberUtil) {
    PhoneNumberUtilHolder.inject(util)
}

/**
 * Removes any util installed by [initPhoneNumberUtilForTest], restoring the unconfigured state.
 *
 * Call it from `@After` so a test that configures phone validation can't decide the outcome of a
 * later test class that forgot to — otherwise a suite passes or fails purely on shard/execution
 * order. It does not clear the application `Context` captured by the androidx.startup
 * `PhoneNumberUtilInitializer` (always absent in a local unit test, and load-bearing in a real app
 * or instrumented test).
 */
fun resetPhoneNumberUtilForTest() {
    PhoneNumberUtilHolder.reset()
}

/**
 * Built once per JVM and shared: libphonenumber's own docs call [PhoneNumberUtil.createInstance]
 * "very expensive", and tests call [initPhoneNumberUtilForTest] from every `@Before`.
 */
private val sharedTestUtil: PhoneNumberUtil by lazy {
    UnitTestMetadataLoader.verifyMetadataDiscoverable()
    PhoneNumberUtil.createInstance(UnitTestMetadataLoader())
}

/**
 * Loads libphonenumber's metadata in a local unit test without an Android [android.content.Context].
 *
 * libphonenumber-kotlin ships its 250-odd `PhoneNumberMetadataProto_<REGION>` files as
 * compose-resources. The `-android` artifact — the variant an Android module resolves, including for
 * its unit tests — packages them as **assets**, and reaches them through `Res.getUri(...)`, which
 * needs compose-resources' `AndroidContextProvider`. A local unit test creates no `ContentProvider`,
 * so that path throws `IllegalStateException: Failed to read file PhoneNumberMetadataProto_US`, and
 * the artifact has no `ClassPathResourceMetadataLoader` (that one is `jvmMain`-only) to fall back to.
 *
 * So look for the files directly, in the two places a unit test can actually see them:
 *
 * 1. **The test classpath** — a hit when the module puts the `-jvm` variant on the unit-test
 *    classpath, which carries the metadata as ordinary classpath resources.
 * 2. **AGP's merged unit-test assets** — the normal case for an Android module. AGP writes
 *    `com/android/tools/test_config.properties` onto the unit-test classpath (the same file
 *    Robolectric reads) whose `android_merged_assets` points at the merged assets directory,
 *    dependency assets included.
 *
 * For (2) the compose-resources directory is found by *scanning* for the requested file rather than
 * by naming libphonenumber's generated resources package, so an upstream rename of its Maven
 * coordinates can't silently break this. Sibling directories (yakcov's own resources, the app's) are
 * skipped for free — they hold no `PhoneNumberMetadataProto_*`.
 */
internal class UnitTestMetadataLoader : MetadataLoader {

    override fun loadMetadata(phoneMetadataResource: String): InputStream? {
        classLoader.getResourceAsStream(CLASSPATH_PREFIX + phoneMetadataResource)
            ?.let { return JavaInputStream(it) }

        for (dir in mergedAssetResourceDirs) {
            val file = File(dir, "files/$phoneMetadataResource")
            if (file.isFile) return JavaInputStream(file.inputStream())
        }

        // `null` is libphonenumber's "this resource isn't available", and plenty of the files it asks
        // for are genuinely optional — most country codes ship no PhoneNumberAlternateFormatsProto_*,
        // for instance — so throwing here would break formatting for all of them. A wholesale
        // discovery failure is caught up front instead, by [verifyMetadataDiscoverable].
        return null
    }

    internal companion object {
        /**
         * Where the `-jvm` variant keeps the metadata on the classpath. Only a fallback, and only
         * reached when that variant is present, so a rename upstream degrades to route (2).
         */
        const val CLASSPATH_PREFIX =
            "composeResources/io.github.luca992.libphonenumber_kotlin.libphonenumber.generated.resources/files/"

        val classLoader: ClassLoader
            get() = UnitTestMetadataLoader::class.java.classLoader
                ?: error("yakcov: no class loader available to load libphonenumber metadata")

        /** Resolved once — libphonenumber asks for metadata region by region, lazily. */
        val mergedAssetResourceDirs: List<File> by lazy { findMergedAssetResourceDirs() }

        fun findMergedAssetResourceDirs(): List<File> {
            val config = classLoader.getResource(AGP_TEST_CONFIG) ?: return emptyList()
            val properties = Properties().also { props -> config.openStream().use(props::load) }
            val merged = properties.getProperty("android_merged_assets") ?: return emptyList()

            // Absolute in some AGP versions; otherwise relative to the module directory.
            val assetRoots = File(merged).takeIf(File::isAbsolute)?.let(::listOf)
                ?: moduleDirCandidates(config).map { dir -> File(dir, merged) }

            return assetRoots.map { root -> File(root, "composeResources") }
                .filter(File::isDirectory)
                .flatMap { it.listFiles().orEmpty().filter(File::isDirectory) }
        }

        /**
         * Where a module-relative `android_merged_assets` might be anchored.
         *
         * Normally the test JVM's working directory *is* the module directory, but a build that sets
         * `Test.workingDir` — common in multi-module repos, to make fixture paths uniform — breaks
         * that, and the miss would surface as [verifyMetadataDiscoverable]'s error pointing at a
         * remedy the user has already applied. So also derive it from where AGP put
         * `test_config.properties`: that always lands under `<module>/build/…`, so the `build`
         * ancestor's parent is the module directory, whatever the working directory happens to be.
         */
        private fun moduleDirCandidates(config: URL): List<File> {
            val fromWorkingDir = System.getProperty("user.dir")?.let(::File)
            val fromConfigLocation = config.takeIf { it.protocol == "file" }
                ?.let { url -> runCatching { File(url.toURI()) }.getOrNull() }
                ?.let { file ->
                    generateSequence(file.parentFile) { it.parentFile }
                        .firstOrNull { it.name == "build" }
                        ?.parentFile
                }
            return listOfNotNull(fromWorkingDir, fromConfigLocation).distinct()
        }

        const val AGP_TEST_CONFIG = "com/android/tools/test_config.properties"

        /**
         * Fails fast, once, when neither route can see libphonenumber's metadata at all.
         *
         * [loadMetadata] must answer `null` for a genuinely optional file, so it cannot tell a
         * misconfigured module from a region that simply has no alternate formats. Left to itself
         * libphonenumber would turn the former into a per-region `MissingMetadataException`, which
         * `isPhoneNumber` swallows — reproducing the silent "every number is invalid" this seam
         * exists to end. So probe one file that libphonenumber always ships and complain loudly.
         */
        fun verifyMetadataDiscoverable() {
            if (exists(CANARY)) return
            error(
                "yakcov: could not find libphonenumber's metadata ($CANARY) from this unit test, so " +
                    "phone validation would report every number invalid. Either keep Android " +
                    "resources available to unit tests — android { testOptions { unitTests { " +
                    "isIncludeAndroidResources = true } } } — and make sure the module depends on " +
                    "io.github.luca992.libphonenumber-kotlin:libphonenumber, or build the util " +
                    "yourself and pass it to initPhoneNumberUtilForTest(util)."
            )
        }

        /** Existence check that avoids opening a stream the caller would have to close. */
        private fun exists(phoneMetadataResource: String): Boolean =
            classLoader.getResource(CLASSPATH_PREFIX + phoneMetadataResource) != null ||
                mergedAssetResourceDirs.any { File(it, "files/$phoneMetadataResource").isFile }

        /** US metadata is always present in libphonenumber's bundle; only used as a probe. */
        private const val CANARY = "PhoneNumberMetadataProto_US"
    }
}
