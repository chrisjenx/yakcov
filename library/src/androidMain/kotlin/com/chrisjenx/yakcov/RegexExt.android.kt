package com.chrisjenx.yakcov

import android.content.Context
import androidx.startup.Initializer
import io.michaelrocks.libphonenumber.kotlin.PhoneNumberUtil
import io.michaelrocks.libphonenumber.kotlin.createInstance


internal actual val phoneUtil: PhoneNumberUtil get() = PhoneNumberUtilHolder.get()

/**
 * Lazily provides the [PhoneNumberUtil] for phone-number rules.
 *
 * The util is built on **first use**, not at app startup, so an app that never uses a phone-number
 * rule never loads libphonenumber — which yakcov declares `compileOnly`. That matters: this holder
 * references libphonenumber types only inside [create], which runs lazily; touching the holder (or
 * the startup [PhoneNumberUtilInitializer]) does not load those classes. So apps that don't add the
 * optional dependency no longer crash at startup with `NoClassDefFoundError`. If such an app actually
 * validates a phone number, the error [create] raises is caught by the phone rule's `isPhoneNumber`
 * check (a `catch (Throwable)`): the actionable message is logged via `printStackTrace` and validation
 * returns `false`, so the field reads as invalid rather than throwing or crashing the app.
 */
internal object PhoneNumberUtilHolder {
    /** Application context captured at startup by [PhoneNumberUtilInitializer]. */
    @Volatile
    internal var appContext: Context? = null

    /**
     * Test seam: a pre-built util that wins over [lazyUtil], so unit tests need no `Context`.
     * Published to consumers as [initPhoneNumberUtilForTest] / [resetPhoneNumberUtilForTest] (#41)
     * rather than by exposing this object, which would also expose [appContext].
     */
    @Volatile
    private var injected: PhoneNumberUtil? = null

    private val lazyUtil: PhoneNumberUtil by lazy { create() }

    internal fun inject(util: PhoneNumberUtil) {
        injected = util
    }

    /**
     * Drops any [inject]ed util, restoring the unconfigured state so test order can't decide
     * outcomes. [appContext] is deliberately left alone — see [resetPhoneNumberUtilForTest].
     */
    internal fun reset() {
        injected = null
    }

    fun get(): PhoneNumberUtil = injected ?: lazyUtil

    private fun create(): PhoneNumberUtil {
        val context = appContext ?: error(
            "yakcov: phone-number validation was used before startup captured a Context. Ensure the " +
                "androidx.startup PhoneNumberUtilInitializer is present (it is in the library manifest)."
        )
        return try {
            PhoneNumberUtil.createInstance(context)
        } catch (error: LinkageError) {
            // libphonenumber-kotlin is compileOnly; it isn't on the runtime classpath.
            throw IllegalStateException(
                "yakcov: phone-number validation requires the libphonenumber-kotlin dependency, which " +
                    "yakcov declares as compileOnly. Add " +
                    "io.github.luca992.libphonenumber-kotlin:libphonenumber to your app/module to use " +
                    "phone-number rules.",
                error,
            )
        }
    }
}

/**
 * androidx.startup initializer that captures the application [Context] (see the library manifest).
 *
 * It deliberately holds **no** libphonenumber reference, so it runs safely even when that
 * `compileOnly` dependency is absent. The [PhoneNumberUtil] itself is created lazily on first use by
 * [PhoneNumberUtilHolder] — keeping phone-number support zero-cost (and crash-free) for apps that
 * never use it.
 */
class PhoneNumberUtilInitializer : Initializer<Context> {
    override fun create(context: Context): Context {
        val app = context.applicationContext
        PhoneNumberUtilHolder.appContext = app
        return app
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
